package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.RuntimeExecutionException;
import utils.Throwables;
import utils.Utilities;
import utils.statechart.Signal;
import utils.statechart.StateChart;
import utils.statechart.Transition;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.ErrorMessage;


/**
 * WebSocket 도메인 동작을 추가한 {@link StateChart}.
 * <p>
 * {@link WebSocketContext}와 짝을 이루며, 다음 책임을 수행한다:
 * <ul>
 *   <li>{@link WebSocketListener}를 보유하여 WebSocket 콜백을 도메인 신호로 변환</li>
 *   <li>{@link #setPingInterval(Duration)} / {@link #setPongTimeout(Duration)}로 keep-alive 설정</li>
 *   <li>{@link #sendText(String, boolean)} / {@link #sendBinary(byte[], boolean)} 송신 API 제공</li>
 *   <li>{@link Signals.ErrorMessage} / {@link Signals.ConnectionClosed} 신호가 사용자 transition에서
 *       처리되지 않을 때 차트를 fail / cancel 상태로 전이시키는 fall-back 보장</li>
 * </ul>
 * <p>
 * <b>라이프사이클</b>:
 * <ol>
 *   <li>{@code new WebSocketStateChart(context)} — listener가 함께 생성된다.</li>
 *   <li>(선택) {@link #setPingInterval} / {@link #setPongTimeout} 호출 — 연결 시작 전</li>
 *   <li>차트 시작 후 {@code OpenWebSocket} 같은 상태가 연결을 시도하고 결과로
 *       {@link #setWebSocket(WebSocket)}을 호출한다.</li>
 *   <li>차트 실행 중에 {@code sendText}/{@code sendBinary}로 송신 가능.</li>
 *   <li>종료 신호 도착 시 fall-back이 차트를 종료시킨다.</li>
 * </ol>
 *
 * @param <C>	{@link WebSocketContext} 서브타입의 self-bound 타입 파라미터
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketStateChart<C extends WebSocketContext<C>> extends StateChart<C> {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketStateChart.class);

	private final WebSocketListener<C> m_socketListener;
	@Nullable private volatile WebSocket m_webSocket;

	/**
	 * 주어진 컨텍스트로 WebSocket 상태 차트를 생성한다.
	 * <p>
	 * 생성과 동시에 내부 {@link WebSocketListener}가 초기화되어
	 * {@link #getWebSocketListener()}로 접근 가능하다. 실제 WebSocket 연결은 차트 시작 후
	 * 사용자가 정의한 상태(예: {@code OpenWebSocket})에서 이루어진다.
	 *
	 * @param context	WebSocket 도메인 컨텍스트
	 */
	public WebSocketStateChart(C context) {
		super(context);

		setLogger(s_logger);
		m_socketListener = new WebSocketListener<>(this);
	}

	/**
	 * 현재 활성 WebSocket 연결을 반환한다.
	 *
	 * @return	연결된 {@link WebSocket}. {@link #setWebSocket(WebSocket)} 호출 전이거나
	 *			연결이 close 된 경우 {@code null}.
	 */
	public @Nullable WebSocket getWebSocket() {
		return m_webSocket;
	}

	/**
	 * 활성 WebSocket 연결을 등록한다.
	 * <p>
	 * 본 메소드는 일반적으로 {@code OpenWebSocket} 같은 상태가 비동기 연결 결과를 받았을 때
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
		Utilities.checkNotNullArgument(ws, "ws is null");
		Utilities.checkState(m_webSocket == null, "WebSocket is already set on this chart");

		m_webSocket = ws;
	}

	/**
	 * 본 차트가 보유한 {@link WebSocketListener}를 반환한다.
	 * <p>
	 * {@code OpenWebSocket} 같은 상태가 {@code HttpClient.newWebSocketBuilder().buildAsync(uri, listener)}
	 * 호출 시 인자로 사용한다.
	 *
	 * @return	WebSocket listener
	 */
	public WebSocketListener<C> getWebSocketListener() {
		return m_socketListener;
	}

	/**
	 * Ping 발송 주기를 설정한다.
	 * <p>
	 * {@link #setPongTimeout(Duration)}와 함께 호출되어야 keep-alive가 활성화된다.
	 * 본 메소드는 listener가 사용되기 전 (= WebSocket 연결 시작 전) 에 호출되어야 효과가 있다.
	 *
	 * @param interval	ping 발송 주기 (양수)
	 * @throws IllegalArgumentException	{@code interval}이 {@code null}이거나 양수가 아닌 경우
	 */
	public void setPingInterval(Duration interval) {
		Utilities.checkNotNullArgument(interval, "interval is null");
		Utilities.checkArgument(DurationUtils.isPositive(interval),
								"interval must be positive: %s", interval);

		m_socketListener.setPingInterval(interval);
	}

	/**
	 * Pong 응답 대기 timeout을 설정한다.
	 * <p>
	 * {@link #setPingInterval(Duration)}와 함께 호출되어야 keep-alive가 활성화된다.
	 * timeout 안에 pong이 도착하지 않으면 차트는 {@link Signals.ErrorMessage}로
	 * 실패 상태에 진입한다. 본 메소드는 listener가 사용되기 전에 호출되어야 한다.
	 *
	 * @param timeout	pong 응답 timeout (양수)
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 양수가 아닌 경우
	 */
	public void setPongTimeout(Duration timeout) {
		Utilities.checkNotNullArgument(timeout, "timeout is null");
		Utilities.checkArgument(DurationUtils.isPositive(timeout),
								"timeout must be positive: %s", timeout);

		m_socketListener.setPongTimeout(timeout);
	}

	/**
	 * 텍스트 메시지를 비동기로 송신한다.
	 * <p>
	 * 차트가 실행 중이고 WebSocket 연결이 활성 상태일 때만 호출 가능하다.
	 *
	 * @param text	송신할 텍스트 (non-null)
	 * @param last	이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @return		송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @throws IllegalArgumentException	{@code text}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 실행 중이 아니거나 WebSocket이 연결되지 않은 경우
	 */
	public CompletableFuture<WebSocket> sendText(String text, boolean last) {
		Utilities.checkNotNullArgument(text, "text is null");
		Utilities.checkState(isRunning(), "WebSocketStateMachine is not running");
		Utilities.checkState(m_webSocket != null, "WebSocket is not connected");

		getLogger().debug("sending text: {}", text);
		return m_webSocket.sendText(text, last);
	}

	/**
	 * 텍스트 메시지를 송신하고 완료될 때까지 블록한다.
	 * <p>
	 * 내부적으로 {@link #sendText(String, boolean)}의 결과 future를 {@code get()}으로 대기한다.
	 * 송신 중 예외가 발생하면 원인을 unwrap한 {@link RuntimeExecutionException}으로 전파된다.
	 * 대기 중 쓰레드가 인터럽트되면 {@link InterruptedException}이 호출자에게 전파된다.
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
	 * 바이너리 메시지를 비동기로 송신한다.
	 * <p>
	 * 차트가 실행 중이고 WebSocket 연결이 활성 상태일 때만 호출 가능하다.
	 *
	 * @param binary	송신할 바이트 배열 (non-null)
	 * @param last		이 메시지가 fragment 시퀀스의 마지막인지 여부
	 * @return			송신 완료 시 WebSocket을 결과로 갖는 {@link CompletableFuture}
	 * @throws IllegalArgumentException	{@code binary}가 {@code null}인 경우
	 * @throws IllegalStateException	차트가 실행 중이 아니거나 WebSocket이 연결되지 않은 경우
	 */
	public CompletableFuture<WebSocket> sendBinary(byte[] binary, boolean last) {
		Utilities.checkNotNullArgument(binary, "binary is null");
		Utilities.checkState(isRunning(), "WebSocketStateMachine is not running");
		Utilities.checkState(m_webSocket != null, "WebSocket is not connected");

		getLogger().debug("sending binary: len={}", binary.length);
		return m_webSocket.sendBinary(ByteBuffer.wrap(binary), last);
	}

	/**
	 * 바이너리 메시지를 송신하고 완료될 때까지 블록한다.
	 * <p>
	 * 내부적으로 {@link #sendBinary(byte[], boolean)}의 결과 future를 {@code get()}으로 대기한다.
	 * 송신 중 예외가 발생하면 원인을 unwrap한 {@link RuntimeExecutionException}으로 전파된다.
	 * 대기 중 쓰레드가 인터럽트되면 {@link InterruptedException}이 호출자에게 전파된다.
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
	 * 신호를 처리한다.
	 * <p>
	 * 부모 클래스의 {@link StateChart#handleSignal(Signal)}을 우선 호출한 뒤, 등록된 transition이
	 * 없고 차트가 여전히 실행 중인 경우에 한해 다음 fall-back을 수행한다:
	 * <ul>
	 *   <li>{@link Signals.ErrorMessage} → {@link #fail(Throwable)}을 호출하여 차트를 실패 상태로 전이</li>
	 *   <li>{@link Signals.ConnectionClosed} → {@code cancel(true)}로 차트를 취소</li>
	 * </ul>
	 * 이 fall-back은 사용자가 ErrorMessage / ConnectionClosed 처리 transition을 정의하지 않았더라도
	 * WebSocket이 비정상 종료되었을 때 차트가 dangling 상태에 머무르지 않도록 보장한다.
	 *
	 * @param signal	처리할 신호
	 * @return			처리에 사용된 transition. fall-back 경로에서는 {@link Optional#empty()}.
	 * @throws IllegalArgumentException	{@code signal}이 {@code null}인 경우
	 */
	@Override
	public Optional<Transition<C>> handleSignal(Signal signal) {
		Utilities.checkNotNullArgument(signal, "signal is null");

		Optional<Transition<C>> otrans = super.handleSignal(signal);
		if ( otrans.isEmpty() && isRunning() ) {
			// WebSocket Listener에서 onClose, onError 이벤트가 발생되었지만,
			// 해당 이벤트가 처리되지 않은 경우에 대비하여 fall-back 처리를 수행함.
			// 	onError: 상태머신을 실패 상태로 전이시킴.
			// 	onClose: 상태머신을 취소 상태로 전이시킴.
			if ( signal instanceof ErrorMessage errMsg ) {	// onError 메시지가 도착한 경우
				fail(errMsg.getError());
			}
			else if ( signal instanceof ConnectionClosed ) {	// onClose 메시지가 도착한 경우
				cancel(true);
			}
		}
		return otrans;
	}
}
