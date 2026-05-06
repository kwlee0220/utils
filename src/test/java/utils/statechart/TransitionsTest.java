package utils.statechart;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link Transitions} 정적 팩토리와 그 결과 {@link Transition} 인스턴스의 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TransitionsTest {

	private static class TestCtx implements StateContext<TestCtx> {
		private StateChart<TestCtx> m_chart;
		@Override public StateChart<TestCtx> getStateChart() { return m_chart; }
		@Override public void setStateChart(StateChart<TestCtx> m) { m_chart = m; }
	}

	private final TestCtx m_ctx = new TestCtx();
	private final Signal m_signal = new Signal() {};

	// ---- create ----

	@Test
	public void create_returns_transition_with_target_and_action() {
		AtomicBoolean invoked = new AtomicBoolean();
		AtomicReference<Signal> seenSignal = new AtomicReference<>();
		TransitionAction<TestCtx> action = (ctx, sig) -> {
			invoked.set(true);
			seenSignal.set(sig);
		};
		Transition<TestCtx> t = Transitions.create("targetPath", action);

		Assert.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assert.assertFalse(t.isSelfTransition());

		t.execute(m_ctx, m_signal);
		Assert.assertTrue(invoked.get());
		Assert.assertSame(m_signal, seenSignal.get());
	}

	@Test(expected = IllegalArgumentException.class)
	public void create_null_path_rejected() {
		Transitions.create(null, (c, s) -> {});
	}

	@Test(expected = IllegalArgumentException.class)
	public void create_null_action_rejected() {
		Transitions.create("path", null);
	}

	// ---- noop ----

	@Test
	public void noop_returns_transition_with_target_and_no_op_execute() {
		Transition<TestCtx> t = Transitions.noop("targetPath");

		Assert.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assert.assertFalse(t.isSelfTransition());

		t.execute(m_ctx, m_signal);   // 예외 발생 없이 통과해야 함
	}

	@Test(expected = IllegalArgumentException.class)
	public void noop_null_path_rejected() {
		Transitions.noop(null);
	}

	// ---- stay ----

	@Test
	public void stay_returns_singleton() {
		Transition<TestCtx> t1 = Transitions.stay();
		Transition<TestCtx> t2 = Transitions.stay();
		Assert.assertSame(t1, t2);
	}

	@Test
	public void stay_is_self_transition() {
		Transition<TestCtx> t = Transitions.stay();

		Assert.assertTrue(t.getTargetStatePath().isEmpty());
		Assert.assertTrue(t.isSelfTransition());

		t.execute(m_ctx, m_signal);   // self-transition execute는 no-op
	}

	// ---- toString ----

	@Test
	public void toString_includes_target_path() {
		Assert.assertTrue(Transitions.<TestCtx>noop("X").toString().contains("X"));
		Assert.assertEquals("stay", Transitions.<TestCtx>stay().toString());
	}
}
