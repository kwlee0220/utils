package utils.websocket;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

import utils.Preconditions;
import utils.Throwables;
import utils.websocket.WebSocketObservable.Message;


/**
 * RxJava3 {@link Observable}로 감싼 WebSocket 클라이언트.
 * <p>
 * 각 {@code subscribe}마다 새로운 WebSocket 연결을 생성하여 수신한 메시지를
 * {@link TextMessage} 또는 {@link BinaryMessage}로 downstream에 emit한다.
 * 구독 dispose 시 WebSocket은 정상 close 된다.
 * <p>
 * <b>Fragment 처리</b>: {@link Builder#collectTextFragments(boolean)}와
 * {@link Builder#collectBinaryFragments(boolean)}로 동작 모드 선택:
 * <ul>
 *   <li>{@code true} (기본값) — 모든 fragment를 누적해 마지막 fragment 도착 시 합쳐진 단일 메시지를 emit
 *       (이때 {@code isLast()}는 항상 {@code true}). 누적 크기가 {@link Builder#maxTextMessageLength(int)} /
 *       {@link Builder#maxBinaryMessageLength(int)}를 넘으면 버퍼를 비우고 {@code onError}로 끝내며 연결을 닫는다.</li>
 *   <li>{@code false} — 각 fragment를 즉시 fragment 단위로 emit ({@code isLast()}는 원본 플래그 그대로).
 *       누적하지 않으므로 크기 상한이 적용되지 않는다.</li>
 * </ul>
 * <p>
 * <b>종료</b>: 상대가 정상 종료 코드(1000, 1001, 1005)로 닫으면 {@code onComplete}, 그 밖의 코드로 닫으면
 * {@link WebSocketClosedException}으로 {@code onError}를 호출한다. 연결을 닫을 때는 close 프레임을 보내고 상대의
 * 응답을 기다리되, {@link Builder#closeTimeout(Duration)} 안에 끝나지 않으면 연결을 끊는다(abort).
 * <p>
 * <b>Backpressure</b>: 매 메시지 처리 후 {@code webSocket.request(1)}을 호출해 1개 단위로 수신.
 * <p>
 * <b>사용 예</b>:
 * <pre>{@code
 * WebSocketObservable.builder()
 *         .uri("ws://example.com/ws")
 *         .collectTextFragments(true)
 *         .build()
 *         .subscribe(msg -> System.out.println(msg));
 * }</pre>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class WebSocketObservable extends Observable<Message> {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketObservable.class);

	/** 조각을 모을 때의 기본 최대 크기(텍스트는 문자 수, 바이너리는 바이트 수, 16M). */
	public static final int DEFAULT_MAX_MESSAGE_LENGTH = 16 * 1024 * 1024;

	private final URI m_uri;
	private final @Nullable HttpClient m_client;
	private final @Nullable Duration m_connectTimeout;
	private final Duration m_closeTimeout;
	private final boolean m_collectTextFragments;
	private final boolean m_collectBinaryFragments;
	private final int m_maxTextMessageLength;
	private final int m_maxBinaryMessageLength;
	private final @Nullable Consumer<WebSocket.Builder> m_builderCustomizer;

	private WebSocketObservable(Builder builder) {
		Preconditions.checkNotNullArgument(builder, "builder is null");

		m_uri = builder.m_uri;
		m_client = builder.m_client;
		m_connectTimeout = builder.m_connectTimeout;
		m_closeTimeout = builder.m_closeTimeout;
		m_collectTextFragments = builder.m_collectTextFragments;
		m_collectBinaryFragments = builder.m_collectBinaryFragments;
		m_maxTextMessageLength = builder.m_maxTextMessageLength;
		m_maxBinaryMessageLength = builder.m_maxBinaryMessageLength;
		m_builderCustomizer = builder.m_builderCustomizer;
	}

	@Override
	protected void subscribeActual(Observer<? super Message> downstream) {
		// 구독마다 새로운 WebSocket 연결
		WebSocketDisposable disposable = new WebSocketDisposable(downstream, m_closeTimeout);
		downstream.onSubscribe(disposable);

		CompletableFuture<WebSocket> future;
		try {
			HttpClient client = (m_client != null) ? m_client : SharedResources.httpClient();
			WebSocket.Builder builder = client.newWebSocketBuilder();
			if ( m_connectTimeout != null ) {
				builder.connectTimeout(m_connectTimeout);
			}
			if ( m_builderCustomizer != null ) {
				m_builderCustomizer.accept(builder);
			}
			future = builder.buildAsync(m_uri, new RxWebSocketListener(disposable));
		}
		catch ( Throwable e ) {
			// onSubscribe 뒤에 던지면 구독자에게 전달되지 않고 RxJava 전역 오류 처리기로 빠져 구독이 끝나지 않는다.
			// (2026-10-31 이전 동작. 예: 빌더 설정에서 header(null, ...))
			disposable.onError(e);
			return;
		}
		future.whenComplete((ws, error) -> {
			if ( error != null ) {
				// 연결 자체가 실패한 경우
				disposable.onError(Throwables.unwrapThrowable(error));
			}
			else {
				disposable.setWebSocket(ws);
			}
		});
	}

	/**
	 * 새 {@link Builder} 인스턴스를 반환한다.
	 *
	 * @return	기본 설정의 빌더 ({@link Builder#collectTextFragments collectTextFragments}=true,
	 * 			{@link Builder#collectBinaryFragments collectBinaryFragments}=true,
	 * 			{@code httpClient}=패키지가 함께 쓰는 클라이언트, {@code connectTimeout}=30초,
	 * 			{@code closeTimeout}=5초, 최대 메시지 크기=16M)
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link WebSocketObservable} 인스턴스를 구성하는 빌더.
	 * <p>
	 * 호출 체인 종료 시 {@link #build()}를 호출하면 인스턴스가 생성된다.
	 * {@link #uri(String)}는 반드시 호출되어야 한다.
	 */
	public static final class Builder {
		private URI m_uri;
		private @Nullable HttpClient m_client;
		private @Nullable Duration m_connectTimeout = WebSocketStateChart.DEFAULT_CONNECT_TIMEOUT;
		private Duration m_closeTimeout = WebSocketStateChart.DEFAULT_CLOSE_TIMEOUT;
		private boolean m_collectTextFragments = true;
		private boolean m_collectBinaryFragments = true;
		private int m_maxTextMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
		private int m_maxBinaryMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
		private @Nullable Consumer<WebSocket.Builder> m_builderCustomizer;

		/**
		 * WebSocket 연결 URL을 설정한다.
		 *
		 * @param url	WebSocket 서버 URL (예: {@code "ws://localhost:8080/ws"})
		 * @return		this
		 * @throws IllegalArgumentException URL 형식이 잘못된 경우 ({@link URI#create(String)}에서 던짐)
		 */
		public Builder uri(String url) {
			m_uri = URI.create(url);
			return this;
		}

		/**
		 * 사용할 {@link HttpClient}을 설정한다. 지정하지 않으면 패키지가 함께 쓰는 클라이언트 하나를 쓴다
		 * ({@link WebSocketStateChart}의 기본 클라이언트와 같다). 지정한 클라이언트의 수명은 호출자가 관리한다.
		 * (2026-10-31 이전에는 빌더마다 새 클라이언트를 만들어, 지정해도 기본 클라이언트가 먼저 만들어졌다.)
		 *
		 * @param client	HTTP 클라이언트 (non-null)
		 * @return			this
		 * @throws IllegalArgumentException	{@code client}가 {@code null}인 경우
		 */
		public Builder httpClient(HttpClient client) {
			Preconditions.checkNotNullArgument(client, "client is null");
			m_client = client;
			return this;
		}

		/**
		 * 접속 시간 제한을 설정한다. 기본값은 {@link WebSocketStateChart#DEFAULT_CONNECT_TIMEOUT}(30초)이다.
		 * 시간 안에 접속하지 못하면 {@code onError}({@link java.net.http.HttpConnectTimeoutException})로 끝난다.
		 *
		 * @param timeout	접속 시간 제한(양수). {@code null}이면 제한을 두지 않는다.
		 * @return			this
		 * @throws IllegalArgumentException	{@code timeout}이 양수가 아닌 경우
		 */
		public Builder connectTimeout(@Nullable Duration timeout) {
			Preconditions.checkArgument(timeout == null || DurationUtils.isPositive(timeout),
										"timeout must be positive: %s", timeout);
			m_connectTimeout = timeout;
			return this;
		}

		/**
		 * 연결 종료 절차(close 프레임 전송과 상대의 응답 대기)의 시간 제한을 설정한다. 넘으면 연결을 끊는다(abort).
		 * 기본값은 {@link WebSocketStateChart#DEFAULT_CLOSE_TIMEOUT}(5초)이다.
		 *
		 * @param timeout	시간 제한 (1ms 이상)
		 * @return			this
		 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 1ms 미만이거나 너무 큰 경우
		 */
		public Builder closeTimeout(Duration timeout) {
			WebSocketStateChart.checkMillisDuration(timeout, "timeout");
			m_closeTimeout = timeout;
			return this;
		}

		/**
		 * 텍스트 메시지 fragment 수집 모드를 설정한다.
		 *
		 * @param collect	{@code true}면 모든 fragment를 누적해 합쳐진 메시지를 emit (기본값);
		 *					{@code false}면 fragment 단위로 즉시 emit.
		 * @return			this
		 */
		public Builder collectTextFragments(boolean collect) {
			m_collectTextFragments = collect;
			return this;
		}

		/**
		 * 바이너리 메시지 fragment 수집 모드를 설정한다.
		 *
		 * @param collect	{@code true}면 모든 fragment를 누적해 합쳐진 메시지를 emit (기본값);
		 *					{@code false}면 fragment 단위로 즉시 emit.
		 * @return			this
		 */
		public Builder collectBinaryFragments(boolean collect) {
			m_collectBinaryFragments = collect;
			return this;
		}

		/**
		 * 텍스트 fragment를 모을 때의 최대 길이(문자 수)를 설정한다. 기본값은 {@link #DEFAULT_MAX_MESSAGE_LENGTH}다.
		 * 넘으면 모은 내용을 버리고 {@code onError}({@link IllegalStateException})로 끝내며 연결을 닫는다.
		 * {@link #collectTextFragments(boolean) collectTextFragments}가 {@code false}이면 적용되지 않는다.
		 *
		 * @param maxLength	최대 길이 (양수)
		 * @return			this
		 * @throws IllegalArgumentException	{@code maxLength}가 양수가 아닌 경우
		 */
		public Builder maxTextMessageLength(int maxLength) {
			Preconditions.checkArgument(maxLength > 0, "maxLength must be positive: %s", maxLength);
			m_maxTextMessageLength = maxLength;
			return this;
		}

		/**
		 * 바이너리 fragment를 모을 때의 최대 크기(바이트 수)를 설정한다. 기본값은 {@link #DEFAULT_MAX_MESSAGE_LENGTH}다.
		 * 넘으면 모은 내용을 버리고 {@code onError}({@link IllegalStateException})로 끝내며 연결을 닫는다.
		 * {@link #collectBinaryFragments(boolean) collectBinaryFragments}가 {@code false}이면 적용되지 않는다.
		 *
		 * @param maxLength	최대 크기 (양수)
		 * @return			this
		 * @throws IllegalArgumentException	{@code maxLength}가 양수가 아닌 경우
		 */
		public Builder maxBinaryMessageLength(int maxLength) {
			Preconditions.checkArgument(maxLength > 0, "maxLength must be positive: %s", maxLength);
			m_maxBinaryMessageLength = maxLength;
			return this;
		}

		/**
		 * 접속 직전에 {@link WebSocket.Builder}를 추가로 설정할 것을 지정한다. 접속 헤더({@code header(...)}),
		 * 서브프로토콜({@code subprotocols(...)}) 등에 쓴다. 구독할 때마다 {@code buildAsync()} 직전에 호출한다.
		 * 여기서 {@code buildAsync()}를 호출하면 안 된다.
		 *
		 * @param customizer	빌더 설정 (non-null)
		 * @return			this
		 * @throws IllegalArgumentException	{@code customizer}가 {@code null}인 경우
		 */
		public Builder webSocketBuilderCustomizer(Consumer<WebSocket.Builder> customizer) {
			Preconditions.checkNotNullArgument(customizer, "customizer is null");
			m_builderCustomizer = customizer;
			return this;
		}

		/**
		 * 설정된 빌더로 {@link WebSocketObservable}을 생성한다.
		 *
		 * @return	생성된 {@link WebSocketObservable}
		 * @throws IllegalArgumentException {@link #uri(String)}이 호출되지 않은 경우
		 */
		public WebSocketObservable build() {
			Preconditions.checkNotNullArgument(m_uri, "uri should be set");
			return new WebSocketObservable(this);
		}
	}

	/**
	 * WebSocket 메시지의 sealed 마커 인터페이스.
	 * 허용된 구체 타입은 {@link TextMessage}와 {@link BinaryMessage}뿐이다.
	 * <p>
	 * sealed로 정의되어 있어 {@code switch} 패턴 매칭에서 모든 분기 처리 시 컴파일러가
	 * exhaustiveness를 검증해준다.
	 */
	public static sealed interface Message permits TextMessage, BinaryMessage { };

	/**
	 * WebSocket 텍스트 메시지.
	 * <p>
	 * {@link Builder#collectTextFragments collectTextFragments}={@code true} 모드에서는
	 * fragment 누적 후 합쳐진 단일 메시지로 emit되며 {@link #isLast()}는 항상 {@code true}.
	 * {@code false} 모드에서는 fragment 도착 즉시 emit되며 {@code isLast()}는 fragment 의 원본 플래그.
	 */
	public static final class TextMessage implements Message {
		private final String m_text;
		private final boolean m_last;

		public TextMessage(String text, boolean last) {
			m_text = text;
			m_last = last;
		}

		/**
		 * @return	텍스트 메시지 본문
		 */
		public String getText() {
			return m_text;
		}

		/**
		 * @return	본 메시지가 fragment 시퀀스의 마지막인지 여부
		 */
		public boolean isLast() {
			return m_last;
		}
	};

	/**
	 * WebSocket 바이너리 메시지.
	 * <p>
	 * {@link Builder#collectBinaryFragments collectBinaryFragments}={@code true} 모드에서는
	 * fragment 누적 후 합쳐진 단일 메시지로 emit되며 {@link #isLast()}는 항상 {@code true}.
	 * {@code false} 모드에서는 fragment 도착 즉시 emit되며 {@code isLast()}는 fragment 의 원본 플래그.
	 */
	public static final class BinaryMessage implements Message {
		private final byte[] m_data;
		private final boolean m_last;

		public BinaryMessage(byte[] data, boolean last) {
			m_data = data;
			m_last = last;
		}

		/**
		 * 메시지 데이터를 반환한다.
		 * <p>
		 * <b>주의</b>: 반환된 배열은 내부 저장소를 직접 노출한 것이므로 호출자가 수정해서는 안 된다.
		 * 안전한 복사본이 필요하면 {@code msg.getBytes().clone()} 또는
		 * {@code java.util.Arrays.copyOf(msg.getBytes(), ...)}을 사용한다.
		 * {@link java.nio.ByteBuffer} 뷰가 필요하면 {@code ByteBuffer.wrap(msg.getBytes())}로 감싼다.
		 *
		 * @return	메시지 데이터 (직접 노출되는 내부 배열)
		 */
		public byte[] getBytes() {
			return m_data;
		}

		/**
		 * @return	본 메시지가 fragment 시퀀스의 마지막인지 여부
		 */
		public boolean isLast() {
			return m_last;
		}
	};

	/**
	 * WebSocket 연결 및 dispose 를 관리하는 RxJava {@link Disposable}.
	 * <p>
	 * downstream observer 의 {@code onNext}/{@code onError}/{@code onComplete} 게이트 역할을 하며,
	 * dispose 또는 종료 시 정확히 한 번만 종료 신호를 발송하고 WebSocket을 close한다.
	 */
	private static final class WebSocketDisposable implements Disposable {
		private final Observer<? super Message> m_downstream;
		private final Duration m_closeTimeout;
		private final AtomicBoolean m_disposed = new AtomicBoolean(false);
		/** 상대의 close 프레임을 받았거나 연결 오류가 나면 완료된다. 연결을 닫을 때 상대의 응답을 기다리는 데 쓴다. */
		private final CompletableFuture<Void> m_peerClosed = new CompletableFuture<>();

		// 나중에 buildAsync 완료 시 세팅
		private volatile WebSocket m_webSocket;

		private WebSocketDisposable(Observer<? super Message> downstream, Duration closeTimeout) {
			m_downstream = downstream;
			m_closeTimeout = closeTimeout;
		}

		void setWebSocket(WebSocket webSocket) {
			m_webSocket = webSocket;
			// buildAsync 도중 dispose() 가 먼저 호출됐다면, 비어 있던 m_webSocket 때문에
			// closeQuietly가 실제 close를 못 보냈다. 늦게 도착한 WebSocket을 즉시 정리해 leak 방지.
			if ( m_disposed.get() ) {
				closeQuietly("disposed");
			}
		}

		void onMessage(Message message) {
			if ( !m_disposed.get() ) {
				m_downstream.onNext(message);
			}
		}

		void onError(Throwable t) {
			if ( m_disposed.compareAndSet(false, true) ) {
				m_downstream.onError(t);
				closeQuietly("error");
			}
		}

		/** 연결 오류로 더 이상 수신할 수 없게 되었다. */
		void onConnectionError(Throwable t) {
			m_peerClosed.complete(null);
			onError(t);
		}

		void onComplete(int statusCode, String reason) {
			// 상대가 close를 보냈으므로 응답을 기다리는 쪽(앞선 dispose 등)에 먼저 알린다.
			m_peerClosed.complete(null);
			if ( m_disposed.compareAndSet(false, true) ) {
				// 정상 종료 코드(1000, 1001, 1005)만 완료로 보고, 그 밖의 코드(예: 1006, 1011)는 오류로 알린다.
				// 서버 장애로 끊긴 것을 구독자가 정상 완료로 오해하지 않게 하기 위해서다.
				if ( WebSocketClosedException.isNormalClosure(statusCode) ) {
					m_downstream.onComplete();
				}
				else {
					m_downstream.onError(new WebSocketClosedException(statusCode, reason));
				}
				// 응답 close 프레임은 정상 종료 코드로 보낸다. 받은 코드(예: 1005, 1006)는 클라이언트가 보낼 수 없는 경우가 있다.
				closeQuietly("normal closure");
			}
		}

		@Override
		public void dispose() {
			if ( m_disposed.compareAndSet(false, true) ) {
				closeQuietly("disposed");
			}
		}

		@Override
		public boolean isDisposed() {
			return m_disposed.get();
		}

		private void closeQuietly(String reason) {
			WebSocket ws = m_webSocket;
			if ( ws != null ) {
				WebSocketCloser.close(ws, WebSocket.NORMAL_CLOSURE, reason, m_closeTimeout, m_peerClosed, s_logger);
			}
		}
	}

	/**
	 * {@link java.net.http.WebSocket.Listener} 구현체.
	 * <p>
	 * WebSocket 콜백을 {@link WebSocketDisposable}을 통해 RxJava {@link Observer}로 전달하며,
	 * fragment 누적/패스스루 모드를 outer class 의 설정에 따라 적용한다.
	 */
	private final class RxWebSocketListener implements Listener {
		private final WebSocketDisposable m_disposable;
		private StringBuilder m_textMessageBuilder = new StringBuilder();
		private ByteArrayOutputStream m_binaryMessageCollector = new ByteArrayOutputStream();

		RxWebSocketListener(WebSocketDisposable disposable) {
			this.m_disposable = disposable;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			// 처음에 요청할 메시지 개수 지정 (backpressure 유사).
			// Listener.super.onOpen 도 request(1)을 호출하므로 중복 호출 방지를 위해 super는 호출하지 않는다.
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if ( !m_disposable.isDisposed() ) {
				if ( m_collectTextFragments ) {
					// 모든 fragment를 누적; last=true에서만 emit
					if ( (long)m_textMessageBuilder.length() + data.length() > m_maxTextMessageLength ) {
						// 모은 내용을 버리고 버퍼의 메모리도 돌려준 뒤 오류로 끝낸다.
						m_textMessageBuilder = new StringBuilder();
						m_disposable.onError(new IllegalStateException(
							String.format("text message exceeds the maximum length: %d", m_maxTextMessageLength)));
					}
					else {
						m_textMessageBuilder.append(data);
						if ( last ) {
							String str = m_textMessageBuilder.toString();
							m_textMessageBuilder.setLength(0);
							m_disposable.onMessage(new TextMessage(str, last));
						}
					}
				}
				else {
					// fragment 단위로 즉시 emit
					m_disposable.onMessage(new TextMessage(data.toString(), last));
				}
			}

			// 다음 메시지 1개 더 요청
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			if ( !m_disposable.isDisposed() ) {
				byte[] bytes = new byte[data.remaining()];
				data.get(bytes);

				if ( m_collectBinaryFragments ) {
					// 모든 fragment를 누적; last=true에서만 emit
					if ( (long)m_binaryMessageCollector.size() + bytes.length > m_maxBinaryMessageLength ) {
						// 모은 내용을 버리고 버퍼의 메모리도 돌려준 뒤 오류로 끝낸다.
						m_binaryMessageCollector = new ByteArrayOutputStream();
						m_disposable.onError(new IllegalStateException(
							String.format("binary message exceeds the maximum length: %d", m_maxBinaryMessageLength)));
					}
					else {
						m_binaryMessageCollector.writeBytes(bytes);
						if ( last ) {
							byte[] fullData = m_binaryMessageCollector.toByteArray();
							m_binaryMessageCollector.reset();
							m_disposable.onMessage(new BinaryMessage(fullData, last));
						}
					}
				}
				else {
					// fragment 단위로 즉시 emit
					m_disposable.onMessage(new BinaryMessage(bytes, last));
				}
			}

			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			m_disposable.onComplete(statusCode, reason);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			m_disposable.onConnectionError(error);
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			// JDK가 pong을 자동으로 회신하므로 여기서 sendPong()을 부르지 않는다. 부르면 pong이 두 번 나가고,
			// super.onPing()이 null을 반환한 뒤(=버퍼 즉시 회수) 비동기 sendPong()이 그 버퍼를 읽게 된다.
	        return WebSocket.Listener.super.onPing(webSocket, message);
	    }
	}
}
