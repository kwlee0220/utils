package utils.websocket;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link WebSocketStateChart}의 인자 검증 및 라이프사이클 계약을 검증한다.
 * <p>
 * 실제 WebSocket 연결이 필요한 시나리오(송신 성공 경로 등)는 외부 의존성이 너무 커서 통합 테스트로 분리한다.
 * 본 테스트는 단위 검증과, mock WebSocket으로 확인할 수 있는 대체 종료 경로를 다룬다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketStateChartTest {

	private static class TestCtx extends WebSocketContext<TestCtx> {
		TestCtx(String url) { super(url); }
	}

	private WebSocketStateChart<TestCtx> newChart() {
		return new WebSocketStateChart<>(new TestCtx("ws://localhost:8080/test"));
	}

	// ---- listener 접근자 ----

	@Test
	public void listener_returns_non_null() {
		Assertions.assertNotNull(newChart().listener());
	}

	// ---- setWebSocket / getWebSocket ----

	@Test
	public void getWebSocket_null_before_setWebSocket() {
		Assertions.assertNull(newChart().getWebSocket());
	}

	@Test
	public void setWebSocket_then_getWebSocket() {
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = mock(WebSocket.class);
		chart.setWebSocket(ws);

		Assertions.assertSame(ws, chart.getWebSocket());
	}

	@Test
	public void setWebSocket_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setWebSocket(null);
		});
	}

	@Test
	public void setWebSocket_double_call_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			WebSocketStateChart<TestCtx> chart = newChart();
			chart.setWebSocket(mock(WebSocket.class));
			chart.setWebSocket(mock(WebSocket.class));
		});
	}

	// ---- setPingInterval ----

	@Test
	public void setPingInterval_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPingInterval(null);
		});
	}

	@Test
	public void setPingInterval_zero_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPingInterval(Duration.ZERO);
		});
	}

	@Test
	public void setPingInterval_negative_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPingInterval(Duration.ofMillis(-1));
		});
	}

	@Test
	public void setPingInterval_positive_accepted() {
		newChart().setPingInterval(Duration.ofSeconds(5));
	}

	// ---- setPongTimeout ----

	@Test
	public void setPongTimeout_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPongTimeout(null);
		});
	}

	@Test
	public void setPongTimeout_zero_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPongTimeout(Duration.ZERO);
		});
	}

	@Test
	public void setPongTimeout_negative_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().setPongTimeout(Duration.ofMillis(-1));
		});
	}

	@Test
	public void setPongTimeout_positive_accepted() {
		newChart().setPongTimeout(Duration.ofSeconds(2));
	}

	// ---- sendText ----

	@Test
	public void sendText_null_text_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().sendText(null, true);
		});
	}

	@Test
	public void sendText_before_chart_running_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			WebSocketStateChart<TestCtx> chart = newChart();
			chart.setWebSocket(mock(WebSocket.class));   // 연결은 등록했지만 차트는 NOT_STARTED
			chart.sendText("hello", true);
		});
	}

	// ---- sendBinary ----

	@Test
	public void sendBinary_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			newChart().sendBinary(null, true);
		});
	}

	@Test
	public void sendBinary_before_chart_running_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			WebSocketStateChart<TestCtx> chart = newChart();
			chart.setWebSocket(mock(WebSocket.class));
			chart.sendBinary(new byte[] { 1, 2, 3 }, true);
		});
	}

	// ---- handleSignal ----

	@Test
	public void handleSignal_null_rejected() {
		// handleSignal() 재정의를 onUnhandledSignal()로 옮긴 뒤로는 StateChart와 같이 NullPointerException이다.
		Assertions.assertThrows(NullPointerException.class, () -> {
			newChart().handleSignal(null);
		});
	}

	// ---- close ----

	private WebSocket newMockWebSocket() {
		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(ws));
		return ws;
	}

	@Test
	public void close_before_connect_is_noop() {
		// 연결 수립 전(WebSocket 미등록)에 닫아도 예외가 나지 않아야 한다.
		Assertions.assertDoesNotThrow(() -> newChart().closeConnection());
	}

	@Test
	public void close_sends_normal_closure() {
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = newMockWebSocket();
		chart.setWebSocket(ws);

		chart.closeConnection();

		verify(ws).sendClose(eq(WebSocket.NORMAL_CLOSURE), anyString());
	}

	@Test
	public void close_repeated_is_safe() {
		// best-effort 시맨틱이므로 반복 호출이 안전해야 한다.
		// (종료 상태의 enter()와 차트 종료 훅이 연달아 호출하는 경우가 실제로 발생한다)
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setWebSocket(newMockWebSocket());

		Assertions.assertDoesNotThrow(() -> {
			chart.closeConnection();
			chart.closeConnection();
		});
	}

	@Test
	public void close_is_quiet_when_sendClose_throws() {
		// close 실패가 호출자에게 전파되면 종료 상태 진입이나 종료 통지가 실패로 뒤집힌다.
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenThrow(new IllegalStateException("already closed"));
		chart.setWebSocket(ws);

		Assertions.assertDoesNotThrow(() -> chart.closeConnection());
	}

	// ---- 차트 종료 시 자동 close ----

	@Test
	public void fail_closes_websocket() {
		// 종료 상태를 거치지 않는 fail()에서도 연결이 닫혀야 한다.
		// (WebSocketError fall-back이 밟는 경로다)
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = newMockWebSocket();
		chart.notifyStarted();
		chart.setWebSocket(ws);

		chart.fail(new IllegalStateException("boom"));

		verify(ws).sendClose(WebSocket.NORMAL_CLOSURE, "closure due to an error");
	}

	@Test
	public void complete_closes_websocket() {
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = newMockWebSocket();
		chart.notifyStarted();
		chart.setWebSocket(ws);

		chart.complete();

		verify(ws).sendClose(WebSocket.NORMAL_CLOSURE, "normal closure");
	}

	@Test
	public void unhandled_connectionClosed_cancels_chart_and_closes_socket() throws Exception {
		// 처리되지 않은 ConnectionClosed는 대체 종료로 cancel(true)된다. 취소가 CANCELLED로 확정되어야
		// 종료 훅이 실행되어 연결이 닫힌다.
		WebSocketStateChart<TestCtx> chart = newChart();
		TestCtx ctx = chart.getContext();
		chart.addState(new utils.statechart.AbstractState<TestCtx>("/Waiting", ctx) {
			@Override
			public java.util.Optional<utils.statechart.Transition<TestCtx>> selectTransition(
																			utils.statechart.Signal signal) {
				return java.util.Optional.empty();
			}
		});
		chart.addState(new States.CompletedState<>("/Completed", ctx));
		chart.setInitialState("/Waiting");
		chart.addFinalState("/Completed");
		chart.start();
		WebSocket ws = newMockWebSocket();
		chart.setWebSocket(ws);

		chart.handleSignal(new Signals.ConnectionClosed(ws, WebSocket.NORMAL_CLOSURE, "bye"));

		Assertions.assertTrue(chart.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).isCancelled());
		verify(ws).sendClose(WebSocket.NORMAL_CLOSURE, "normal closure");
	}

	@Test
	public void openWebSocket_uses_registered_transitions_for_other_signals() {
		WebSocketStateChart<TestCtx> chart = newChart();
		TestCtx ctx = chart.getContext();
		utils.statechart.Signal cancel = new utils.statechart.Signal() { };
		States.WebSocketConnectingState<TestCtx> open = new States.WebSocketConnectingState<>("/Open", ctx, "/Running", "/Error");
		open.on(cancel, utils.statechart.Transitions.to("/Cancelled"));

		// 접속 결과가 아닌 신호는 on(...)으로 등록한 전이를 따른다.
		Assertions.assertEquals(java.util.Optional.of("/Cancelled"),
								open.selectTransition(cancel).orElseThrow().getTargetStatePath());
		Assertions.assertTrue(open.selectTransition(new utils.statechart.Signal() { }).isEmpty());
		// 접속 결과 신호는 지금처럼 처리한다.
		Assertions.assertEquals(java.util.Optional.of("/Running"),
								open.selectTransition(new Signals.Connected(mock(WebSocket.class)))
									.orElseThrow().getTargetStatePath());
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void connection_completed_after_chart_finished_is_closed() throws Exception {
		// 업그레이드 응답을 늦춰, 접속이 끝나기 전에 차트를 취소한다(검토 websocket #1).
		java.util.concurrent.CountDownLatch closing = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger closeCode = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicReference<String> closeReason = new java.util.concurrent.atomic.AtomicReference<>();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse()
								.setHeadersDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
								.withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					closeCode.set(code);
					closeReason.set(reason);
					ws.close(code, null);
					closing.countDown();
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
			TestCtx ctx = chart.getContext();
			chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Completed", "/Error"));
			chart.addState(new States.CompletedState<>("/Completed", ctx));
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Open");
			chart.start();

			Assertions.assertTrue(chart.cancel(true));
			Assertions.assertTrue(chart.isCancelled());

			// 뒤늦게 완료된 연결은 차트에 등록되지 않고 바로 닫힌다.
			Assertions.assertTrue(closing.await(5, java.util.concurrent.TimeUnit.SECONDS), "connection was left open");
			Assertions.assertEquals(WebSocket.NORMAL_CLOSURE, closeCode.get());
			Assertions.assertEquals(States.WebSocketConnectingState.ABANDONED_CLOSE_REASON, closeReason.get());
			Assertions.assertNull(chart.getWebSocket());
		}
	}

	@Test
	public void startReceiving_after_chart_finished_does_not_start_ping() throws Exception {
		WebSocketStateChart<TestCtx> chart = newChart();
		TestCtx ctx = chart.getContext();
		chart.setPingInterval(Duration.ofMillis(30));
		chart.setPongTimeout(Duration.ofMillis(30));
		chart.addState(new utils.statechart.DefaultState<>("/A", ctx));
		chart.addState(new utils.statechart.TerminalState<>("/Done", ctx));
		chart.setInitialState("/A");
		chart.start();
		chart.cancel(true);
		Assertions.assertTrue(chart.isDone());

		// 차트가 끝난 뒤 수신을 시작하는 경우. 종료 훅이 이미 실행되었으므로 ping 타이머를 시작하면 멈출 곳이 없다.
		WebSocket ws = mock(WebSocket.class);
		chart.listener().onOpen(ws);
		chart.listener().startReceiving(ws);
		Thread.sleep(200);

		verify(ws).request(1);
		verify(ws, org.mockito.Mockito.never()).sendPing(org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void connection_failure_fails_chart_with_actual_cause() throws Exception {
		// 접속할 수 없는 주소(로컬의 1번 포트)로 접속 실패를 일으킨다. 실패 원인이 FailedState에 전달되어
		// 차트가 "원인 없음"이 아니라 실제 접속 실패 원인으로 실패해야 한다.
		WebSocketStateChart<TestCtx> chart
					= new WebSocketStateChart<>(new TestCtx("ws://127.0.0.1:1/test"));
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Completed", "/Error"));
		chart.addState(new States.CompletedState<>("/Completed", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Open");
		chart.addFinalState("/Completed");
		chart.addFinalState("/Error");
		chart.start();

		Throwable cause = chart.waitForFinished(10, java.util.concurrent.TimeUnit.SECONDS).getFailureCause();
		Assertions.assertNotNull(cause);
		Assertions.assertFalse(cause.getMessage() != null && cause.getMessage().contains("failure cause is not set"),
								"cause=" + cause);
		Assertions.assertFalse(cause instanceof java.util.concurrent.CompletionException, "cause=" + cause);
	}

	// ---- 대체 종료: close 상태 코드 구분 (검토 websocket #2) ----

	private WebSocketStateChart<TestCtx> startWaitingChart(WebSocketStateChart<TestCtx> chart) {
		TestCtx ctx = chart.getContext();
		chart.addState(new utils.statechart.DefaultState<>("/Waiting", ctx));
		chart.addState(new States.CompletedState<>("/Completed", ctx));
		chart.setInitialState("/Waiting");
		chart.start();
		return chart;
	}

	@Test
	public void unhandled_abnormal_close_fails_chart_with_status_code() throws Exception {
		WebSocketStateChart<TestCtx> chart = startWaitingChart(newChart());
		WebSocket ws = newMockWebSocket();
		chart.setWebSocket(ws);

		chart.handleSignal(new Signals.ConnectionClosed(ws, 1011, "internal error"));

		Throwable cause = chart.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).getFailureCause();
		WebSocketClosedException closed = Assertions.assertInstanceOf(WebSocketClosedException.class, cause);
		Assertions.assertEquals(1011, closed.getStatusCode());
		Assertions.assertEquals("internal error", closed.getReason());
		Assertions.assertTrue(chart.isFailed());
	}

	@Test
	public void unhandled_close_without_status_code_cancels_chart() throws Exception {
		// 1005(상태 코드 없음)와 1001(going away)은 정상 종료로 본다.
		for ( int code: new int[] { WebSocketClosedException.NO_STATUS_CODE, WebSocketClosedException.GOING_AWAY } ) {
			WebSocketStateChart<TestCtx> chart = startWaitingChart(newChart());
			WebSocket ws = newMockWebSocket();
			chart.setWebSocket(ws);

			chart.handleSignal(new Signals.ConnectionClosed(ws, code, ""));

			Assertions.assertTrue(chart.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).isCancelled(),
									"code=" + code);
		}
	}

	@Test
	public void isNormalClosure_classifies_status_codes() {
		Assertions.assertTrue(WebSocketClosedException.isNormalClosure(WebSocket.NORMAL_CLOSURE));
		Assertions.assertTrue(WebSocketClosedException.isNormalClosure(1001));
		Assertions.assertTrue(WebSocketClosedException.isNormalClosure(1005));
		Assertions.assertFalse(WebSocketClosedException.isNormalClosure(1006));
		Assertions.assertFalse(WebSocketClosedException.isNormalClosure(1011));
	}

	// ---- 대체 종료는 하위 클래스의 cancel/fail 재정의를 거치지 않는다 (검토 websocket #6) ----

	@Test
	public void fallback_bypasses_overridden_cancel_and_fail() throws Exception {
		java.util.concurrent.atomic.AtomicInteger overridden = new java.util.concurrent.atomic.AtomicInteger();
		class StubbornChart extends WebSocketStateChart<TestCtx> {
			StubbornChart() { super(new TestCtx("ws://localhost:8080/test")); }
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				// 예: 중지 신호만 보내고 실제 취소는 상태가 처리하기를 기대하는 재정의
				overridden.incrementAndGet();
				return false;
			}
			@Override
			public void fail(Throwable cause) {
				overridden.incrementAndGet();
			}
		}

		WebSocketStateChart<TestCtx> cancelled = startWaitingChart(new StubbornChart());
		WebSocket ws1 = newMockWebSocket();
		cancelled.setWebSocket(ws1);
		cancelled.handleSignal(new Signals.ConnectionClosed(ws1, WebSocket.NORMAL_CLOSURE, "bye"));
		Assertions.assertTrue(cancelled.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).isCancelled());

		WebSocketStateChart<TestCtx> failed = startWaitingChart(new StubbornChart());
		WebSocket ws2 = newMockWebSocket();
		failed.setWebSocket(ws2);
		RuntimeException error = new RuntimeException("boom");
		failed.handleSignal(new Signals.WebSocketError(ws2, error));
		Assertions.assertSame(error, failed.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).getFailureCause());

		Assertions.assertEquals(0, overridden.get());
	}

	// ---- close 시간 제한 (검토 websocket #7) ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void close_aborts_when_close_frame_is_not_sent_in_time() {
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setCloseTimeout(Duration.ofMillis(100));
		WebSocket ws = mock(WebSocket.class);
		// 출력 버퍼가 막혀 close 프레임 전송이 끝나지 않는 경우
		when(ws.sendClose(anyInt(), anyString())).thenReturn(new CompletableFuture<>());
		chart.setWebSocket(ws);

		long started = System.nanoTime();
		chart.closeConnection();
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		verify(ws).abort();
		Assertions.assertTrue(elapsedMillis < 2000, "elapsed=" + elapsedMillis);
	}

	@Test
	public void setCloseTimeout_rejects_non_positive() {
		WebSocketStateChart<TestCtx> chart = newChart();
		Assertions.assertEquals(WebSocketStateChart.DEFAULT_CLOSE_TIMEOUT, chart.getCloseTimeout());
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setCloseTimeout(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setCloseTimeout(Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setCloseTimeout(Duration.ofSeconds(-1)));
	}

	// ---- HttpClient 공유와 접속 시간 제한 (검토 websocket #8) ----

	@Test
	public void charts_share_default_http_client() {
		Assertions.assertSame(newChart().getHttpClient(), newChart().getHttpClient());
	}

	@Test
	public void setConnectTimeout_validates_and_allows_null() {
		WebSocketStateChart<TestCtx> chart = newChart();
		Assertions.assertEquals(WebSocketStateChart.DEFAULT_CONNECT_TIMEOUT, chart.getConnectTimeout());
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setConnectTimeout(Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setConnectTimeout(Duration.ofSeconds(-1)));
		chart.setConnectTimeout(null);
		Assertions.assertNull(chart.getConnectTimeout());
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setHttpClient(null));
	}

	@Test
	public void openWebSocket_uses_chart_http_client_and_connect_timeout() {
		java.net.http.HttpClient client = mock(java.net.http.HttpClient.class);
		WebSocket.Builder builder = mock(WebSocket.Builder.class);
		when(client.newWebSocketBuilder()).thenReturn(builder);
		when(builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.buildAsync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(new CompletableFuture<>());

		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(client);
		chart.setConnectTimeout(Duration.ofSeconds(3));
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Completed", "/Error"));
		chart.addState(new States.CompletedState<>("/Completed", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Open");
		chart.start();

		verify(builder).connectTimeout(Duration.ofSeconds(3));
		verify(builder).buildAsync(eq(java.net.URI.create("ws://localhost:8080/test")),
									org.mockito.ArgumentMatchers.any(WebSocket.Listener.class));
		chart.cancel(true);
	}

	// ---- 재연결 거부 (검토 websocket #4) ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void second_connection_is_closed_and_fails_chart() throws Exception {
		// 이미 연결이 등록된 차트가 접속 상태로 다시 접속한 경우. 이전에는 콜백 안의 setWebSocket()
		// 예외가 사라져 새 연결은 열린 채 남고 차트는 접속 상태에 머물렀다.
		java.util.concurrent.CountDownLatch closing = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicReference<String> closeReason = new java.util.concurrent.atomic.AtomicReference<>();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse()
								.withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					closeReason.set(reason);
					ws.close(code, null);
					closing.countDown();
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
			WebSocket previous = newMockWebSocket();
			chart.setWebSocket(previous);
			TestCtx ctx = chart.getContext();
			chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Completed", "/Error"));
			chart.addState(new States.CompletedState<>("/Completed", ctx));
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Open");
			chart.start();

			Throwable cause = chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).getFailureCause();
			Assertions.assertInstanceOf(IllegalStateException.class, cause);
			Assertions.assertTrue(closing.await(5, java.util.concurrent.TimeUnit.SECONDS), "new connection was left open");
			Assertions.assertEquals(States.WebSocketConnectingState.RECONNECTION_CLOSE_REASON, closeReason.get());
			Assertions.assertSame(previous, chart.getWebSocket());
		}
	}

	// ---- 접속 성공 전이 (검토 websocket #14) ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void connected_chart_sends_and_receives_then_completes() throws Exception {
		// 접속 성공 → Connected로 /Running 진입 → 송신 → 서버의 응답(TextMessage)으로 /Completed → 정상 close.
		java.util.concurrent.CountDownLatch closing = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger closeCode = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicReference<String> closeReason = new java.util.concurrent.atomic.AtomicReference<>();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse()
								.withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onMessage(okhttp3.WebSocket ws, String text) {
					ws.send("echo:" + text);
				}
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					closeCode.set(code);
					closeReason.set(reason);
					ws.close(code, null);
					closing.countDown();
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
			TestCtx ctx = chart.getContext();
			java.util.concurrent.atomic.AtomicReference<String> received = new java.util.concurrent.atomic.AtomicReference<>();
			chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Running", "/Error"));
			chart.addState(new utils.statechart.DefaultState<TestCtx>("/Running", ctx) {
				@Override
				public void enter() {
					// Connected를 받아 진입했으므로 연결이 등록되어 있다.
					getContext().getStateChart().sendText("hello", true);
				}
			}.on(Signals.TextMessage.class,
				utils.statechart.Transitions.to("/Completed",
										(c, sig) -> received.set(((Signals.TextMessage)sig).getText()))));
			chart.addState(new States.CompletedState<>("/Completed", ctx));
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Open");
			chart.start();

			Assertions.assertTrue(chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).isCompleted());
			Assertions.assertEquals("echo:hello", received.get());
			Assertions.assertNotNull(chart.getWebSocket());
			Assertions.assertTrue(closing.await(5, java.util.concurrent.TimeUnit.SECONDS), "connection was not closed");
			Assertions.assertEquals(WebSocket.NORMAL_CLOSURE, closeCode.get());
			Assertions.assertEquals("normal closure", closeReason.get());
		}
	}

	// ---- 접속 상태는 컨텍스트의 차트를 쓴다 (검토 websocket #12) ----

	@Test
	public void openWebSocket_rejects_null_target_paths() {
		TestCtx ctx = newChart().getContext();
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new States.WebSocketConnectingState<>("/Open", ctx, null, "/Error"));
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new States.WebSocketConnectingState<>("/Open", ctx, "/Running", null));
	}

	@Test
	public void openWebSocket_requires_context_attached_to_chart() {
		// 차트에 연결되지 않은 컨텍스트로 만든 상태는 접속할 차트를 알 수 없다.
		States.WebSocketConnectingState<TestCtx> open
						= new States.WebSocketConnectingState<>("/Open", new TestCtx("ws://localhost:8080/test"), "/Running", "/Error");
		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, open::enter);
		Assertions.assertTrue(e.getMessage().contains("/Open"), e.getMessage());
	}

	// ---- 텍스트 메시지 길이 상한 (검토 websocket #10) ----

	@Test
	public void setMaxTextMessageLength_validates() {
		WebSocketStateChart<TestCtx> chart = newChart();
		Assertions.assertEquals(WebSocketStateChart.DEFAULT_MAX_TEXT_MESSAGE_LENGTH, chart.getMaxTextMessageLength());
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setMaxTextMessageLength(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setMaxTextMessageLength(-1));
		chart.setMaxTextMessageLength(10);
		Assertions.assertEquals(10, chart.getMaxTextMessageLength());
	}

	// ---- close()와 서버의 close 응답, 서버가 먼저 닫는 경우 ----

	/** 받은 close 프레임의 코드와 사유를 기록하고, 받은 텍스트 메시지에 따라 서버가 먼저 닫는 테스트용 서버 리스너. */
	private static class ClosingServer extends okhttp3.WebSocketListener {
		final java.util.concurrent.CountDownLatch m_closing = new java.util.concurrent.CountDownLatch(1);
		volatile int m_closeCode;
		volatile String m_closeReason;

		@Override
		public void onMessage(okhttp3.WebSocket ws, String text) {
			// "close <code>"를 받으면 서버가 먼저 그 코드로 닫는다.
			if ( text.startsWith("close ") ) {
				ws.close(Integer.parseInt(text.substring(6)), "server closing");
			}
		}

		@Override
		public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
			m_closeCode = code;
			m_closeReason = reason;
			ws.close(code, null);
			m_closing.countDown();
		}
	}

	/**
	 * /Open → /Running으로 접속하는 차트를 시작하고, /Running에 진입할 때까지 기다린다.
	 *
	 * @param running	/Running 상태에 추가로 등록할 것. {@code null}이면 없음.
	 * @param onEnter	/Running 진입 시 실행할 것. {@code null}이면 없음.
	 */
	private WebSocketStateChart<TestCtx> startConnectedChart(okhttp3.mockwebserver.MockWebServer server,
										java.util.function.Consumer<utils.statechart.DefaultState<TestCtx>> running,
										java.util.function.Consumer<WebSocketStateChart<TestCtx>> onEnter)
		throws Exception {
		String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
		WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
		TestCtx ctx = chart.getContext();
		java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		utils.statechart.DefaultState<TestCtx> runningState = new utils.statechart.DefaultState<TestCtx>("/Running", ctx) {
			@Override
			public void enter() {
				if ( onEnter != null ) {
					onEnter.accept(getContext().getStateChart());
				}
				entered.countDown();
			}
		};
		if ( running != null ) {
			running.accept(runningState);
		}
		chart.addState(new States.WebSocketConnectingState<>("/Open", ctx, "/Running", "/Error"));
		chart.addState(runningState);
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Open");
		chart.start();
		Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "not connected");
		return chart;
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void user_close_cancels_chart_when_server_replies() throws Exception {
		// close()는 close 프레임만 보내지만, 서버의 close 응답이 ConnectionClosed로 와서 처리되지 않으면
		// 대체 종료로 차트가 취소된다.
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			WebSocketStateChart<TestCtx> chart = startConnectedChart(server, null, null);

			chart.closeConnection(WebSocket.NORMAL_CLOSURE, "bye");

			Assertions.assertTrue(serverListener.m_closing.await(5, java.util.concurrent.TimeUnit.SECONDS));
			Assertions.assertEquals("bye", serverListener.m_closeReason);
			Assertions.assertTrue(chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).isCancelled());
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void user_close_keeps_chart_running_when_connectionClosed_is_handled() throws Exception {
		// 연결만 끊고 차트를 유지하려면 ConnectionClosed를 처리하는 전이를 등록한다.
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			java.util.concurrent.CountDownLatch closedHandled = new java.util.concurrent.CountDownLatch(1);
			WebSocketStateChart<TestCtx> chart = startConnectedChart(server,
					st -> st.on(Signals.ConnectionClosed.class,
								utils.statechart.Transitions.internal((c, sig) -> closedHandled.countDown())),
					null);

			chart.closeConnection(WebSocket.NORMAL_CLOSURE, "bye");

			Assertions.assertTrue(closedHandled.await(5, java.util.concurrent.TimeUnit.SECONDS));
			Assertions.assertTrue(chart.isRunning());
			chart.cancel(true);
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void server_abnormal_close_fails_chart_and_replies_close() throws Exception {
		// 서버가 먼저 1011로 닫으면 JDK의 onClose 스레드에서 대체 종료가 차트를 실패시키고, 종료 훅의 close()가
		// 그 스레드 안에서 응답 close 프레임을 보낸다(close 시간 제한까지 기다리지 않아야 한다).
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			WebSocketStateChart<TestCtx> chart
						= startConnectedChart(server, null, ch -> ch.sendText("close 1011", true));

			Throwable cause = chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).getFailureCause();
			WebSocketClosedException closed = Assertions.assertInstanceOf(WebSocketClosedException.class, cause);
			Assertions.assertEquals(1011, closed.getStatusCode());
			Assertions.assertEquals("server closing", closed.getReason());

			Assertions.assertTrue(serverListener.m_closing.await(2, java.util.concurrent.TimeUnit.SECONDS),
									"close reply was not sent");
			Assertions.assertEquals(WebSocket.NORMAL_CLOSURE, serverListener.m_closeCode);
			Assertions.assertEquals("closure due to an error", serverListener.m_closeReason);
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void server_normal_close_cancels_chart() throws Exception {
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			WebSocketStateChart<TestCtx> chart
						= startConnectedChart(server, null, ch -> ch.sendText("close 1000", true));

			Assertions.assertTrue(chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).isCancelled());
			Assertions.assertTrue(serverListener.m_closing.await(2, java.util.concurrent.TimeUnit.SECONDS));
			Assertions.assertEquals("normal closure", serverListener.m_closeReason);
		}
	}

	// ---- 접속 흐름: 수신 시작 시점, 콜백 스레드, 상태를 떠난 뒤의 연결 ----

	/** buildAsync의 결과를 테스트가 정하는 mock HttpClient. */
	private static class ControlledClient {
		final java.net.http.HttpClient m_client = mock(java.net.http.HttpClient.class);
		final WebSocket.Builder m_builder = mock(WebSocket.Builder.class);
		final CompletableFuture<WebSocket> m_future = new CompletableFuture<>();

		ControlledClient() {
			when(m_client.newWebSocketBuilder()).thenReturn(m_builder);
			when(m_builder.connectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(m_builder);
			when(m_builder.buildAsync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(m_future);
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void receiving_starts_after_connected_is_processed() throws Exception {
		// 서버가 접속 직후 보낸 메시지가 Connected보다 먼저 접속 상태에 도착해 버려지지 않도록, 첫 수신 요청은
		// Connected 처리(다음 상태 진입) 뒤에 한다. 결과 처리는 공용 풀이 아닌 패키지 전용 스레드에서 한다.
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		WebSocket ws = newMockWebSocket();
		java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicBoolean requestedBeforeReady = new java.util.concurrent.atomic.AtomicBoolean();
		java.util.concurrent.atomic.AtomicReference<String> threadName = new java.util.concurrent.atomic.AtomicReference<>();
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
		chart.addState(new utils.statechart.DefaultState<TestCtx>("/Ready", ctx) {
			@Override
			public void enter() {
				requestedBeforeReady.set(org.mockito.Mockito.mockingDetails(ws).getInvocations().stream()
											.anyMatch(inv -> inv.getMethod().getName().equals("request")));
				threadName.set(Thread.currentThread().getName());
				entered.countDown();
			}
		});
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();

		// JDK는 onOpen을 접속 future 완료 전후 어느 때나 부를 수 있다. onOpen은 수신을 요청하지 않아야 한다.
		chart.listener().onOpen(ws);
		verify(ws, org.mockito.Mockito.never()).request(org.mockito.ArgumentMatchers.anyLong());
		cc.m_future.complete(ws);

		Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
		Assertions.assertFalse(requestedBeforeReady.get(), "receiving started before Connected was processed");
		verify(ws, org.mockito.Mockito.timeout(1000)).request(1);
		Assertions.assertTrue(threadName.get().startsWith("websocket-callback-"), threadName.get());
		chart.cancel(true);
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void greeting_sent_right_after_upgrade_is_received() throws Exception {
		// 서버가 업그레이드 직후 먼저 보내는 메시지(인사·인증 등)를 다음 상태가 받는다.
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
					ws.send("hello");
				}
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					ws.close(code, null);
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
			TestCtx ctx = chart.getContext();
			java.util.concurrent.atomic.AtomicReference<String> greeting = new java.util.concurrent.atomic.AtomicReference<>();
			chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
			chart.addState(new utils.statechart.DefaultState<TestCtx>("/Ready", ctx)
								.on(Signals.TextMessage.class,
									utils.statechart.Transitions.to("/Done",
											(c, sig) -> greeting.set(((Signals.TextMessage)sig).getText()))));
			chart.addState(new States.CompletedState<>("/Done", ctx));
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Connect");
			chart.start();

			Assertions.assertTrue(chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).isCompleted());
			Assertions.assertEquals("hello", greeting.get());
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void connection_completed_after_leaving_connecting_state_is_closed() throws Exception {
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		utils.statechart.Signal leave = new utils.statechart.Signal() { };
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error")
							.on(leave, utils.statechart.Transitions.to("/Waiting")));
		chart.addState(new utils.statechart.DefaultState<>("/Waiting", ctx));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		chart.handleSignal(leave);

		WebSocket ws = newMockWebSocket();
		cc.m_future.complete(ws);

		verify(ws, org.mockito.Mockito.timeout(2000))
			.sendClose(WebSocket.NORMAL_CLOSURE, States.WebSocketConnectingState.STATE_LEFT_CLOSE_REASON);
		verify(ws, org.mockito.Mockito.timeout(2000)).abort();
		Assertions.assertNull(chart.getWebSocket());
		Assertions.assertTrue(chart.isRunning());
		Assertions.assertEquals("/Waiting", chart.getCurrentState().getPath());
		chart.cancel(true);
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void connection_failure_after_leaving_connecting_state_is_ignored() throws Exception {
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		utils.statechart.Signal leave = new utils.statechart.Signal() { };
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error")
							.on(leave, utils.statechart.Transitions.to("/Waiting")));
		chart.addState(new utils.statechart.DefaultState<>("/Waiting", ctx));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		chart.handleSignal(leave);

		cc.m_future.completeExceptionally(new java.net.ConnectException("refused"));
		Thread.sleep(200);

		Assertions.assertTrue(chart.isRunning());
		Assertions.assertEquals("/Waiting", chart.getCurrentState().getPath());
		chart.cancel(true);
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void abandoned_connection_is_aborted_even_if_close_frame_is_stuck() throws Exception {
		// 차트가 끝난 뒤 완료된 연결의 close 전송이 끝나지 않아도 close 시간 제한 뒤에 끊는다.
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		chart.setCloseTimeout(Duration.ofMillis(50));
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		chart.cancel(true);

		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenReturn(new CompletableFuture<>());
		cc.m_future.complete(ws);

		verify(ws, org.mockito.Mockito.timeout(2000)).abort();
	}

	// ---- closeAsync와 송신 단축 메소드 ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void closeConnectionAsync_completes_when_server_replies() throws Exception {
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			WebSocketStateChart<TestCtx> chart = startConnectedChart(server, null, null);
			chart.setCloseTimeout(Duration.ofSeconds(5));

			long started = System.nanoTime();
			chart.closeConnectionAsync(WebSocket.NORMAL_CLOSURE, "bye").get(5, java.util.concurrent.TimeUnit.SECONDS);
			long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

			Assertions.assertTrue(elapsedMillis < 3000, "elapsed=" + elapsedMillis);
			Assertions.assertEquals("bye", serverListener.m_closeReason);
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void close_aborts_when_peer_does_not_reply() throws Exception {
		// sendClose()는 출력만 닫는다. 상대가 응답하지 않으면 close 시간 제한 뒤에 연결을 끊어야 연결이 남지 않는다.
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setCloseTimeout(Duration.ofMillis(100));
		WebSocket ws = newMockWebSocket();
		chart.setWebSocket(ws);

		chart.closeConnection();						// 전송만 기다리고 바로 반환한다.
		verify(ws, org.mockito.Mockito.never()).abort();
		verify(ws, org.mockito.Mockito.timeout(2000)).abort();
	}

	@Test
	public void closeConnectionAsync_without_websocket_is_already_done() {
		Assertions.assertTrue(newChart().closeConnectionAsync().isDone());
	}

	@Test
	public void single_message_send_shortcuts_send_last_fragment() throws Exception {
		WebSocketStateChart<TestCtx> chart = startWaitingChart(newChart());
		WebSocket ws = newMockWebSocket();
		when(ws.sendText(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
			.thenReturn(CompletableFuture.completedFuture(ws));
		when(ws.sendBinary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
			.thenReturn(CompletableFuture.completedFuture(ws));
		chart.setWebSocket(ws);

		chart.sendText("a");
		chart.sendTextSync("b");
		chart.sendBinary(new byte[] { 1 });
		chart.sendBinarySync(new byte[] { 2 });

		verify(ws).sendText("a", true);
		verify(ws).sendText("b", true);
		verify(ws, org.mockito.Mockito.times(2)).sendBinary(org.mockito.ArgumentMatchers.any(), eq(true));
		chart.cancel(true);
	}

	// ---- 접속 시도별 listener ----

	private WebSocket.Listener captureAttemptListener(ControlledClient cc) {
		org.mockito.ArgumentCaptor<WebSocket.Listener> captor = org.mockito.ArgumentCaptor.forClass(WebSocket.Listener.class);
		verify(cc.m_builder).buildAsync(org.mockito.ArgumentMatchers.any(), captor.capture());
		return captor.getValue();
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void error_on_abandoned_attempt_does_not_fail_chart() throws Exception {
		// 접속 시작 → onOpen → 다른 상태로 이동 → 이전 연결의 onError. 폐기된 시도의 오류는 차트에 전달하지 않는다.
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		utils.statechart.Signal leave = new utils.statechart.Signal() { };
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error")
							.on(leave, utils.statechart.Transitions.to("/Waiting")));
		chart.addState(new utils.statechart.DefaultState<>("/Waiting", ctx));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		WebSocket.Listener listener = captureAttemptListener(cc);
		WebSocket ws = newMockWebSocket();

		listener.onOpen(ws);
		chart.handleSignal(leave);
		listener.onError(ws, new java.io.IOException("connection reset"));

		Assertions.assertTrue(chart.isRunning());
		Assertions.assertEquals("/Waiting", chart.getCurrentState().getPath());
		Assertions.assertFalse(chart.listener().peerClosed().toCompletableFuture().isDone());
		chart.cancel(true);
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void error_on_current_attempt_before_registration_is_delivered() throws Exception {
		// 현재 시도의 등록 전 오류(접속 직후 끊김 등)는 차트에 전달해 대체 종료로 실패시킨다.
		ControlledClient cc = new ControlledClient();
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		WebSocket.Listener listener = captureAttemptListener(cc);
		WebSocket ws = newMockWebSocket();
		java.io.IOException error = new java.io.IOException("connection reset");

		listener.onOpen(ws);
		listener.onError(ws, error);

		Assertions.assertSame(error, chart.waitForFinished(2, java.util.concurrent.TimeUnit.SECONDS).getFailureCause());
		// 등록된 연결의 오류가 아니므로 상대 종료 통지는 완료하지 않는다.
		Assertions.assertFalse(chart.listener().peerClosed().toCompletableFuture().isDone());
	}

	// ---- 등록 직후 상태를 떠난 연결, 설정 시점, 빌더 설정 ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void connection_registered_while_leaving_state_does_not_cancel_chart() throws Exception {
		// 등록과 재확인 사이에 상태를 떠난 경우. 연결을 닫을 때 오는 상대의 close 응답이 실행 중인 차트를
		// 대체 종료로 취소하면 안 된다.
		ControlledClient cc = new ControlledClient();
		utils.statechart.Signal leave = new utils.statechart.Signal() { };
		WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx("ws://localhost:8080/test")) {
			@Override
			protected void setWebSocket(WebSocket ws) {
				super.setWebSocket(ws);
				handleSignal(leave);			// 등록 직후 다른 상태로 간다.
			}
		};
		chart.setHttpClient(cc.m_client);
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error")
							.on(leave, utils.statechart.Transitions.to("/Waiting")));
		chart.addState(new utils.statechart.DefaultState<>("/Waiting", ctx));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();
		WebSocket.Listener listener = captureAttemptListener(cc);
		WebSocket ws = newMockWebSocket();

		cc.m_future.complete(ws);
		verify(ws, org.mockito.Mockito.timeout(2000))
			.sendClose(WebSocket.NORMAL_CLOSURE, States.WebSocketConnectingState.STATE_LEFT_CLOSE_REASON);
		verify(ws, org.mockito.Mockito.timeout(2000)).abort();

		// 상대의 close 응답이 와도 차트에 전달하지 않는다.
		listener.onClose(ws, WebSocket.NORMAL_CLOSURE, "");
		chart.listener().onClose(ws, WebSocket.NORMAL_CLOSURE, "");
		Assertions.assertTrue(chart.isRunning());
		Assertions.assertEquals("/Waiting", chart.getCurrentState().getPath());
		chart.cancel(true);
	}

	@Test
	public void connection_settings_are_rejected_after_start() {
		WebSocketStateChart<TestCtx> chart = startWaitingChart(newChart());

		Assertions.assertThrows(IllegalStateException.class, () -> chart.setPingInterval(Duration.ofSeconds(1)));
		Assertions.assertThrows(IllegalStateException.class, () -> chart.setPongTimeout(Duration.ofSeconds(1)));
		Assertions.assertThrows(IllegalStateException.class,
								() -> chart.setHttpClient(mock(java.net.http.HttpClient.class)));
		Assertions.assertThrows(IllegalStateException.class, () -> chart.setConnectTimeout(Duration.ofSeconds(1)));
		Assertions.assertThrows(IllegalStateException.class, () -> chart.setWebSocketBuilderCustomizer(b -> { }));
		// 실행 중 변경이 의미 있는 설정은 허용한다.
		Assertions.assertDoesNotThrow(() -> chart.setCloseTimeout(Duration.ofSeconds(1)));
		Assertions.assertDoesNotThrow(() -> chart.setMaxTextMessageLength(10));
		chart.cancel(true);
	}

	@Test
	public void builder_customizer_is_applied_before_connecting() {
		ControlledClient cc = new ControlledClient();
		when(cc.m_builder.header(anyString(), anyString())).thenReturn(cc.m_builder);
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setHttpClient(cc.m_client);
		chart.setWebSocketBuilderCustomizer(b -> b.header("Authorization", "Bearer token"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setWebSocketBuilderCustomizer(null));
		TestCtx ctx = chart.getContext();
		chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
		chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
		chart.addState(new States.FailedState<>("/Error", ctx));
		chart.setInitialState("/Connect");
		chart.start();

		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(cc.m_builder);
		inOrder.verify(cc.m_builder).header("Authorization", "Bearer token");
		inOrder.verify(cc.m_builder).buildAsync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		chart.cancel(true);
	}

	// ---- 실제 서버와의 keep-alive ----

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void keep_alive_with_answering_server_keeps_chart_running() throws Exception {
		// OkHttp 서버는 ping에 pong으로 자동 응답한다. 감시가 오탐하지 않아야 한다.
		ClosingServer serverListener = new ClosingServer();
		try ( okhttp3.mockwebserver.MockWebServer server = new okhttp3.mockwebserver.MockWebServer() ) {
			server.enqueue(new okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(serverListener));
			server.start();
			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(url));
			chart.setPingInterval(Duration.ofMillis(50));
			chart.setPongTimeout(Duration.ofMillis(300));
			TestCtx ctx = chart.getContext();
			java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
			chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
			chart.addState(new utils.statechart.DefaultState<TestCtx>("/Ready", ctx) {
				@Override
				public void enter() {
					entered.countDown();
				}
			});
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Connect");
			chart.start();
			Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));

			Thread.sleep(800);
			Assertions.assertTrue(chart.isRunning(), "chart=" + chart);
			chart.cancel(true);
		}
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
	public void keep_alive_with_silent_server_fails_chart() throws Exception {
		// ping에 응답하지 않는 서버. pong 시간 초과로 차트가 실패해야 한다.
		try ( RawWebSocketServer server = new RawWebSocketServer(true, null) ) {
			WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(new TestCtx(server.url()));
			chart.setPingInterval(Duration.ofMillis(50));
			chart.setPongTimeout(Duration.ofMillis(200));
			chart.setCloseTimeout(Duration.ofMillis(200));
			TestCtx ctx = chart.getContext();
			chart.addState(new States.WebSocketConnectingState<>("/Connect", ctx, "/Ready", "/Error"));
			chart.addState(new utils.statechart.DefaultState<>("/Ready", ctx));
			chart.addState(new States.FailedState<>("/Error", ctx));
			chart.setInitialState("/Connect");
			chart.start();

			Throwable cause = chart.waitForFinished(5, java.util.concurrent.TimeUnit.SECONDS).getFailureCause();
			Assertions.assertInstanceOf(utils.RuntimeTimeoutException.class, cause);
		}
	}

	// ---- 밀리초 단위 시간 값 검증 ----

	@Test
	public void millisecond_durations_reject_sub_millisecond_and_huge_values() {
		WebSocketStateChart<TestCtx> chart = newChart();
		Duration subMillis = Duration.ofNanos(500_000);
		Duration huge = java.time.temporal.ChronoUnit.FOREVER.getDuration();

		// 1ms 미만은 0ms가 되어 pong 감시가 즉시 만료되고, ping 예약이 실패하고, close가 바로 끊긴다.
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setPingInterval(subMillis));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setPongTimeout(subMillis));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setCloseTimeout(subMillis));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setPingInterval(huge));
		Assertions.assertThrows(IllegalArgumentException.class, () -> chart.setCloseTimeout(huge));
		Assertions.assertDoesNotThrow(() -> chart.setPingInterval(Duration.ofMillis(1)));
		Assertions.assertDoesNotThrow(() -> chart.setPongTimeout(Duration.ofMillis(1)));
		Assertions.assertDoesNotThrow(() -> chart.setCloseTimeout(Duration.ofMillis(1)));
	}
}
