package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.RuntimeTimeoutException;
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
 *   <li>{@link #onPing} → 로그만 남긴다. pong 회신은 JDK가 자동 수행 (신호 변환 없음)</li>
 *   <li>{@link #onPong} → pong timeout 태스크 취소 (신호 변환 없음)</li>
 * </ul>
 * <p>
 * <b>Keep-alive</b>: {@link #setPingInterval(Duration)}와 {@link #setPongTimeout(Duration)}이
 * 모두 설정된 경우 {@link #onOpen} 시점에 ping 타이머가 시작된다. 각 ping 발송 후
 * pong timeout 안에 응답이 없으면 {@link ErrorMessage}({@link RuntimeTimeoutException} 포함)를
 * 상태머신에 발송한다.
 * <p>
 * 감시는 <b>ping 단위</b>로 이루어진다. ping마다 자신의 pong을 감시하는 태스크가 예약되고,
 * 그 pong이 도착하면(또는 상태머신이 종료되면) 취소된다. 따라서 두 설정값의 대소 관계와 무관하게
 * 동작하며, {@code pongTimeout}이 {@code pingInterval}보다 길어도 감지가 누락되지 않는다.
 * 다만 감지까지 걸리는 시간은 최대 {@code pingInterval + pongTimeout}이 된다.
 * <p>
 * <b>라이프사이클</b>: ping 타이머는 상태머신 종료 시({@code whenFinished} 콜백) 또는 pong timeout이
 * 처음 발동했을 때 정리되며, 한 번 정리되면 <b>재개되지 않는다</b>. 타이머는 {@link #onOpen}에서만
 * 시작되고 본 클래스는 재연결을 지원하지 않기 때문이다. 따라서 {@link ErrorMessage}를 받아
 * 차트를 계속 실행시키는 transition을 정의한 경우, 그 이후로는 keep-alive 감시가 없다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
class WebSocketListener<C extends WebSocketContext<C>> implements WebSocket.Listener {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketListener.class);

	private static final byte[] PING_PAYLOAD = { 1, 2, 3, 4 };
	/** ping 타이머 스레드 이름을 구분하기 위한 일련번호. 스레드 덤프에서 차트별 식별에 쓰인다. */
	private static final AtomicInteger PING_TIMER_SEQNO = new AtomicInteger();

	private final WebSocketStateChart<C> m_stateMachine;

	private Duration m_pingInterval;
	private Duration m_pongTimeout;
	private volatile Timer m_pingTimer;
	/** 가장 최근에 보낸 ping의 발송 시각(ms). pong 왕복 시간 계산에 쓰인다. 보낸 적이 없으면 0. */
	private final AtomicLong m_lastPingSentTime = new AtomicLong(0);
	private final AtomicLong m_lastPongTime = new AtomicLong(0);
	/** 가장 최근에 보낸 ping의 pong 응답을 감시하는 태스크. pong이 도착하면 취소된다. */
	private volatile TimerTask m_pendingPongTimeout;

	private StringBuilder m_textMessageBuilder = new StringBuilder();

	WebSocketListener(WebSocketStateChart<C> machine) {
		Preconditions.checkNotNullArgument(machine, "machine is null");

		m_stateMachine = machine;

		// 상태머신 종료 시 ping 타이머 자동 정리. 콜백은 생성자에서 한 번만 등록한다.
		m_stateMachine.whenFinished(result -> stopPing());
	}

	/**
	 * Ping 발송 주기를 설정한다.
	 * <p>
	 * {@link #setPongTimeout(Duration)}와 함께 설정되어야 keep-alive가 활성화된다.
	 * ping 타이머는 {@link #onOpen} 시점에 한 번 시작되므로, 그 이후에 호출하면 반영되지 않는다.
	 * <p>
	 * 본 메소드는 {@link WebSocketStateChart#setPingInterval(Duration)}이 위임하는 내부용이며,
	 * 그쪽에서 이미 {@code null}과 비양수를 거부한다. 따라서 {@code null}로 ping을 끄는 경로는
	 * 공개 API로는 도달할 수 없다.
	 *
	 * @param interval	ping 발송 주기. {@code null}이면 ping이 시작되지 않는다.
	 */
	public void setPingInterval(Duration interval) {
		m_pingInterval = interval;
	}

	/**
	 * Pong 응답 대기 timeout을 설정한다.
	 * <p>
	 * {@link #setPingInterval(Duration)}와 함께 설정되어야 keep-alive가 활성화된다.
	 * timeout 안에 pong이 도착하지 않으면 상태머신에 {@link ErrorMessage}가 발송되고
	 * ping이 중단된다. {@link #setPingInterval(Duration)}와 동일하게 {@link #onOpen} 이전에
	 * 설정되어야 하며, {@code null} 거부도 {@link WebSocketStateChart} 쪽에서 이루어진다.
	 *
	 * @param timeout	pong 응답 timeout. {@code null}이면 ping이 시작되지 않는다.
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
		// 방어적 정리. 본 메소드는 onOpen에서 한 번만 호출되므로 정상 경로에서는 해당되지 않는다.
		stopPing();

		long startedAt = System.currentTimeMillis();
		m_lastPongTime.set(startedAt);

		final Timer timer = new Timer("WebSocket-Ping-Timer-" + PING_TIMER_SEQNO.incrementAndGet(), true);
		m_pingTimer = timer;
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				sendPingAndSetPongTimeout(webSocket, timer);
			}
		}, m_pingInterval.toMillis(), m_pingInterval.toMillis());
	}

	/**
	 * ping 타이머와 대기 중인 pong 감시 태스크를 정리한다.
	 * <p>
	 * 상태머신 종료 시, 그리고 pong timeout이 발동했을 때 호출된다. 여러 번 호출해도 안전하다.
	 * 정리 후에는 ping이 재개되지 않는다 — {@link #startPing(WebSocket)}은 {@link #onOpen}에서만
	 * 호출되고 본 클래스는 재연결을 지원하지 않기 때문이다.
	 */
	private void stopPing() {
		Timer timer = m_pingTimer;
		if ( timer != null ) {
			timer.cancel();
			m_pingTimer = null;
		}
		m_pendingPongTimeout = null;
	}

	private void sendPingAndSetPongTimeout(WebSocket webSocket, Timer timer) {
		// 여기서 직전 pong timeout 태스크를 취소해서는 안 된다. pongTimeout이 pingInterval보다
		// 길면 예약된 감시 태스크가 매번 다음 ping에 의해 발동 전에 취소되어, pong이 끊겨도
		// 영원히 감지되지 않는다. 취소는 pong이 실제로 도착한 경우(onPong)에만 수행한다.
		// ping마다 자신만의 감시 태스크를 예약하며, 뒤늦게 pong이 도착한 경우는 태스크 안의
		// m_lastPongTime 검사가 걸러낸다.
		final long pingSentAt = System.currentTimeMillis();
		m_lastPingSentTime.set(pingSentAt);

		s_logger.debug("sending ping");
		webSocket.sendPing(ByteBuffer.wrap(PING_PAYLOAD));

		TimerTask timeoutTask = new TimerTask() {
			@Override
			public void run() {
				// 이 ping 이후에 pong이 도착했으면 정상이므로 무시한다.
				// (onPong의 cancel()과 경쟁하여 실행된 경우도 여기서 걸러진다)
				if ( m_lastPongTime.get() >= pingSentAt ) {
					return;
				}
				s_logger.info("pong timeout: lastPingSent={}, lastPongReceived={}",
								pingSentAt, m_lastPongTime.get());
				// 신호를 보내기 전에 ping을 멈춘다. 순서가 반대이면, 신호를 처리한 transition이
				// 차트를 종료시키면서 whenFinished 훅으로 이미 정리한 타이머를 다시 건드리게 된다.
				// 감시는 여기서 끝난다 — 상태머신이 계속 실행되더라도 ping은 재개되지 않는다.
				stopPing();
				m_stateMachine.handleSignal(new ErrorMessage(webSocket,
						new RuntimeTimeoutException(new TimeoutException("pong timeout"))));
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

	/**
	 * Ping 메시지 수신을 로그로 남긴다.
	 * <p>
	 * <b>pong을 직접 보내지 않는다.</b> JDK WebSocket 구현이 ping 수신 시 자동으로 pong을
	 * 회신하기 때문에({@link WebSocket.Listener#onPing} 참조), 여기서 {@code sendPong}을 호출하면
	 * pong이 중복 전송된다. 또한 {@code message} 버퍼는 본 메소드가 반환한
	 * {@link CompletionStage}가 완료되는 즉시 JDK가 회수할 수 있으므로, 비동기인
	 * {@code sendPong}에 그대로 넘기면서 {@code null}을 반환하면 회수된 버퍼를 읽게 된다.
	 *
	 * @param webSocket	ping을 수신한 WebSocket
	 * @param message	ping payload. 본 메소드 반환 이후에는 접근할 수 없다.
	 * @return	버퍼를 즉시 회수해도 좋음을 뜻하는 {@code null}.
	 */
	@Override
	public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
		s_logger.debug("received PING: {}", message);

		webSocket.request(1);
		return null;
    }

	/**
	 * Pong 응답 수신을 기록하고, 직전 ping으로부터의 왕복 시간을 로그로 남긴다.
	 * <p>
	 * 왕복 시간은 <b>가장 최근에 보낸 ping</b>을 기준으로 계산한다. 따라서 응답을 기다리는 ping이
	 * 여럿인 경우(= {@code pongTimeout}이 {@code pingInterval}보다 긴 경우) 실제보다 짧게 측정될 수
	 * 있다. 정확한 짝맞춤이 필요하면 ping payload에 식별자를 실어 대조해야 한다.
	 *
	 * @param webSocket	pong을 수신한 WebSocket
	 * @param message	pong payload
	 * @return	버퍼를 즉시 회수해도 좋음을 뜻하는 {@code null}.
	 */
	@Override
	public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
		long now = System.currentTimeMillis();
		long pingSentAt = m_lastPingSentTime.get();
		if ( pingSentAt > 0 ) {
			s_logger.warn("received PONG: elapsed={}ms", now - pingSentAt);
		}
		else {
			// 우리가 보낸 ping에 대한 응답이 아닌 경우(상대가 먼저 보낸 unsolicited pong).
			s_logger.debug("received unsolicited PONG");
		}

		m_lastPongTime.set(now);

		// 응답이 왔으므로 이 ping에 대한 감시 태스크는 더 이상 필요 없다.
		// 취소하지 않아도 위의 m_lastPongTime 검사가 오동작을 막지만, 불필요한 태스크를 남기지 않는다.
		TimerTask pending = m_pendingPongTimeout;
		if ( pending != null ) {
			pending.cancel();
			m_pendingPongTimeout = null;
		}

		webSocket.request(1);
		return null;
	}

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
		// throwable을 마지막 인자로 넘겨야 스택 트레이스가 남는다.
		// 포맷 자리표시자에 대응시키면 toString()만 찍히고 원인 추적이 불가능해진다.
		s_logger.warn("WebSocket error", error);

		// 오류 시점 이후로 이 연결에서 더 이상 메시지가 오지 않으므로, 조립 중이던
		// 부분 텍스트 메시지는 버린다.
		m_textMessageBuilder.setLength(0);

		m_stateMachine.handleSignal(new ErrorMessage(webSocket, error));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		s_logger.info("closed: statusCode={}, reason={}", statusCode, reason);

		// 조립 중이던 부분 텍스트 메시지는 완성될 수 없으므로 버린다.
		m_textMessageBuilder.setLength(0);

		m_stateMachine.handleSignal(new ConnectionClosed(webSocket, statusCode, reason));

		return null;
    }
}
