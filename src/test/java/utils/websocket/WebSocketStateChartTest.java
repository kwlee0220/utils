package utils.websocket;

import static org.mockito.Mockito.mock;

import java.net.http.WebSocket;
import java.time.Duration;

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
}
