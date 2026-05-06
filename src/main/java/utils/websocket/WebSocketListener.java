package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.RuntimeTimeoutException;
import utils.Utilities;
import utils.websocket.Signals.BinaryMessage;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.ErrorMessage;
import utils.websocket.Signals.TextMessage;


/**
 * WebSocket 이벤트를 도메인 신호로 변환하여 {@link WebSocketStateChart}에 전달하는
 * {@link WebSocket.Listener} 구현.
 * <p>
 * 콜백 ↔ 신호 매핑:
 * <ul>
 *   <li>{@link #onText} → {@link TextMessage} (fragment 누적 후 {@code last=true} 시 발송)</li>
 *   <li>{@link #onBinary} → {@link BinaryMessage}</li>
 *   <li>{@link #onError} → {@link ErrorMessage}</li>
 *   <li>{@link #onClose} → {@link ConnectionClosed}</li>
 *   <li>{@link #onPing} → 자동 pong 응답 (신호 변환 없음)</li>
 *   <li>{@link #onPong} → pong timeout 태스크 취소 (신호 변환 없음)</li>
 * </ul>
 * <p>
 * <b>Keep-alive</b>: {@link #setPingInterval(Duration)}와 {@link #setPongTimeout(Duration)}이
 * 모두 설정된 경우 {@link #onOpen} 시점에 ping 타이머가 시작된다. 각 ping 발송 후
 * pong timeout 안에 응답이 없으면 {@link ErrorMessage}({@link RuntimeTimeoutException} 포함)를
 * 상태머신에 발송한다.
 * <p>
 * <b>라이프사이클</b>: ping 타이머는 상태머신 종료 시 ({@code whenFinished} 콜백) 자동으로
 * 취소된다. 콜백은 생성자에서 한 번만 등록되어 재연결 시 누적되지 않는다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
class WebSocketListener<C extends WebSocketContext<C>> implements WebSocket.Listener {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketListener.class);

	private static final byte[] PING_PAYLOAD = { 1, 2, 3, 4 };

	private final WebSocketStateChart<C> m_stateMachine;

	private Duration m_pingInterval;
	private Duration m_pongTimeout;
	private volatile Timer m_pingTimer;
	private final AtomicLong m_lastPongTime = new AtomicLong(0);
	private volatile TimerTask m_pendingPongTimeout;

	private StringBuilder m_textMessageBuilder = new StringBuilder();

	WebSocketListener(WebSocketStateChart<C> machine) {
		Utilities.checkNotNullArgument(machine, "machine is null");

		m_stateMachine = machine;

		// 상태머신 종료 시 ping 타이머 자동 정리. 콜백은 생성자에서 한 번만 등록되어
		// 재연결로 인한 누적을 방지한다.
		m_stateMachine.whenFinished(result -> {
			Timer timer = m_pingTimer;
			if ( timer != null ) {
				timer.cancel();
				m_pingTimer = null;
			}
		});
	}

	/**
	 * Ping 발송 주기를 설정한다. {@link #setPongTimeout(Duration)}와 함께 설정되어야
	 * {@link #onOpen} 시점에 ping 타이머가 시작된다.
	 *
	 * @param interval	ping 발송 주기. {@code null}이면 ping 비활성화.
	 */
	public void setPingInterval(Duration interval) {
		m_pingInterval = interval;
	}

	/**
	 * Pong 응답 대기 timeout을 설정한다. {@link #setPingInterval(Duration)}와 함께 설정되어야
	 * keep-alive가 활성화된다. timeout 안에 pong이 도착하지 않으면 상태머신에
	 * {@link ErrorMessage}가 발송된다.
	 *
	 * @param timeout	pong 응답 timeout. {@code null}이면 ping 비활성화.
	 */
	public void setPongTimeout(Duration timeout) {
		m_pongTimeout = timeout;
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		s_logger.info("connected: {}", webSocket.getSubprotocol());

		webSocket.request(1);
		if ( m_pingInterval != null && m_pongTimeout != null ) {
			s_logger.info("starting ping: interval={}, timeout={}", m_pingInterval, m_pongTimeout);
			startPing(webSocket);
		}
	}

	private void startPing(WebSocket webSocket) {
		// 기존 타이머가 존재하면 취소 (재연결 시 기존 타이머가 존재할 수 있음)
		if ( m_pingTimer != null ) {
			m_pingTimer.cancel();
			m_pingTimer = null;
		}

		long startedAt = System.currentTimeMillis();
		m_lastPongTime.set(startedAt);

		final Timer timer = new Timer("WebSocket-Ping-Timer", true);
		m_pingTimer = timer;
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				sendPingAndArmPongTimeout(webSocket, timer);
			}
		}, m_pingInterval.toMillis(), m_pingInterval.toMillis());
	}

	private void sendPingAndArmPongTimeout(WebSocket webSocket, Timer timer) {
		// 직전 ping에 대해 아직 대기 중인 pong timeout 태스크가 있으면 취소
		TimerTask prev = m_pendingPongTimeout;
		if ( prev != null ) {
			prev.cancel();
		}

		final long pingSentAt = System.currentTimeMillis();

		s_logger.debug("sending ping");
		webSocket.sendPing(ByteBuffer.wrap(PING_PAYLOAD));

		TimerTask timeoutTask = new TimerTask() {
			@Override
			public void run() {
				// pong이 이 ping 이후 도착했다면 cancel()과의 경쟁으로 실행된 것이므로 무시
				if ( m_lastPongTime.get() >= pingSentAt ) {
					return;
				}
				s_logger.warn("pong timeout: lastPingSent={}, lastPongReceived={}",
								pingSentAt, m_lastPongTime.get());
				m_stateMachine.handleSignal(new ErrorMessage(webSocket,
						new RuntimeTimeoutException(new TimeoutException("pong timeout"))));
				timer.cancel();
			}
		};
		m_pendingPongTimeout = timeoutTask;
		try {
			timer.schedule(timeoutTask, m_pongTimeout.toMillis());
		}
		catch ( IllegalStateException ignored ) {
			// 타이머가 이미 취소된 경우(상태머신 종료 등) — 무시
		}
	}

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();

    	m_textMessageBuilder.append(message);
        if ( last ) {
			message = m_textMessageBuilder.toString();
			m_textMessageBuilder.setLength(0);

	        m_stateMachine.handleSignal(new TextMessage(webSocket, message));
        }
		webSocket.request(1);

		return null;
    }

	@Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		m_stateMachine.handleSignal(new BinaryMessage(webSocket, bytes, last));

		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
		s_logger.debug("received PING: {}", message);

		webSocket.sendPong(message);
		webSocket.request(1);
		return null;
    }

	@Override
	public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
		s_logger.debug("received PONG: {}", message);

		m_lastPongTime.set(System.currentTimeMillis());

		TimerTask pending = m_pendingPongTimeout;
		if ( pending != null ) {
			pending.cancel();
		}

		webSocket.request(1);
		return null;
	}

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
		s_logger.info("onError: {}", error);

		m_stateMachine.handleSignal(new ErrorMessage(webSocket, error));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		s_logger.info("closed: statusCode={}, reason={}", statusCode, reason);

		m_stateMachine.handleSignal(new ConnectionClosed(webSocket, statusCode, reason));

		return null;
    }
}
