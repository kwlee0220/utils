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
 * 실제 WebSocket 연결이나 차트 실행이 필요한 시나리오 (송신 성공 경로, fall-back transition 등)는
 * 외부 의존성이 너무 커서 통합 테스트로 분리한다. 본 테스트는 단위 검증만 다룬다.
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
	public void getWebSocketListener_returns_non_null() {
		Assertions.assertNotNull(newChart().getWebSocketListener());
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
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
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
		Assertions.assertDoesNotThrow(() -> newChart().close());
	}

	@Test
	public void close_sends_normal_closure() {
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = newMockWebSocket();
		chart.setWebSocket(ws);

		chart.close();

		verify(ws).sendClose(eq(WebSocket.NORMAL_CLOSURE), anyString());
	}

	@Test
	public void close_repeated_is_safe() {
		// best-effort 시맨틱이므로 반복 호출이 안전해야 한다.
		// (종료 상태의 enter()와 차트 종료 훅이 연달아 호출하는 경우가 실제로 발생한다)
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setWebSocket(newMockWebSocket());

		Assertions.assertDoesNotThrow(() -> {
			chart.close();
			chart.close();
		});
	}

	@Test
	public void close_is_quiet_when_sendClose_throws() {
		// close 실패가 호출자에게 전파되면 종료 상태 진입이나 종료 통지가 실패로 뒤집힌다.
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenThrow(new IllegalStateException("already closed"));
		chart.setWebSocket(ws);

		Assertions.assertDoesNotThrow(() -> chart.close());
	}

	// ---- 차트 종료 시 자동 close ----

	@Test
	public void fail_closes_websocket() {
		// 종료 상태를 거치지 않는 fail()에서도 연결이 닫혀야 한다.
		// (ErrorMessage fall-back이 밟는 경로다)
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = newMockWebSocket();
		chart.notifyStarted();
		chart.setWebSocket(ws);

		chart.fail(new IllegalStateException("boom"));

		verify(ws).sendClose(WebSocket.NORMAL_CLOSURE, "closure due to a error");
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
}
