package utils.websocket;

import static org.mockito.Mockito.mock;

import org.junit.Assert;
import org.junit.Test;

import utils.statechart.StateChart;

/**
 * {@link WebSocketContext}의 라이프사이클 계약을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketContextTest {

	private static class TestCtx extends WebSocketContext<TestCtx> {
		TestCtx(String url) { super(url); }
	}

	// ---- 생성자 ----

	@Test(expected = IllegalArgumentException.class)
	public void constructor_null_serverUrl_rejected() {
		new TestCtx(null);
	}

	@Test
	public void getServerUrl_returns_provided_value() {
		Assert.assertEquals("ws://localhost:8080/path",
							new TestCtx("ws://localhost:8080/path").getServerUrl());
	}

	// ---- StateChart 등록 ----

	@Test
	public void getStateChart_returns_null_before_chart_attached() {
		Assert.assertNull(new TestCtx("ws://localhost:8080").getStateChart());
	}

	@Test
	public void chart_construction_attaches_itself_to_context() {
		TestCtx ctx = new TestCtx("ws://localhost:8080");
		WebSocketStateChart<TestCtx> chart = new WebSocketStateChart<>(ctx);

		Assert.assertSame(chart, ctx.getStateChart());
	}

	@Test(expected = IllegalArgumentException.class)
	public void setStateChart_null_rejected() {
		new TestCtx("ws://localhost:8080").setStateChart(null);
	}

	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unchecked")
	public void setStateChart_non_websocket_chart_rejected() {
		// Mockito로 만든 일반 StateChart는 WebSocketStateChart의 인스턴스가 아니므로 거부되어야 함.
		TestCtx ctx = new TestCtx("ws://localhost:8080");
		StateChart<TestCtx> mocked = mock(StateChart.class);
		ctx.setStateChart(mocked);
	}

	@Test(expected = IllegalStateException.class)
	public void setStateChart_double_set_rejected() {
		// 첫 차트가 자동 등록된 컨텍스트에 다른 차트를 다시 set하면 IllegalStateException.
		TestCtx ctx1 = new TestCtx("ws://localhost:8080");
		new WebSocketStateChart<>(ctx1);   // ctx1에 자동 등록

		TestCtx ctx2 = new TestCtx("ws://other");
		WebSocketStateChart<TestCtx> chart2 = new WebSocketStateChart<>(ctx2);

		ctx1.setStateChart(chart2);
	}
}
