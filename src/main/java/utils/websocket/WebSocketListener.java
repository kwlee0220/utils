package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.RuntimeTimeoutException;
import utils.websocket.Signals.BinaryMessage;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.TextMessage;
import utils.websocket.Signals.WebSocketError;


/**
 * WebSocket 이벤트를 도메인 신호로 변환하여 {@link WebSocketStateChart}에 전달하는
 * {@link WebSocket.Listener} 구현.
 * <p>
 * 콜백 ↔ 신호 매핑:
 * <ul>
 *   <li>{@link #onText} → {@link TextMessage} (fragment 누적 후 {@code last=true} 시 발송). 누적 길이가
 *       {@link WebSocketStateChart#getMaxTextMessageLength()}를 넘으면 {@link WebSocketError}</li>
 *   <li>{@link #onBinary} → {@link BinaryMessage} (fragment마다 하나씩, 누적하지 않음)</li>
 *   <li>{@link #onError} → {@link WebSocketError}</li>
 *   <li>{@link #onClose} → {@link ConnectionClosed}</li>
 *   <li>{@link #onPing} → 로그만 남긴다. pong 회신은 JDK가 자동 수행 (신호 변환 없음)</li>
 *   <li>{@link #onPong} → pong timeout 태스크 취소 (신호 변환 없음)</li>
 * </ul>
 * <p>
 * <b>수신 시작</b>: {@link #onOpen}은 수신을 요청하지 않는다. 접속 상태가 연결을 차트에 등록하고
 * {@link Signals.Connected}의 처리를 마친 뒤 {@link #startReceiving(WebSocket)}을 호출해야 첫 수신을 요청한다.
 * {@code onOpen}에서 바로 요청하면 서버가 접속 직후 보낸 메시지가 {@code Connected}보다 먼저 차트에 도착해
 * 접속 상태에서 버려지기 때문이다(2026-10-31 이전 동작). 또 {@code onOpen}은 차트에 등록되지 않을 연결
 * (차트가 끝난 뒤나 접속 상태를 떠난 뒤 완료된 연결)에서도 호출되므로, ping 예약도 {@code startReceiving}에서 시작한다.
 * <p>
 * 차트에 등록된 연결이 있으면 다른 연결에서 온 콜백은 무시한다. 접속 상태는 본 listener 대신 접속 시도마다
 * {@link #forAttempt(BooleanSupplier)}가 만든 listener를 쓰며, 이 listener는 등록 전이라도 폐기된 시도의 콜백을 버린다.
 * <p>
 * <b>Keep-alive</b>: {@link #setPingInterval(Duration)}와 {@link #setPongTimeout(Duration)}이
 * 모두 설정된 경우 {@link #startReceiving(WebSocket)} 시점에 ping 예약 작업이 시작된다. 각 ping 발송 후
 * pong timeout 안에 응답이 없으면 {@link WebSocketError}({@link RuntimeTimeoutException} 포함)를
 * 상태 차트에 발송한다.
 * <p>
 * 감시는 <b>ping 단위</b>로 이루어진다. ping마다 자신의 pong을 감시하는 태스크가 예약되고,
 * 그 pong이 도착하면(또는 상태 차트가 종료되면) 취소된다. 따라서 두 설정값의 대소 관계와 무관하게
 * 동작하며, {@code pongTimeout}이 {@code pingInterval}보다 길어도 감지가 누락되지 않는다.
 * 다만 감지까지 걸리는 시간은 최대 {@code pingInterval + pongTimeout}이 된다.
 * <p>
 * <b>라이프사이클</b>: ping 예약 작업은 상태 차트 종료 시({@code whenFinished} 콜백) 또는 pong timeout이
 * 처음 발동했을 때 정리되며, 한 번 정리되면 <b>재개되지 않는다</b>. 예약은 {@link #startReceiving}에서만
 * 시작되고 본 클래스는 재연결을 지원하지 않기 때문이다. 따라서 {@link WebSocketError}를 받아
 * 차트를 계속 실행시키는 transition을 정의한 경우, 그 이후로는 keep-alive 감시가 없다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
class WebSocketListener<C extends WebSocketContext<C>> implements WebSocket.Listener {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketListener.class);

	private static final byte[] PING_PAYLOAD = { 1, 2, 3, 4 };

	private final WebSocketStateChart<C> m_chart;

	// 설정 스레드와 접속 결과 처리 스레드가 다르므로 volatile로 둔다.
	private volatile Duration m_pingInterval;
	private volatile Duration m_pongTimeout;
	/**
	 * 주기적인 ping 송신 작업. 패키지가 함께 쓰는 스케줄러({@link SharedResources#scheduler()})에 예약한다.
	 * (2026-10-31 이전에는 차트마다 {@code java.util.Timer} 스레드를 하나씩 만들었다.)
	 */
	private volatile ScheduledFuture<?> m_pingTask;
	/**
	 * ping 감시 세대. {@link #startPing}과 {@link #stopPing}마다 증가한다. 예약된 ping·pong 감시 작업은 자기 세대가
	 * 현재 값과 다르면(= 이미 멈춘 감시에 속하면) 아무 일도 하지 않는다. 공유 스케줄러에서는 이 listener의 작업만
	 * 골라 한꺼번에 취소할 수 없기 때문이다.
	 */
	private final AtomicLong m_pingGeneration = new AtomicLong();
	/** 가장 최근에 보낸 ping의 발송 시각(ms). pong 왕복 시간 계산에 쓰인다. 보낸 적이 없으면 0. */
	private final AtomicLong m_lastPingSentTime = new AtomicLong(0);
	private final AtomicLong m_lastPongTime = new AtomicLong(0);
	/**
	 * 가장 최근에 보낸 ping의 pong 응답을 감시하는 태스크. pong이 도착하면 취소된다. 앞서 보낸 ping들의 감시
	 * 태스크는 따로 남아 있을 수 있다(ping마다 하나씩 예약하므로).
	 */
	private volatile ScheduledFuture<?> m_latestPongTimeoutTask;
	/**
	 * 등록된 연결에서 상대의 close 프레임을 받았거나 연결 오류가 나면 완료된다. close 절차가 상대의 응답을 기다릴 때 쓴다.
	 * 차트에 등록된 연결의 콜백만 완료시킨다(등록 전 연결이나 다른 연결의 오류로 완료되면, 나중에 등록된 연결을 닫을 때
	 * 응답을 기다리지 않고 바로 끊게 된다).
	 */
	private final CompletableFuture<Void> m_peerClosed = new CompletableFuture<>();

	private StringBuilder m_textMessageBuilder = new StringBuilder();
	/**
	 * 길이 상한을 넘은 텍스트 메시지의 남은 조각을 버리는 중인지 여부. JDK는 수신 콜백을 차례로 하나씩
	 * 호출하므로(이전 호출이 끝난 뒤 다음 호출) {@link #m_textMessageBuilder}와 함께 동기화 없이 쓴다.
	 */
	private boolean m_discardingText = false;

	WebSocketListener(WebSocketStateChart<C> chart) {
		Preconditions.checkNotNullArgument(chart, "chart is null");

		m_chart = chart;

		// 상태 차트 종료 시 ping 예약 작업 자동 정리. 콜백은 생성자에서 한 번만 등록한다.
		m_chart.whenFinished(result -> stopPing());
	}

	/**
	 * 등록된 연결에서 상대의 close 프레임을 받았거나 연결 오류가 나면 완료되는 것을 반환한다.
	 *
	 * @return	상대 종료 통지
	 */
	CompletionStage<Void> peerClosed() {
		return m_peerClosed;
	}

	/**
	 * 주어진 연결이 차트에 등록된 연결이 아닌지 여부. 등록된 연결이 아직 없으면 {@code false}다.
	 * 차트에 등록하지 않고 닫는 연결(늦게 완료된 연결, 재연결 시도로 생긴 연결)의 콜백을 걸러낸다.
	 */
	private boolean isForeign(WebSocket webSocket) {
		if ( webSocket == m_abandoned ) {
			return true;
		}
		WebSocket registered = m_chart.getWebSocket();
		return registered != null && registered != webSocket;
	}

	/**
	 * 차트에 등록되었지만 더 이상 쓰지 않는 연결. 이 연결의 콜백은 모두 버린다. 등록 직후 접속 상태를 떠난 경우에
	 * 접속 상태가 지정한다({@link #abandon(WebSocket)}).
	 */
	private volatile WebSocket m_abandoned;

	/**
	 * 등록된 연결을 버린다. 이후 이 연결의 콜백은 차트에 전달하지 않는다. 등록 직후 접속 상태를 떠나 연결을 닫을 때
	 * 쓴다. 닫으면서 오는 상대의 close 응답이 {@link ConnectionClosed}로 차트에 가서, 실행 중인 차트를 대체 종료로
	 * 취소하지 않게 하기 위해서다.
	 *
	 * @param webSocket	버릴 연결
	 */
	void abandon(WebSocket webSocket) {
		m_abandoned = webSocket;
	}

	/** 주어진 연결이 차트에 등록된 연결인지 여부. */
	private boolean isRegistered(WebSocket webSocket) {
		return m_chart.getWebSocket() == webSocket;
	}

	/**
	 * 접속 시도 하나에 쓸 listener를 반환한다. 접속 상태가 {@code buildAsync()}에 넘긴다.
	 * <p>
	 * 반환된 listener는 다음 경우에만 콜백을 본 listener에 넘기고, 그 밖의 콜백은 버린다.
	 * <ul>
	 *   <li>그 연결이 차트에 등록된 연결인 경우.</li>
	 *   <li>아직 차트에 등록된 연결이 없고 {@code attemptValid}가 {@code true}인 경우. 현재 접속 시도의 등록 전
	 *       콜백(예: 접속 직후의 오류)은 차트에 전달한다.</li>
	 * </ul>
	 * 접속 상태를 떠나 폐기된 시도의 연결에서 등록 전에 오류가 나도 차트를 실패시키지 않게 하기 위해서다. 등록된 연결
	 * 유무만 보는 {@link #isForeign(WebSocket)}로는 등록 전의 현재 시도와 폐기된 시도를 구분할 수 없다.
	 *
	 * @param attemptValid	접속 시도가 아직 유효한지(접속 상태를 떠나지 않았는지) 알려 주는 것
	 * @return	접속 시도용 listener
	 */
	WebSocket.Listener forAttempt(BooleanSupplier attemptValid) {
		return new AttemptListener(attemptValid);
	}

	private final class AttemptListener implements WebSocket.Listener {
		private final BooleanSupplier m_attemptValid;

		AttemptListener(BooleanSupplier attemptValid) {
			m_attemptValid = attemptValid;
		}

		private boolean accepts(WebSocket webSocket) {
			if ( webSocket == m_abandoned ) {
				return false;
			}
			WebSocket registered = m_chart.getWebSocket();
			return (registered != null) ? registered == webSocket : m_attemptValid.getAsBoolean();
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			WebSocketListener.this.onOpen(webSocket);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			return accepts(webSocket) ? WebSocketListener.this.onText(webSocket, data, last) : null;
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			return accepts(webSocket) ? WebSocketListener.this.onBinary(webSocket, data, last) : null;
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			return accepts(webSocket) ? WebSocketListener.this.onPing(webSocket, message) : null;
		}

		@Override
		public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
			return accepts(webSocket) ? WebSocketListener.this.onPong(webSocket, message) : null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			return accepts(webSocket) ? WebSocketListener.this.onClose(webSocket, statusCode, reason) : null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			if ( accepts(webSocket) ) {
				WebSocketListener.this.onError(webSocket, error);
			}
			else {
				s_logger.debug("ignored error on an abandoned connection attempt: {}", ""+error);
			}
		}
	}

	/**
	 * Ping 발송 주기를 설정한다.
	 * <p>
	 * {@link #setPongTimeout(Duration)}와 함께 설정되어야 keep-alive가 활성화된다.
	 * ping 예약 작업은 {@link #startReceiving} 시점에 한 번 시작되므로, 그 이후에 호출하면 반영되지 않는다.
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
	 * timeout 안에 pong이 도착하지 않으면 상태 차트에 {@link WebSocketError}가 발송되고
	 * ping이 중단된다. {@link #setPingInterval(Duration)}와 동일하게 {@link #startReceiving} 이전에
	 * 설정되어야 하며, {@code null} 거부도 {@link WebSocketStateChart} 쪽에서 이루어진다.
	 *
	 * @param timeout	pong 응답 timeout. {@code null}이면 ping이 시작되지 않는다.
	 */
	public void setPongTimeout(Duration timeout) {
		m_pongTimeout = timeout;
	}

	/**
	 * 접속이 열렸음을 로그로 남긴다. 수신 요청과 ping 시작은 하지 않는다({@link #startReceiving(WebSocket)} 참조).
	 */
	@Override
	public void onOpen(WebSocket webSocket) {
		s_logger.debug("WebSocket opened: subprotocol={}", webSocket.getSubprotocol());
	}

	/**
	 * 차트에 등록된 연결에서 수신을 시작한다. 첫 수신을 요청하고, keep-alive가 설정되어 있으면 ping 예약 작업을 시작한다.
	 * <p>
	 * 접속 상태가 연결을 등록하고 {@link Signals.Connected}의 처리를 마친 뒤에 한 번 호출한다. 그 전에 도착한
	 * 메시지는 JDK가 보관해 두었다가 이 요청 뒤에 전달하므로, 서버가 접속 직후 보낸 메시지도 {@code Connected}
	 * 다음에 차트에 도착한다.
	 *
	 * @param webSocket	차트에 등록된 연결
	 */
	void startReceiving(WebSocket webSocket) {
		webSocket.request(1);
		if ( m_pingInterval != null && m_pongTimeout != null ) {
			// 차트가 끝났으면 ping을 시작하지 않는다. 종료 훅(stopPing)이 이미 실행되어 새로 시작한 예약 작업을 멈출
			// 곳이 없기 때문이다. 시작 직후 차트가 끝난 경우도 다시 확인해 멈춘다
			// (종료 훅과 겹쳐도 stopPing()은 여러 번 불러도 안전하다).
			if ( m_chart.isDone() ) {
				s_logger.debug("chart already finished; not starting ping");
				return;
			}
			s_logger.info("starting ping: interval={}, timeout={}", m_pingInterval, m_pongTimeout);
			startPing(webSocket);
			if ( m_chart.isDone() ) {
				stopPing();
			}
		}
	}

	private void startPing(WebSocket webSocket) {
		// 방어적 정리. 본 메소드는 startReceiving에서 한 번만 호출되므로 정상 경로에서는 해당되지 않는다.
		stopPing();

		long generation = m_pingGeneration.incrementAndGet();
		m_lastPongTime.set(System.currentTimeMillis());

		long intervalMillis = m_pingInterval.toMillis();
		m_pingTask = SharedResources.scheduler().scheduleAtFixedRate(() -> {
			// 예외가 나면 이후 주기 실행이 모두 멈추므로 잡아서 로그만 남긴다.
			try {
				sendPingAndSetPongTimeout(webSocket, generation);
			}
			catch ( Throwable e ) {
				s_logger.warn("failed to send ping: {}", ""+e);
			}
		}, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * ping 예약 작업과 대기 중인 pong 감시 태스크를 정리한다.
	 * <p>
	 * 상태 차트 종료 시, 그리고 pong timeout이 발동했을 때 호출된다. 여러 번 호출해도 안전하다.
	 * 정리 후에는 ping이 재개되지 않는다 — {@link #startPing(WebSocket)}은 {@link #startReceiving}에서만
	 * 호출되고 본 클래스는 재연결을 지원하지 않기 때문이다.
	 */
	private void stopPing() {
		m_pingGeneration.incrementAndGet();
		ScheduledFuture<?> pingTask = m_pingTask;
		if ( pingTask != null ) {
			pingTask.cancel(false);
			m_pingTask = null;
		}
		// 앞서 보낸 ping들의 감시 작업은 남아 있을 수 있지만, 세대가 바뀌었으므로 실행돼도 아무 일도 하지 않는다.
		ScheduledFuture<?> timeoutTask = m_latestPongTimeoutTask;
		if ( timeoutTask != null ) {
			timeoutTask.cancel(false);
			m_latestPongTimeoutTask = null;
		}
	}

	private void sendPingAndSetPongTimeout(WebSocket webSocket, long generation) {
		if ( m_pingGeneration.get() != generation ) {
			return;
		}
		// 여기서 직전 pong timeout 태스크를 취소해서는 안 된다. pongTimeout이 pingInterval보다
		// 길면 예약된 감시 태스크가 매번 다음 ping에 의해 발동 전에 취소되어, pong이 끊겨도
		// 영원히 감지되지 않는다. 취소는 pong이 실제로 도착한 경우(onPong)에만 수행한다.
		// ping마다 자신만의 감시 태스크를 예약하며, 뒤늦게 pong이 도착한 경우는 태스크 안의
		// m_lastPongTime 검사가 걸러낸다.
		final long pingSentAt = System.currentTimeMillis();
		m_lastPingSentTime.set(pingSentAt);

		s_logger.debug("sending ping");
		webSocket.sendPing(ByteBuffer.wrap(PING_PAYLOAD));

		m_latestPongTimeoutTask = SharedResources.scheduler().schedule(
						() -> onPongTimeout(webSocket, generation, pingSentAt),
						m_pongTimeout.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void onPongTimeout(WebSocket webSocket, long generation, long pingSentAt) {
		// 이미 멈춘 감시이거나, 이 ping 이후에 pong이 도착했으면 정상이므로 무시한다.
		// (onPong의 cancel()과 경쟁하여 실행된 경우도 여기서 걸러진다)
		if ( m_pingGeneration.get() != generation || m_lastPongTime.get() >= pingSentAt ) {
			return;
		}
		s_logger.info("pong timeout: lastPingSent={}, lastPongReceived={}", pingSentAt, m_lastPongTime.get());
		// 신호를 보내기 전에 ping을 멈춘다. 감시는 여기서 끝난다 — 상태 차트가 계속 실행되더라도 ping은 재개되지 않는다.
		stopPing();
		// 신호 처리(사용자 상태 코드)는 공유 스케줄러 스레드를 막지 않도록 콜백 스레드 풀에서 한다.
		SharedResources.callbackExecutor().execute(() ->
			m_chart.handleSignal(new WebSocketError(webSocket,
									new RuntimeTimeoutException(new TimeoutException("pong timeout")))));
	}

	/**
	 * 텍스트 조각을 모아 마지막 조각이 오면 {@link TextMessage}로 전달한다.
	 * <p>
	 * 모은 길이가 {@link WebSocketStateChart#getMaxTextMessageLength()}를 넘으면 모은 내용을 버리고
	 * {@link WebSocketError}를 한 번 전달한 뒤, 그 메시지의 남은 조각(마지막 조각까지)을 버린다. 상대가 조각을
	 * 끝없이 보내도 메모리가 계속 늘지 않게 하기 위해서다.
	 */
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		if ( isForeign(webSocket) ) {
			return null;
		}
		if ( m_discardingText ) {
			// 길이 상한을 넘은 메시지의 남은 조각
			m_discardingText = !last;
		}
		else {
			int maxLength = m_chart.getMaxTextMessageLength();
			long length = (long)m_textMessageBuilder.length() + data.length();
			if ( length > maxLength ) {
				// 모은 내용을 버리고 버퍼의 메모리도 돌려준다.
				m_textMessageBuilder = new StringBuilder();
				m_discardingText = !last;

				s_logger.warn("text message exceeds the maximum length: max={}, discarding the message", maxLength);
				m_chart.handleSignal(new WebSocketError(webSocket, new IllegalStateException(
										String.format("text message exceeds the maximum length: %d", maxLength))));
			}
			else {
				m_textMessageBuilder.append(data);
				if ( last ) {
					String message = m_textMessageBuilder.toString();
					m_textMessageBuilder.setLength(0);

					m_chart.handleSignal(new TextMessage(webSocket, message));
				}
			}
		}
		webSocket.request(1);

		return null;
    }

	/**
	 * 바이너리 조각을 받을 때마다 {@link BinaryMessage}로 전달한다. 조각을 모으지 않으므로 누적 상한도 없다.
	 * 메시지 전체가 필요하면 상태가 {@link BinaryMessage#isLast()}까지 직접 합친다.
	 */
	@Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		if ( isForeign(webSocket) ) {
			return null;
		}
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		m_chart.handleSignal(new BinaryMessage(webSocket, bytes, last));

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
			// 정상 응답이므로 ping 주기마다 남는다. 운영 로그를 채우지 않도록 DEBUG로 남긴다.
			s_logger.debug("received PONG: elapsed={}ms", now - pingSentAt);
		}
		else {
			// 우리가 보낸 ping에 대한 응답이 아닌 경우(상대가 먼저 보낸 unsolicited pong).
			s_logger.debug("received unsolicited PONG");
		}

		m_lastPongTime.set(now);

		// 응답이 왔으므로 이 ping에 대한 감시 태스크는 더 이상 필요 없다.
		// 취소하지 않아도 위의 m_lastPongTime 검사가 오동작을 막지만, 불필요한 태스크를 남기지 않는다.
		ScheduledFuture<?> pending = m_latestPongTimeoutTask;
		if ( pending != null ) {
			pending.cancel(false);
			m_latestPongTimeoutTask = null;
		}

		webSocket.request(1);
		return null;
	}

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
		if ( isForeign(webSocket) ) {
			s_logger.debug("ignored error on a WebSocket not registered on the chart: {}", ""+error);
			return;
		}
		if ( isRegistered(webSocket) ) {
			m_peerClosed.complete(null);
		}

		// throwable을 마지막 인자로 넘겨야 스택 트레이스가 남는다.
		// 포맷 자리표시자에 대응시키면 toString()만 찍히고 원인 추적이 불가능해진다.
		s_logger.warn("WebSocket error", error);

		// 오류 시점 이후로 이 연결에서 더 이상 메시지가 오지 않으므로, 조립 중이던
		// 부분 텍스트 메시지는 버린다.
		m_textMessageBuilder.setLength(0);

		m_chart.handleSignal(new WebSocketError(webSocket, error));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		if ( isForeign(webSocket) ) {
			return null;
		}
		// 신호를 처리하기 전에 알린다. 차트를 닫는 쪽이 이 통지를 기다리는 중일 수 있기 때문이다.
		if ( isRegistered(webSocket) ) {
			m_peerClosed.complete(null);
		}
		s_logger.info("closed: statusCode={}, reason={}", statusCode, reason);

		// 조립 중이던 부분 텍스트 메시지는 완성될 수 없으므로 버린다.
		m_textMessageBuilder.setLength(0);

		m_chart.handleSignal(new ConnectionClosed(webSocket, statusCode, reason));

		return null;
    }
}
