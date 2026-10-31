package utils.statechart;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link AbstractStateContext}의 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractStateContextTest {
	private static class TestCtx extends AbstractStateContext<TestCtx> { }

	@Test
	public void chart_constructor_attaches_itself() {
		TestCtx ctx = new TestCtx();
		Assertions.assertNull(ctx.getStateChart());

		StateChart<TestCtx> chart = new StateChart<>(ctx);

		Assertions.assertSame(chart, ctx.getStateChart());
	}

	@Test
	public void second_chart_rejected() {
		TestCtx ctx = new TestCtx();
		StateChart<TestCtx> first = new StateChart<>(ctx);

		Assertions.assertThrows(IllegalStateException.class, () -> new StateChart<>(ctx));
		Assertions.assertSame(first, ctx.getStateChart());
	}

	@Test
	public void null_chart_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new TestCtx().setStateChart(null));
	}
}
