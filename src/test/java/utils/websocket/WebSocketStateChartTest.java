package utils.websocket;

import static org.mockito.Mockito.mock;

import java.net.http.WebSocket;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;

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
		Assert.assertNotNull(newChart().getWebSocketListener());
	}

	// ---- setWebSocket / getWebSocket ----

	@Test
	public void getWebSocket_null_before_setWebSocket() {
		Assert.assertNull(newChart().getWebSocket());
	}

	@Test
	public void setWebSocket_then_getWebSocket() {
		WebSocketStateChart<TestCtx> chart = newChart();
		WebSocket ws = mock(WebSocket.class);
		chart.setWebSocket(ws);

		Assert.assertSame(ws, chart.getWebSocket());
	}

	@Test(expected = IllegalArgumentException.class)
	public void setWebSocket_null_rejected() {
		newChart().setWebSocket(null);
	}

	@Test(expected = IllegalStateException.class)
	public void setWebSocket_double_call_rejected() {
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setWebSocket(mock(WebSocket.class));
		chart.setWebSocket(mock(WebSocket.class));
	}

	// ---- setPingInterval ----

	@Test(expected = IllegalArgumentException.class)
	public void setPingInterval_null_rejected() {
		newChart().setPingInterval(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setPingInterval_zero_rejected() {
		newChart().setPingInterval(Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setPingInterval_negative_rejected() {
		newChart().setPingInterval(Duration.ofMillis(-1));
	}

	@Test
	public void setPingInterval_positive_accepted() {
		newChart().setPingInterval(Duration.ofSeconds(5));
	}

	// ---- setPongTimeout ----

	@Test(expected = IllegalArgumentException.class)
	public void setPongTimeout_null_rejected() {
		newChart().setPongTimeout(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setPongTimeout_zero_rejected() {
		newChart().setPongTimeout(Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setPongTimeout_negative_rejected() {
		newChart().setPongTimeout(Duration.ofMillis(-1));
	}

	@Test
	public void setPongTimeout_positive_accepted() {
		newChart().setPongTimeout(Duration.ofSeconds(2));
	}

	// ---- sendText ----

	@Test(expected = IllegalArgumentException.class)
	public void sendText_null_text_rejected() {
		newChart().sendText(null, true);
	}

	@Test(expected = IllegalStateException.class)
	public void sendText_before_chart_running_rejected() {
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setWebSocket(mock(WebSocket.class));   // 연결은 등록했지만 차트는 NOT_STARTED
		chart.sendText("hello", true);
	}

	// ---- sendBinary ----

	@Test(expected = IllegalArgumentException.class)
	public void sendBinary_null_rejected() {
		newChart().sendBinary(null, true);
	}

	@Test(expected = IllegalStateException.class)
	public void sendBinary_before_chart_running_rejected() {
		WebSocketStateChart<TestCtx> chart = newChart();
		chart.setWebSocket(mock(WebSocket.class));
		chart.sendBinary(new byte[] { 1, 2, 3 }, true);
	}

	// ---- handleSignal ----

	@Test(expected = IllegalArgumentException.class)
	public void handleSignal_null_rejected() {
		newChart().handleSignal(null);
	}
}
