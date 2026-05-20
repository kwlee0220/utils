package utils.statechart;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

		Assertions.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assertions.assertFalse(t.isSelfTransition());

		t.execute(m_ctx, m_signal);
		Assertions.assertTrue(invoked.get());
		Assertions.assertSame(m_signal, seenSignal.get());
	}

	@Test
	public void create_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.create(null, (c, s) -> {});
			});
	}

	@Test
	public void create_null_action_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.create("path", null);
			});
	}

	// ---- noop ----

	@Test
	public void noop_returns_transition_with_target_and_no_op_execute() {
		Transition<TestCtx> t = Transitions.noop("targetPath");

		Assertions.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assertions.assertFalse(t.isSelfTransition());

		t.execute(m_ctx, m_signal);   // 예외 발생 없이 통과해야 함
	}

	@Test
	public void noop_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.noop(null);
			});
	}

	// ---- stay ----

	@Test
	public void stay_returns_singleton() {
		Transition<TestCtx> t1 = Transitions.stay();
		Transition<TestCtx> t2 = Transitions.stay();
		Assertions.assertSame(t1, t2);
	}

	@Test
	public void stay_is_self_transition() {
		Transition<TestCtx> t = Transitions.stay();

		Assertions.assertTrue(t.getTargetStatePath().isEmpty());
		Assertions.assertTrue(t.isSelfTransition());

		t.execute(m_ctx, m_signal);   // self-transition execute는 no-op
	}

	// ---- toString ----

	@Test
	public void toString_includes_target_path() {
		Assertions.assertTrue(Transitions.<TestCtx>noop("X").toString().contains("X"));
		Assertions.assertEquals("stay", Transitions.<TestCtx>stay().toString());
	}
}
