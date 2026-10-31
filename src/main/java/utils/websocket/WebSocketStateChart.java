package utils.websocket;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.RuntimeExecutionException;
import utils.Throwables;
import utils.func.Result;
import utils.statechart.Signal;
import utils.statechart.StateChart;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.WebSocketError;


/**
 * WebSocket 도메인 동작을 추가한 {@link StateChart}.
 * <p>
 * {@link WebSocketContext}와 짝을 이루며, 다음 책임을 수행한다:
 * <ul>
 *   <li>{@link WebSocketListener}를 보유하여 WebSocket 콜백을 도메인 신호로 변환</li>
 *   <li>{@link #setPingInterval(Duration)} / {@link #setPongTimeout(Duration)}로 keep-alive 설정</li>
 *   <li>{@link #sendText(String, boolean)} / {@link #sendBinary(byte[], boolean)} 송신 API 제공</li>
 *   <li>{@link Signals.WebSocketError} / {@link Signals.ConnectionClosed} 신호가 사용자 transition에서
 *       처리되지 않을 때 차트를 fail / cancel 상태로 전이시키는 fall-back 보장</li>
 * </ul>
 * <p>
 * <b>라이프사이클</b>:
 * <ol>
 *   <li>{@code new WebSocketStateChart(context)} — listener가 함께 생성된다.</li>
 *   <li>(선택, 차트 시작 전) 설정: keep-alive({@link #setPingInterval} / {@link #setPongTimeout}),
 *       접속({@link #setHttpClient}, {@link #setConnectTimeout}), 종료({@link #setCloseTimeout}),
 *       수신({@link #setMaxTextMessageLength}).</li>
 *   <li>차트 시작 후 {@link States.WebSocketConnectingState} 같은 상태가 연결을 시도하고, 성공하면
 *       {@link #setWebSocket(WebSocket)}으로 등록한 뒤 {@link Signals.Connected}를 보내고 수신을 시작한다.</li>
 *   <li>차트 실행 중에 {@code sendText}/{@code sendBinary}로 송신 가능.</li>
 *   <li>종료 신호 도착 시 fall-back이 차트를 종료시킨다. 어떤 경로로 끝나든 연결은 닫힌다.</li>
 * </ol>
 *
 * @param <C>	{@link WebSocketContext} 서브타입의 self-bound 타입 파라미터
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketStateChart<C extends WebSocketContext<C>> extends StateChart<C> {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketStateChart.class);

	/** 기본 접속 시간 제한. 응답 없는 서버에서 OS의 TCP 시간 제한(수 분)까지 기다리지 않도록 둔다. */
	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
	/** 기본 close 프레임 전송 시간 제한. 상대가 멈춰도 차트 잠금을 무기한 잡고 있지 않도록 둔다. */
	public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5);
	/**
	 * 텍스트 메시지 조각을 모을 때의 기본 최대 길이(문자 수, 16M). 상대가 조각을 끝없이 보내도 메모리가 계속 늘지 않도록 둔다.
	 */
	public static final int DEFAULT_MAX_TEXT_MESSAGE_LENGTH = 16 * 1024 * 1024;

	private final WebSocketListener<C> m_socketListener;
	@Nullable private volatile WebSocket m_webSocket;
	private volatile @Nullable HttpClient m_httpClient;
	private volatile @Nullable Duration m_connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private volatile Duration m_closeTimeout = DEFAULT_CLOSE_TIMEOUT;
	private volatile int m_maxTextMessageLength = DEFAULT_MAX_TEXT_MESSAGE_LENGTH;
	private volatile @Nullable Consumer<WebSocket.Builder> m_builderCustomizer;

	/**
	 * 주어진 컨텍스트로 WebSocket 상태 차트를 생성한다.
	 * <p>
	 * 생성과 동시에 내부 {@link WebSocketListener}가 초기화된다. 실제 WebSocket 연결은 차트 시작 후
	 * 사용자가 정의한 상태(예: {@link States.WebSocketConnectingState})에서 이루어진다.
	 *
	 * @param context	WebSocket 도메인 컨텍스트
	 */
	public WebSocketStateChart(C context) {
		super(context);

		setLogger(s_logger);
		m_socketListener = new WebSocketListener<>(this);

		// 어떤 경로로 종료되든 연결이 닫히도록 보장한다. 종료 상태({@link States.CompletedState} 등)로
		// 전이한 경우는 그 상태의 enter()가 이미 닫았으므로 여기서의 호출은 no-op이 되고,
		// complete()/fail()/cancel() 직접 호출이나 사용자 정의 final state처럼 종료 상태를
		// 거치지 않는 경로에서는 이 훅이 유일한 close 지점이 된다.
		whenFinished(result -> closeConnection(WebSocket.NORMAL_CLOSURE, closeReasonOf(result)));
	}

	/**
	 * 현재 활성 WebSocket 연결을 반환한다.
	 *
	 * 한 번 등록된 참조는 해제되지 않는다. 연결이 close된 뒤에도, 차트가 종료된 뒤에도 마지막
	 * {@link WebSocket}을 그대로 반환한다.
	 *
	 * @return	등록된 {@link WebSocket}. {@link #setWebSocket(WebSocket)} 호출 전이면 {@code null}.
	 */
	public @Nullable WebSocket getWebSocket() {
		return m_webSocket;
	}

	/**
	 * 활성 WebSocket 연결을 등록한다.
	 * <p>
	 * 본 메소드는 일반적으로 {@link States.WebSocketConnectingState} 같은 상태가 비동기 연결 결과를 받았을 때
	 * 차트 lifetime 동안 정확히 한 번 호출하는 프레임워크 내부용 콜백이다.
	 * 외부 코드가 직접 호출할 일은 없다.
	 * <p>
	 * 이미 다른 WebSocket이 등록된 차트에 다시 호출되면 {@link IllegalStateException}을 던져
	 * 재설정을 거부한다. 재연결이 필요한 경우 새 {@link WebSocketStateChart} 인스턴스를 생성해야 한다.
	 *
	 * @param ws	연결된 WebSocket (non-null)
	 * @throws IllegalArgumentException	{@code ws}가 {@code null}인 경우
	 * @throws IllegalStateException	이미 WebSocket이 등록된 경우
	 */
	protected void setWebSocket(WebSocket ws) {
		Preconditions.checkNotNullArgument(ws, "ws is null");
		Preconditions.checkState(m_webSocket == null, "WebSocket is already set on this chart");

		m_webSocket = ws;
	}

	/**
	 * WebSocket 연결을 정상 종료 코드({@link WebSocket#NORMAL_CLOSURE})로 닫는다.
	 * (2026-10-31 이전 이름은 {@code close()}였다. 차트(실행)를 끝내는 것으로 읽히기 쉬워 바꾸었다.)
	 *
	 * @see #closeConnection(int, String)
	 */
	public void closeConnection() {
		closeConnection(WebSocket.NORMAL_CLOSURE, "normal closure");
	}

	/**
	 * WebSocket 연결을 정상 종료 코드({@link WebSocket#NORMAL_CLOSURE})로 닫고, 종료 결과를 반환한다.
	 *
	 * @return	종료 절차가 끝나면 완료되는 future
	 * @see #closeConnectionAsync(int, String)
	 */
	public CompletableFuture<Void> closeConnectionAsync() {
		return closeConnectionAsync(WebSocket.NORMAL_CLOSURE, "normal closure");
	}

	/**
	 * WebSocket 연결을 닫고, 종료 절차가 끝나면 완료되는 future를 반환한다.
	 * <p>
	 * 종료 절차는 close 프레임 전송 → 상대의 close 응답 수신이다. 상대가 응답하지 않거나 전송이 막혀 close 시간 제한
	 * ({@link #setCloseTimeout(Duration)}) 안에 끝나지 않으면 {@link WebSocket#abort()}로 연결을 끊고, 전송이 실패해도
	 * 끊는다. 따라서 반환된 future는 늦어도 시간 제한 뒤에 <b>정상 완료</b>되며 예외로 끝나지 않는다. 등록된 연결이
	 * 없으면 이미 완료된 future를 반환한다. 이 호출이 차트에 미치는 영향은 {@link #closeConnection(int, String)}와 같다.
	 * (2026-10-31 이전 이름은 {@code closeAsync}였다.)
	 *
	 * @param statusCode	close 상태 코드
	 * @param reason		close 사유 문자열
	 * @return	종료 절차가 끝나면(상대의 응답 수신 또는 abort) 완료되는 future
	 */
	public CompletableFuture<Void> closeConnectionAsync(int statusCode, String reason) {
		return startClosing(statusCode, reason).closed().copy();
	}

	/**
	 * WebSocket 연결을 닫는다.
	 * <p>
	 * <b>Best-effort 연산이다.</b> 연결이 수립되기 전이라 보유한 WebSocket이 없거나, 이미 닫혔거나,
	 * 원격이 연결을 끊어 close 전송이 실패하는 경우를 모두 무시하고 조용히 반환한다. 이는 본 메소드가
	 * 종료 경로(종료 상태 진입, 차트 종료 훅)에서 호출되기 때문으로, 여기서 던진 예외가 상태 전이나
	 * 종료 통지를 실패로 뒤집지 않도록 하기 위함이다. 따라서 반복 호출도 안전하다.
	 * <p>
	 * close 프레임이 전송될 때까지 <b>블록</b>한다. 원격의 응답을 기다리지는 않는다. 상대가 멈춰 전송이 끝나지 않을 수
	 * 있으므로 close 시간 제한({@link #setCloseTimeout(Duration)}, 기본 {@link #DEFAULT_CLOSE_TIMEOUT})까지만 기다리고,
	 * 그 안에 끝나지 않으면 {@link WebSocket#abort()}로 연결을 끊는다. 본 메소드는 차트 잠금을 잡은 채(종료 상태의
	 * {@code enter()}, 종료 훅) 호출되므로, 무기한 기다리면 신호 처리와 종료 통지가 모두 멈춘다.
	 * <p>
	 * 상대의 close 응답은 반환 뒤에도 백그라운드에서 기다린다. 전송 후 시간 제한 안에 응답이 없으면 그때
	 * {@code abort()}한다. {@code sendClose()}는 출력만 닫으므로, 응답하지 않는 상대에게서 연결이 남지 않게 하기
	 * 위해서다(2026-10-31 이전에는 응답을 기다리지 않아 연결이 남을 수 있었다). 종료 결과가 필요하면
	 * {@link #closeConnectionAsync(int, String)}를 쓴다.
	 * <p>
	 * 차트가 종료되면 이 메소드가 자동으로 호출되므로, 정상적인 시나리오에서 외부 코드가 직접
	 * 호출할 일은 없다.
	 * <p>
	 * <b>본 메소드는 close 프레임만 보내지만, 차트도 끝날 수 있다.</b> 상대가 close 프레임으로 응답하면 리스너가
	 * {@link Signals.ConnectionClosed}를 차트에 보내고, 이를 처리하는 전이가 없으면 대체 종료
	 * ({@link #onUnhandledSignal(Signal)})가 차트를 취소한다(응답 코드가 비정상이면 실패). 연결만 끊고 차트를 계속
	 * 실행하려면 현재 상태에 {@code ConnectionClosed}를 처리하는 전이(예: {@link utils.statechart.Transitions#stay()})를
	 * 등록한다. 이때 연결은 닫혔으므로 이후의 송신은 실패하고, keep-alive를 설정했다면 pong 시간 초과로
	 * {@link Signals.WebSocketError}가 온다. 차트를 끝내려는 목적이면 본 메소드 대신 {@code cancel(true)}를 쓴다.
	 *
	 * @param statusCode	close 상태 코드
	 * @param reason		close 사유 문자열
	 */
	public void closeConnection(int statusCode, String reason) {
		// 종료 절차의 시간 제한이 frameSent도 끝내므로 여기서 따로 시간 제한을 두지 않는다.
		CompletableFuture<Void> frameSent = startClosing(statusCode, reason).frameSent();
		try {
			frameSent.get();
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			WebSocket ws = m_webSocket;
			if ( ws != null ) {
				ws.abort();
			}
		}
		catch ( ExecutionException e ) {
			// frameSent는 예외로 끝나지 않는다.
			getLogger().debug("ignored error while closing WebSocket: {}", ""+e.getCause());
		}
	}

	private WebSocketCloser.Closing startClosing(int statusCode, String reason) {
		WebSocket ws = m_webSocket;
		if ( ws == null ) {
			return WebSocketCloser.Closing.done();
		}
		// 상대의 close 응답(onClose)도 수신 요청이 있어야 전달된다. 수신을 시작하기 전(Connected 처리 중)에 닫는
		// 경우에도 응답을 받을 수 있도록 하나 더 요청한다. 이미 요청이 있어도 해가 없다.
		try {
			ws.request(1);
		}
		catch ( Throwable ignored ) { }
		return WebSocketCloser.close(ws, statusCode, reason, m_closeTimeout, m_socketListener.peerClosed(),
									getLogger());
	}

	/**
	 * 차트 종료 결과에 대응하는 close 사유 문자열을 반환한다.
	 *
	 * @param result	차트 종료 결과.
	 * @return	close 사유 문자열.
	 */
	private static String closeReasonOf(Result<?> result) {
		// 취소도 프로토콜 상으로는 정상적인 연결 종료이므로 실패인 경우만 구분한다.
		return result.isFailed() ? "closure due to an error" : "normal closure";
	}

	/**
	 * 패키지 내부(접속 상태, 테스트)에서 쓰는 listener 구현을 반환한다.
	 * <p>
	 * (2026-10-31까지 있던 공개 메소드 {@code getWebSocketListener()}는 지웠다. 접속 상태는 접속 시도마다 별도의
	 * listener({@link WebSocketListener#forAttempt})를 쓰고, 패키지 밖에서는 연결을 차트에 등록할 수 없어 쓸 곳이 없었다.)
	 *
	 * @return	listener 구현
	 */
	WebSocketListener<C> listener() {
		return m_socketListener;
	}

	/**
	 * Ping 발송 주기를 설정한다.
	 * <p>
	 * {@link #setPongTimeout(Duration)}와 함께 호출되어야 keep-alive가 활성화된다.
	 * 본 메소드는 listener가 사용되기 전 (= WebSocket 연결 시작 전) 에 호출되어야 효과가 있다.
	 *
	 * @param interval	ping 발송 주기 (1ms 이상)
	 * @throws IllegalArgumentException	{@code interval}이 {@code null}이거나 1ms 미만이거나 너무 큰 경우
	 * @throws IllegalStateException	차트가 이미 시작된 경우
	 */
	public void setPingInterval(Duration interval) {
		checkNotStarted();
		checkMillisDuration(interval, "interval");

		m_socketListener.setPingInterval(interval);
	}

	/**
	 * Pong 응답 대기 timeout을 설정한다.
	 * <p>
	 * {@link #setPingInterval(Duration)}와 함께 호출되어야 keep-alive가 활성화된다.
	 * timeout 안에 pong이 도착하지 않으면 차트는 {@link Signals.WebSocketError}로
	 * 실패 상태에 진입한다. 본 메소드는 listener가 사용되기 전에 호출되어야 한다.
	 *
	 * @param timeout	pong 응답 timeout (1ms 이상)
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 1ms 미만이거나 너무 큰 경우
	 * @throws IllegalStateException	차트가 이미 시작된 경우
	 */
	public void setPongTimeout(Duration timeout) {
		checkNotStarted();
		checkMillisDuration(timeout, "timeout");

		m_socketListener.setPongTimeout(timeout);
	}

	/**
	 * 밀리초 단위로 예약하는 시간 값(ping 주기, pong 대기, close 시간 제한)을 검증한다.
	 * <p>
	 * 예약은 {@link Duration#toMillis()}로 하므로 1ms 미만은 0ms가 된다. 그러면 pong 감시는 즉시 만료되어 매번 시간
	 * 초과가 되고, ping 주기는 예약 자체가 실패하며, close는 응답을 기다리지 않고 끊는다. 밀리초로 바꿀 수 없을 만큼 큰
	 * 값도 거부한다. (2026-10-31 이전에는 양수인지만 확인했다.)
	 *
	 * @param duration	검증할 값
	 * @param name		인자 이름(예외 메시지용)
	 * @throws IllegalArgumentException	{@code null}이거나 1ms 미만이거나 밀리초로 나타낼 수 없을 만큼 큰 경우
	 */
	static void checkMillisDuration(Duration duration, String name) {
		Preconditions.checkNotNullArgument(duration, name + " is null");
		Preconditions.checkArgument(duration.compareTo(Duration.ofMillis(1)) >= 0,
									"%s must be at least 1ms: %s", name, duration);
		try {
			duration.toMillis();
		}
		catch ( ArithmeticException e ) {
			throw new IllegalArgumentException(name + " is too large: " + duration);
		}
	}

	/**
	 * 접속 설정 메소드가 차트 시작 전에 호출되었는지 확인한다. 접속 설정은 접속 상태에 진입할 때 읽으므로 시작 뒤의
	 * 변경은 반영되지 않거나 일부만 반영된다. (2026-10-31 이전에는 거부하지 않았다.)
	 */
	private void checkNotStarted() {
		Preconditions.checkState(isNotStarted(), "connection settings must be set before the chart starts: %s", this);
	}

	/**
	 * 접속 직전에 {@link WebSocket.Builder}를 추가로 설정할 것을 지정한다. 접속 헤더({@code header(...)}),
	 * 서브프로토콜({@code subprotocols(...)}) 등 차트가 따로 제공하지 않는 설정에 쓴다.
	 * <p>
	 * 접속 상태가 클라이언트에서 빌더를 만들고 접속 시간 제한을 적용한 뒤, {@code buildAsync()} 직전에 호출한다.
	 * 여기서 {@code buildAsync()}를 호출하면 안 된다. 예외를 던지면 접속 상태의 {@code enter()}가 실패한다.
	 *
	 * @param customizer	빌더 설정 (non-null)
	 * @throws IllegalArgumentException	{@code customizer}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 이미 시작된 경우
	 */
	public void setWebSocketBuilderCustomizer(Consumer<WebSocket.Builder> customizer) {
		checkNotStarted();
		Preconditions.checkNotNullArgument(customizer, "customizer is null");
		m_builderCustomizer = customizer;
	}

	/**
	 * {@link #setWebSocketBuilderCustomizer(Consumer)}로 지정한 빌더 설정을 반환한다.
	 *
	 * @return	빌더 설정. 지정하지 않았으면 {@code null}.
	 */
	public @Nullable Consumer<WebSocket.Builder> getWebSocketBuilderCustomizer() {
		return m_builderCustomizer;
	}

	/**
	 * 접속에 사용할 {@link HttpClient}를 반환한다.
	 *
	 * @return	{@link #setHttpClient(HttpClient)}로 지정한 클라이언트. 지정하지 않았으면 차트들이 함께 쓰는 기본 클라이언트.
	 */
	public HttpClient getHttpClient() {
		HttpClient client = m_httpClient;
		return (client != null) ? client : SharedResources.httpClient();
	}

	/**
	 * 접속에 사용할 {@link HttpClient}를 지정한다.
	 * <p>
	 * 지정하지 않으면 차트들이 하나의 기본 클라이언트를 함께 쓴다. 접속마다 클라이언트를 새로 만들면 차트마다 selector
	 * 스레드가 생기기 때문이다. 프록시·SSL 등 별도 설정이 필요할 때 지정한다. 접속({@link States.WebSocketConnectingState} 진입) 전에 호출해야
	 * 반영된다. 지정한 클라이언트의 수명은 호출자가 관리한다.
	 *
	 * @param client	사용할 클라이언트 (non-null)
	 * @throws IllegalArgumentException	{@code client}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 이미 시작된 경우
	 */
	public void setHttpClient(HttpClient client) {
		checkNotStarted();
		Preconditions.checkNotNullArgument(client, "client is null");
		m_httpClient = client;
	}

	/**
	 * 접속 시간 제한을 반환한다.
	 *
	 * @return	접속 시간 제한. {@code null}이면 제한이 없다(OS의 TCP 시간 제한을 따른다).
	 */
	public @Nullable Duration getConnectTimeout() {
		return m_connectTimeout;
	}

	/**
	 * 접속 시간 제한을 지정한다. 기본값은 {@link #DEFAULT_CONNECT_TIMEOUT}이다.
	 * <p>
	 * 시간 안에 접속하지 못하면 {@link States.WebSocketConnectingState}는 {@link Signals.ConnectionFailed}(원인
	 * {@link java.net.http.HttpConnectTimeoutException})로 실패 상태에 전이한다. 접속 전에 호출해야 반영된다.
	 *
	 * @param timeout	접속 시간 제한(양수). {@code null}이면 제한을 두지 않는다.
	 * @throws IllegalArgumentException	{@code timeout}이 양수가 아닌 경우
	 * @throws IllegalStateException	차트가 이미 시작된 경우
	 */
	public void setConnectTimeout(@Nullable Duration timeout) {
		checkNotStarted();
		Preconditions.checkArgument(timeout == null || DurationUtils.isPositive(timeout),
									"timeout must be positive: %s", timeout);
		m_connectTimeout = timeout;
	}

	/**
	 * close 프레임 전송 시간 제한을 반환한다.
	 *
	 * @return	close 시간 제한.
	 */
	public Duration getCloseTimeout() {
		return m_closeTimeout;
	}

	/**
	 * 연결 종료 절차(close 프레임 전송과 상대의 close 응답 대기)의 시간 제한을 지정한다. 기본값은
	 * {@link #DEFAULT_CLOSE_TIMEOUT}이다. 넘으면 {@link WebSocket#abort()}로 연결을 끊는다.
	 *
	 * @param timeout	close 시간 제한 (1ms 이상)
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 1ms 미만이거나 너무 큰 경우
	 * @see #closeConnection(int, String)
	 */
	public void setCloseTimeout(Duration timeout) {
		checkMillisDuration(timeout, "timeout");
		m_closeTimeout = timeout;
	}

	/**
	 * 텍스트 메시지 조각을 모을 때의 최대 길이를 반환한다.
	 *
	 * @return	최대 길이(문자 수).
	 */
	public int getMaxTextMessageLength() {
		return m_maxTextMessageLength;
	}

	/**
	 * 텍스트 메시지 조각을 모을 때의 최대 길이를 지정한다. 기본값은 {@link #DEFAULT_MAX_TEXT_MESSAGE_LENGTH}이다.
	 * <p>
	 * 리스너는 조각으로 나뉘어 온 텍스트 메시지를 마지막 조각까지 모아 {@link Signals.TextMessage} 하나로 전달한다.
	 * 모은 길이가 이 값을 넘으면 모은 내용을 버리고 {@link Signals.WebSocketError}(원인 {@link IllegalStateException})를
	 * 전달하며, 그 메시지의 남은 조각도 버린다. 처리되지 않은 {@code WebSocketError}는 차트를 실패시킨다. 사용자 전이가
	 * 이 오류를 처리해 차트를 계속 실행하면 다음 메시지부터 다시 받는다.
	 * <p>
	 * 바이너리 데이터는 모으지 않고 조각마다 {@link Signals.BinaryMessage}로 전달하므로 이 제한이 적용되지 않는다.
	 * 실행 중에 바꾸면 다음 조각부터 적용된다.
	 *
	 * @param maxLength	최대 길이(문자 수, 양수)
	 * @throws IllegalArgumentException	{@code maxLength}가 양수가 아닌 경우
	 */
	public void setMaxTextMessageLength(int maxLength) {
		Preconditions.checkArgument(maxLength > 0, "maxLength must be positive: %s", maxLength);
		m_maxTextMessageLength = maxLength;
	}

	/**
	 * 텍스트 메시지 하나를 비동기로 송신한다. {@code sendText(text, true)}와 같다.
	 *
	 * @param text	송신할 텍스트 (non-null)
	 * @return		송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @see #sendText(String, boolean)
	 */
	public CompletableFuture<WebSocket> sendText(String text) {
		return sendText(text, true);
	}

	/**
	 * 텍스트 메시지 하나를 송신하고 완료될 때까지 블록한다. {@code sendTextSync(text, true)}와 같다.
	 *
	 * @param text	송신할 텍스트 (non-null)
	 * @throws InterruptedException		대기 중에 쓰레드가 인터럽트된 경우
	 * @see #sendTextSync(String, boolean)
	 */
	public void sendTextSync(String text) throws InterruptedException {
		sendTextSync(text, true);
	}

	/**
	 * 텍스트 메시지(또는 그 조각)를 비동기로 송신한다. 조각으로 나눠 보내지 않으면 {@link #sendText(String)}를 쓴다.
	 * <p>
	 * 차트가 실행 중이고 WebSocket 연결이 활성 상태일 때만 호출 가능하다.
	 * <p>
	 * <b>연속 송신 제약</b>: JDK {@link WebSocket}은 텍스트·바이너리 송신을 한 번에 하나만 받는다. 이전
	 * {@code sendText}/{@link #sendBinary(byte[], boolean)}가 반환한 future가 완료되기 전에 다음 송신을 호출하면
	 * 새 송신은 보내지지 않고, 반환된 future가 {@link IllegalStateException}으로 실패한다. 예외가 던져지는 것이
	 * 아니라 future가 실패하므로, 결과를 확인하지 않으면 메시지가 조용히 사라진다. {@code last=false}로 시작한
	 * 바이너리 메시지를 마지막 조각까지 보내기 전에 텍스트를 보내도(그 반대도) 같은 방식으로 실패한다.
	 * <p>
	 * 본 메소드는 송신을 직렬화하지 않는다. 따라서
	 * <ul>
	 *   <li>한 스레드에서 연달아 보낼 때는 {@link #sendTextSync(String, boolean)}를 쓰거나, 이전 future가 완료된 뒤
	 *       다음 송신을 호출한다(예: {@code thenCompose}).</li>
	 *   <li>여러 스레드가 보낼 수 있으면 호출 측에서 직렬화한다. 예를 들어 동기 변형({@code *Sync})을 하나의 직렬
	 *       실행기(단일 스레드 executor, {@link utils.thread.CallerRunsSerialExecutor} 등)에서만 호출한다.</li>
	 * </ul>
	 * keep-alive의 ping은 텍스트·바이너리와 별개로 관리되므로 이 제약에 걸리지 않는다.
	 *
	 * @param text	송신할 텍스트 (non-null)
	 * @param last	이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @return		송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @throws IllegalArgumentException	{@code text}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 실행 중이 아니거나 WebSocket이 연결되지 않은 경우
	 */
	public CompletableFuture<WebSocket> sendText(String text, boolean last) {
		Preconditions.checkNotNullArgument(text, "text is null");
		Preconditions.checkState(isRunning(), "WebSocketStateChart is not running");
		Preconditions.checkState(m_webSocket != null, "WebSocket is not connected");

		getLogger().debug("sending text: {}", Signals.TextMessage.abbreviate(text));
		return m_webSocket.sendText(text, last);
	}

	/**
	 * 텍스트 메시지를 송신하고 완료될 때까지 블록한다.
	 * <p>
	 * 내부적으로 {@link #sendText(String, boolean)}의 결과 future를 {@code get()}으로 대기한다.
	 * 송신 중 예외가 발생하면 원인을 unwrap한 {@link RuntimeExecutionException}으로 전파된다.
	 * 대기 중 쓰레드가 인터럽트되면 {@link InterruptedException}이 호출자에게 전파된다.
	 * <p>
	 * 송신 완료를 기다리므로 한 스레드에서 연달아 호출해도 연속 송신 제약({@link #sendText(String, boolean)} 참조)에
	 * 걸리지 않는다. 여러 스레드가 동시에 호출하면 걸릴 수 있으며, 이때는
	 * {@code IllegalStateException}을 원인으로 하는 {@link RuntimeExecutionException}이 발생한다.
	 *
	 * @param text	송신할 텍스트 (non-null)
	 * @param last	이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @throws InterruptedException		대기 중에 쓰레드가 인터럽트된 경우
	 * @throws RuntimeExecutionException	송신 중 예외가 발생한 경우 (원인은 {@link Throwable#getCause()})
	 */
	public void sendTextSync(String text, boolean last) throws InterruptedException {
		try {
			sendText(text, last).get();
		}
		catch ( ExecutionException e ) {
			throw new RuntimeExecutionException(Throwables.unwrapThrowable(e));
		}
	}

	/**
	 * 바이너리 메시지 하나를 비동기로 송신한다. {@code sendBinary(binary, true)}와 같다.
	 *
	 * @param binary	송신할 바이트 배열 (non-null)
	 * @return			송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @see #sendBinary(byte[], boolean)
	 */
	public CompletableFuture<WebSocket> sendBinary(byte[] binary) {
		return sendBinary(binary, true);
	}

	/**
	 * 바이너리 메시지 하나를 송신하고 완료될 때까지 블록한다. {@code sendBinarySync(binary, true)}와 같다.
	 *
	 * @param binary	송신할 바이트 배열 (non-null)
	 * @throws InterruptedException		대기 중에 쓰레드가 인터럽트된 경우
	 * @see #sendBinarySync(byte[], boolean)
	 */
	public void sendBinarySync(byte[] binary) throws InterruptedException {
		sendBinarySync(binary, true);
	}

	/**
	 * 바이너리 메시지(또는 그 조각)를 비동기로 송신한다. 조각으로 나눠 보내지 않으면 {@link #sendBinary(byte[])}를 쓴다.
	 * <p>
	 * 차트가 실행 중이고 WebSocket 연결이 활성 상태일 때만 호출 가능하다. {@code binary} 배열은 복사하지 않고
	 * 그대로 보내므로, 반환된 future가 완료되기 전에는 배열을 수정하지 않는다.
	 * <p>
	 * {@link #sendText(String, boolean)}와 같은 <b>연속 송신 제약</b>이 적용된다. 이전 텍스트·바이너리 송신이
	 * 완료되기 전에 호출하면 반환된 future가 {@link IllegalStateException}으로 실패한다. 피하는 방법은
	 * {@link #sendText(String, boolean)}의 설명을 참조한다.
	 *
	 * @param binary	송신할 바이트 배열 (non-null)
	 * @param last		이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @return			송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @throws IllegalArgumentException	{@code binary}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 실행 중이 아니거나 WebSocket이 연결되지 않은 경우
	 */
	public CompletableFuture<WebSocket> sendBinary(byte[] binary, boolean last) {
		Preconditions.checkNotNullArgument(binary, "binary is null");
		Preconditions.checkState(isRunning(), "WebSocketStateChart is not running");
		Preconditions.checkState(m_webSocket != null, "WebSocket is not connected");

		getLogger().debug("sending binary: len={}", binary.length);
		return m_webSocket.sendBinary(ByteBuffer.wrap(binary), last);
	}

	/**
	 * 바이너리 메시지를 송신하고 완료될 때까지 블록한다.
	 * <p>
	 * 내부적으로 {@link #sendBinary(byte[], boolean)}의 결과 future를 {@code get()}으로 대기한다.
	 * 송신 중 예외가 발생하면 원인을 unwrap한 {@link RuntimeExecutionException}으로 전파된다.
	 * 대기 중 쓰레드가 인터럽트되면 {@link InterruptedException}이 호출자에게 전파된다.
	 * <p>
	 * {@link #sendTextSync(String, boolean)}와 마찬가지로, 한 스레드에서 연달아 호출하면 연속 송신 제약에 걸리지
	 * 않지만 여러 스레드가 동시에 호출하면 걸릴 수 있다.
	 *
	 * @param binary	송신할 바이트 배열 (non-null)
	 * @param last		이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @throws InterruptedException		대기 중에 쓰레드가 인터럽트된 경우
	 * @throws RuntimeExecutionException	송신 중 예외가 발생한 경우 (원인은 {@link Throwable#getCause()})
	 */
	public void sendBinarySync(byte[] binary, boolean last) throws InterruptedException {
		try {
			sendBinary(binary, last).get();
		}
		catch ( ExecutionException e ) {
			throw new RuntimeExecutionException(Throwables.unwrapThrowable(e));
		}
	}

	/**
	 * 진입해 있는 어떤 상태도 전이를 선택하지 않은 신호에 대해 fall-back 처리를 수행한다.
	 * <ul>
	 *   <li>{@link Signals.WebSocketError} → 차트를 그 오류로 실패시킨다.</li>
	 *   <li>{@link Signals.ConnectionClosed} → 정상 종료 코드({@link WebSocketClosedException#isNormalClosure(int)}:
	 *       1000, 1001, 1005)이면 차트를 취소하고, 그 밖의 코드(예: 1006, 1011)이면 {@link WebSocketClosedException}으로
	 *       실패시킨다. 서버 장애로 끊긴 것과 정상 종료·취소를 호출자가 구분할 수 있게 하기 위해서다.
	 *       (2026-10-31 이전에는 상태 코드와 관계없이 취소했다.)</li>
	 * </ul>
	 * 종료는 {@code StateChart}의 경로({@code super.fail}/{@code super.cancel})로 요청한다. 하위 클래스가
	 * {@link #fail(Throwable)}이나 {@code cancel(boolean)}을 다른 동작(예: 중지 신호 전송)으로 재정의했더라도
	 * fall-back은 그 재정의를 거치지 않으므로, 현재 상태가 그 신호를 처리하지 않아도 차트가 끝난다.
	 * 이 fall-back은 사용자가 WebSocketError / ConnectionClosed 처리 transition을 정의하지 않았더라도
	 * WebSocket이 비정상 종료되었을 때 차트가 dangling 상태에 머무르지 않도록 보장한다.
	 * <p>
	 * 차트가 실행 중일 때 신호를 처리하는 스레드에서 차트 lock을 보유한 채 호출된다. 이전에는
	 * {@code handleSignal()}을 재정의해 반환값이 비어 있으면 fall-back을 수행했으나, 신호가 큐에 들어가
	 * 나중에 처리되는 경우와 구분할 수 없어 이 hook으로 옮겼다.
	 *
	 * @param signal	처리되지 않은 신호
	 */
	@Override
	protected void onUnhandledSignal(Signal signal) {
		// WebSocket Listener에서 onClose, onError 이벤트가 발생되었지만,
		// 해당 이벤트가 처리되지 않은 경우에 대비하여 fall-back 처리를 수행함.
		// 	onError: 상태머신을 실패 상태로 전이시킴.
		// 	onClose: 정상 종료 코드이면 취소, 그 밖의 코드이면 실패.
		// 	하위 클래스의 재정의를 거치지 않도록 super의 종료 경로를 쓴다.
		if ( signal instanceof WebSocketError errMsg ) {	// onError 등 연결 오류가 도착한 경우
			super.fail(errMsg.getError());
		}
		else if ( signal instanceof ConnectionClosed closed ) {	// onClose 메시지가 도착한 경우
			if ( WebSocketClosedException.isNormalClosure(closed.getStatusCode()) ) {
				super.cancel(true);
			}
			else {
				super.fail(new WebSocketClosedException(closed.getStatusCode(), closed.getReason()));
			}
		}
	}
}
