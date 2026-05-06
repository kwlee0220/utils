package utils.websocket;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

import utils.Utilities;
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
 *       (이때 {@code isLast()}는 항상 {@code true}).</li>
 *   <li>{@code false} — 각 fragment를 즉시 fragment 단위로 emit ({@code isLast()}는 원본 플래그 그대로).</li>
 * </ul>
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

	private final URI m_uri;
	private final HttpClient m_client;
	private final boolean m_collectTextFragments;
	private final boolean m_collectBinaryFragments;

	private WebSocketObservable(Builder builder) {
		Utilities.checkNotNullArgument(builder, "builder is null");

		m_uri = builder.m_uri;
		m_client = builder.m_client;
		m_collectTextFragments = builder.m_collectTextFragments;
		m_collectBinaryFragments = builder.m_collectBinaryFragments;
	}

	@Override
	protected void subscribeActual(Observer<? super Message> downstream) {
		// 구독마다 새로운 WebSocket 연결
		WebSocketDisposable disposable = new WebSocketDisposable(downstream);
		downstream.onSubscribe(disposable);

		m_client.newWebSocketBuilder()
				.buildAsync(m_uri, new RxWebSocketListener(disposable))
				.whenComplete((ws, error) -> {
					disposable.setWebSocket(ws);
					if ( error != null ) {
						// 연결 자체가 실패한 경우
						disposable.onError(error);
					}
				});
	}

	/**
	 * 새 {@link Builder} 인스턴스를 반환한다.
	 *
	 * @return	기본 설정의 빌더 ({@link Builder#collectTextFragments collectTextFragments}=true,
	 * 			{@link Builder#collectBinaryFragments collectBinaryFragments}=true,
	 * 			{@code httpClient}=새 인스턴스)
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
		private HttpClient m_client = HttpClient.newHttpClient();
		private boolean m_collectTextFragments = true;
		private boolean m_collectBinaryFragments = true;

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
		 * 사용할 {@link HttpClient}을 설정한다. 미설정 시 기본 {@code HttpClient.newHttpClient()}
		 * 인스턴스가 사용된다.
		 *
		 * @param client	HTTP 클라이언트
		 * @return			this
		 */
		public Builder httpClient(HttpClient client) {
			m_client = client;
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
		 * 설정된 빌더로 {@link WebSocketObservable}을 생성한다.
		 *
		 * @return	생성된 {@link WebSocketObservable}
		 * @throws IllegalArgumentException {@link #uri(String)}이 호출되지 않은 경우
		 */
		public WebSocketObservable build() {
			Utilities.checkNotNullArgument(m_uri, "uri should be set");
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
		private final AtomicBoolean m_disposed = new AtomicBoolean(false);

		// 나중에 buildAsync 완료 시 세팅
		private volatile WebSocket m_webSocket;

		private WebSocketDisposable(Observer<? super Message> downstream) {
			this.m_downstream = downstream;
		}

		void setWebSocket(WebSocket webSocket) {
			this.m_webSocket = webSocket;
			// buildAsync 도중 dispose() 가 먼저 호출됐다면, 비어 있던 m_webSocket 때문에
			// closeQuietly가 실제 close를 못 보냈다. 늦게 도착한 WebSocket을 즉시 정리해 leak 방지.
			if ( webSocket != null && m_disposed.get() ) {
				try {
					webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "disposed");
				}
				catch ( Exception ignored ) {
				}
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
				closeQuietly(WebSocket.NORMAL_CLOSURE, "error");
			}
		}

		void onComplete(int statusCode, String reason) {
			if ( m_disposed.compareAndSet(false, true) ) {
				m_downstream.onComplete();
				closeQuietly(statusCode, reason);
			}
		}

		@Override
		public void dispose() {
			if ( m_disposed.compareAndSet(false, true) ) {
				closeQuietly(WebSocket.NORMAL_CLOSURE, "disposed");
			}
		}

		@Override
		public boolean isDisposed() {
			return m_disposed.get();
		}

		private void closeQuietly(int statusCode, String reason) {
			WebSocket ws = m_webSocket;
			if ( ws != null ) {
				try {
					ws.sendClose(statusCode, reason);
				}
				catch ( Exception e ) {
					s_logger.debug("ignoring close failure: statusCode={}, reason={}",
									statusCode, reason, e);
				}
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
		private final StringBuilder m_textMessageBuilder = new StringBuilder();
		private final ByteArrayOutputStream m_binaryMessageCollector = new ByteArrayOutputStream();

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
				String str = data.toString();
				if ( m_collectTextFragments ) {
					// 모든 fragment를 누적; last=true에서만 emit
					m_textMessageBuilder.append(str);

					if ( last ) {
						str = m_textMessageBuilder.toString();
						m_textMessageBuilder.setLength(0);
						m_disposable.onMessage(new TextMessage(str, last));
					}
				}
				else {
					// fragment 단위로 즉시 emit
					m_disposable.onMessage(new TextMessage(str, last));
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
					try {
						m_binaryMessageCollector.write(bytes);
					}
					catch ( Exception e ) {
						onError(webSocket, e);
						return CompletableFuture.completedFuture(null);
					}

					if ( last ) {
						byte[] fullData = m_binaryMessageCollector.toByteArray();
						m_binaryMessageCollector.reset();
						m_disposable.onMessage(new BinaryMessage(fullData, last));
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
			m_disposable.onError(error);
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			webSocket.sendPong(message);
	        return WebSocket.Listener.super.onPing(webSocket, message);
	    }
	}
}
