package utils.websocket;

import static org.mockito.Mockito.mock;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.RuntimeTimeoutException;
import utils.statechart.Signal;
import utils.statechart.Transition;
import utils.websocket.Signals.ErrorMessage;

/**
 * {@link WebSocketListener}의 keep-alive 동작 검증.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketListenerTest {
	private RecordingChart m_chart;

	private static class TestCtx extends WebSocketContext<TestCtx> {
		TestCtx(String url) { super(url); }
	}

	/** 차트로 전달된 신호를 기록하기만 하는 테스트용 차트. */
	private static class RecordingChart extends WebSocketStateChart<TestCtx> {
		private final BlockingQueue<Signal> m_signals = new LinkedBlockingQueue<>();

		RecordingChart() {
			super(new TestCtx("ws://localhost:8080/test"));
		}

		@Override
		public Optional<Transition<TestCtx>> handleSignal(Signal signal) {
			m_signals.add(signal);
			return Optional.empty();
		}

		Signal awaitSignal(long timeout, TimeUnit unit) throws InterruptedException {
			return m_signals.poll(timeout, unit);
		}
	}

	@AfterEach
	public void cleanup() {
		// 차트를 종료시켜 ping 타이머를 정리한다. (whenFinished 훅이 담당)
		if ( m_chart != null && !m_chart.isDone() ) {
			m_chart.notifyStarted();
			m_chart.complete();
		}
	}

	@Test
	public void pongTimeout_fires_when_longer_than_pingInterval() throws Exception {
		// 회귀 방지: pongTimeout > pingInterval인 설정에서 예전 구현은 감시 태스크가 매번 다음 ping에
		// 의해 발동 전에 취소되어 pong이 끊겨도 영원히 감지되지 않았다.
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.getWebSocketListener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(200));

		// pong을 돌려주지 않는 상대. (mock이므로 sendPing/sendPong은 아무 일도 하지 않는다)
		listener.onOpen(mock(WebSocket.class));

		Signal signal = m_chart.awaitSignal(5, TimeUnit.SECONDS);
		Assertions.assertNotNull(signal, "pong timeout으로 ErrorMessage가 발송되어야 함");
		Assertions.assertInstanceOf(ErrorMessage.class, signal);
		Assertions.assertInstanceOf(RuntimeTimeoutException.class,
									((ErrorMessage)signal).getError());
	}

	@Test
	public void pongTimeout_stops_ping_after_first_report() throws Exception {
		// 감시는 한 번만 보고하고 멈춘다. 상태머신이 계속 실행되더라도 ErrorMessage가 반복 발송되지 않는다.
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.getWebSocketListener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(100));

		listener.onOpen(mock(WebSocket.class));

		Assertions.assertInstanceOf(ErrorMessage.class, m_chart.awaitSignal(5, TimeUnit.SECONDS));
		Assertions.assertNull(m_chart.awaitSignal(500, TimeUnit.MILLISECONDS),
							"pong timeout 보고 이후에는 추가 신호가 없어야 함");
	}

	@Test
	public void pongTimeout_does_not_fire_while_pongs_arrive() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.getWebSocketListener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(200));

		WebSocket ws = mock(WebSocket.class);
		listener.onOpen(ws);

		// pongTimeout(200ms)을 여러 번 넘길 동안 꾸준히 pong을 돌려준다.
		for ( int i = 0; i < 15; ++i ) {
			listener.onPong(ws, null);
			Thread.sleep(50);
		}

		Assertions.assertNull(m_chart.awaitSignal(100, TimeUnit.MILLISECONDS),
							"pong이 정상 도착하는 동안에는 어떤 신호도 발송되지 않아야 함");
	}
}
