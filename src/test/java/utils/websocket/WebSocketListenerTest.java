package utils.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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
import utils.websocket.Signals.BinaryMessage;
import utils.websocket.Signals.WebSocketError;
import utils.websocket.Signals.TextMessage;

/**
 * {@link WebSocketListener}의 keep-alive 동작과 수신 조각(fragment) 처리 검증.
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
		WebSocketListener<TestCtx> listener = m_chart.listener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(200));

		// pong을 돌려주지 않는 상대. (mock이므로 sendPing/sendPong은 아무 일도 하지 않는다)
		listener.startReceiving(mock(WebSocket.class));

		Signal signal = m_chart.awaitSignal(5, TimeUnit.SECONDS);
		Assertions.assertNotNull(signal, "pong timeout으로 WebSocketError가 발송되어야 함");
		Assertions.assertInstanceOf(WebSocketError.class, signal);
		Assertions.assertInstanceOf(RuntimeTimeoutException.class,
									((WebSocketError)signal).getError());
	}

	@Test
	public void pongTimeout_stops_ping_after_first_report() throws Exception {
		// 감시는 한 번만 보고하고 멈춘다. 상태 차트가 계속 실행되더라도 WebSocketError가 반복 발송되지 않는다.
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(100));

		listener.startReceiving(mock(WebSocket.class));

		Assertions.assertInstanceOf(WebSocketError.class, m_chart.awaitSignal(5, TimeUnit.SECONDS));
		Assertions.assertNull(m_chart.awaitSignal(500, TimeUnit.MILLISECONDS),
							"pong timeout 보고 이후에는 추가 신호가 없어야 함");
	}

	@Test
	public void pongTimeout_does_not_fire_while_pongs_arrive() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		listener.setPingInterval(Duration.ofMillis(50));
		listener.setPongTimeout(Duration.ofMillis(200));

		WebSocket ws = mock(WebSocket.class);
		listener.startReceiving(ws);

		// pongTimeout(200ms)을 여러 번 넘길 동안 꾸준히 pong을 돌려준다.
		for ( int i = 0; i < 15; ++i ) {
			listener.onPong(ws, null);
			Thread.sleep(50);
		}

		Assertions.assertNull(m_chart.awaitSignal(100, TimeUnit.MILLISECONDS),
							"pong이 정상 도착하는 동안에는 어떤 신호도 발송되지 않아야 함");
	}

	// ---- 수신 조각 처리 (검토 websocket #10, #14) ----

	@Test
	public void text_fragments_are_joined_into_one_message() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		WebSocket ws = mock(WebSocket.class);

		listener.onText(ws, "ab", false);
		listener.onText(ws, "cd", true);

		Signal signal = m_chart.awaitSignal(1, TimeUnit.SECONDS);
		Assertions.assertEquals("abcd", Assertions.assertInstanceOf(TextMessage.class, signal).getText());
		Assertions.assertNull(m_chart.awaitSignal(100, TimeUnit.MILLISECONDS));
		verify(ws, times(2)).request(1);
	}

	@Test
	public void binary_fragments_are_delivered_one_by_one() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		WebSocket ws = mock(WebSocket.class);

		listener.onBinary(ws, ByteBuffer.wrap(new byte[] { 1, 2 }), false);
		listener.onBinary(ws, ByteBuffer.wrap(new byte[] { 3 }), true);

		BinaryMessage first = Assertions.assertInstanceOf(BinaryMessage.class, m_chart.awaitSignal(1, TimeUnit.SECONDS));
		Assertions.assertArrayEquals(new byte[] { 1, 2 }, first.getBytes());
		Assertions.assertFalse(first.isLast());
		BinaryMessage second = Assertions.assertInstanceOf(BinaryMessage.class, m_chart.awaitSignal(1, TimeUnit.SECONDS));
		Assertions.assertArrayEquals(new byte[] { 3 }, second.getBytes());
		Assertions.assertTrue(second.isLast());
	}

	@Test
	public void text_message_over_max_length_is_discarded_with_error() throws Exception {
		m_chart = new RecordingChart();
		m_chart.setMaxTextMessageLength(4);
		WebSocketListener<TestCtx> listener = m_chart.listener();
		WebSocket ws = mock(WebSocket.class);

		// 상한과 같은 길이는 받는다.
		listener.onText(ws, "abcd", true);
		Assertions.assertEquals("abcd", ((TextMessage)m_chart.awaitSignal(1, TimeUnit.SECONDS)).getText());

		// 상한을 넘는 순간 오류 한 번, 그 메시지의 남은 조각은 버린다.
		listener.onText(ws, "abc", false);
		listener.onText(ws, "de", false);
		listener.onText(ws, "fgh", true);
		WebSocketError error = Assertions.assertInstanceOf(WebSocketError.class, m_chart.awaitSignal(1, TimeUnit.SECONDS));
		Assertions.assertInstanceOf(IllegalStateException.class, error.getError());
		Assertions.assertNull(m_chart.awaitSignal(100, TimeUnit.MILLISECONDS));

		// 다음 메시지부터 다시 받는다.
		listener.onText(ws, "ok", true);
		Assertions.assertEquals("ok", ((TextMessage)m_chart.awaitSignal(1, TimeUnit.SECONDS)).getText());
		verify(ws, times(5)).request(1);
	}

	@Test
	public void single_fragment_over_max_length_does_not_discard_next_message() throws Exception {
		m_chart = new RecordingChart();
		m_chart.setMaxTextMessageLength(4);
		WebSocketListener<TestCtx> listener = m_chart.listener();
		WebSocket ws = mock(WebSocket.class);

		listener.onText(ws, "abcdef", true);
		Assertions.assertInstanceOf(WebSocketError.class, m_chart.awaitSignal(1, TimeUnit.SECONDS));
		listener.onText(ws, "ok", true);
		Assertions.assertEquals("ok", ((TextMessage)m_chart.awaitSignal(1, TimeUnit.SECONDS)).getText());
	}

	// ---- 등록되지 않은 연결의 콜백, 상대 종료 통지 ----

	@Test
	public void callbacks_from_unregistered_socket_are_ignored() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		WebSocket registered = mock(WebSocket.class);
		WebSocket abandoned = mock(WebSocket.class);
		m_chart.setWebSocket(registered);

		// 차트에 등록하지 않고 닫는 연결(늦게 완료된 연결, 재연결 시도)의 콜백은 차트에 전달하지 않는다.
		listener.onError(abandoned, new java.io.IOException("abandoned"));
		listener.onClose(abandoned, 1000, "bye");
		listener.onText(abandoned, "x", true);
		Assertions.assertNull(m_chart.awaitSignal(100, TimeUnit.MILLISECONDS));
		Assertions.assertFalse(listener.peerClosed().toCompletableFuture().isDone());

		listener.onClose(registered, 1000, "bye");
		Assertions.assertInstanceOf(Signals.ConnectionClosed.class, m_chart.awaitSignal(1, TimeUnit.SECONDS));
		Assertions.assertTrue(listener.peerClosed().toCompletableFuture().isDone());
	}

	@Test
	public void onOpen_does_not_request_nor_start_ping() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		listener.setPingInterval(Duration.ofMillis(20));
		listener.setPongTimeout(Duration.ofMillis(20));
		WebSocket ws = mock(WebSocket.class);

		listener.onOpen(ws);
		Thread.sleep(150);

		verify(ws, org.mockito.Mockito.never()).request(org.mockito.ArgumentMatchers.anyLong());
		verify(ws, org.mockito.Mockito.never()).sendPing(org.mockito.ArgumentMatchers.any());
		Assertions.assertNull(m_chart.awaitSignal(50, TimeUnit.MILLISECONDS));
	}

	// ---- 공유 스케줄러 ----

	@Test
	public void ping_runs_on_shared_scheduler_and_stops_with_chart() throws Exception {
		m_chart = new RecordingChart();
		WebSocketListener<TestCtx> listener = m_chart.listener();
		listener.setPingInterval(Duration.ofMillis(20));
		listener.setPongTimeout(Duration.ofSeconds(10));
		WebSocket ws = mock(WebSocket.class);
		java.util.concurrent.atomic.AtomicReference<String> pingThread = new java.util.concurrent.atomic.AtomicReference<>();
		org.mockito.Mockito.when(ws.sendPing(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
			pingThread.set(Thread.currentThread().getName());
			return java.util.concurrent.CompletableFuture.completedFuture(ws);
		});

		listener.startReceiving(ws);
		verify(ws, org.mockito.Mockito.timeout(1000).atLeast(2)).sendPing(org.mockito.ArgumentMatchers.any());
		Assertions.assertEquals("websocket-scheduler", pingThread.get());
		// 차트마다 만들던 java.util.Timer 스레드는 더 이상 없다.
		Assertions.assertTrue(Thread.getAllStackTraces().keySet().stream()
									.noneMatch(t -> t.getName().startsWith("WebSocket-Ping-Timer")));

		m_chart.notifyStarted();
		m_chart.complete();						// 종료 훅이 ping을 멈춘다.
		Thread.sleep(50);
		int count = org.mockito.Mockito.mockingDetails(ws).getInvocations().stream()
						.filter(inv -> inv.getMethod().getName().equals("sendPing")).toList().size();
		Thread.sleep(150);
		int later = org.mockito.Mockito.mockingDetails(ws).getInvocations().stream()
						.filter(inv -> inv.getMethod().getName().equals("sendPing")).toList().size();
		Assertions.assertEquals(count, later, "ping continued after the chart finished");
	}
}
