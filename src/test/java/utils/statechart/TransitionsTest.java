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

	// ---- to(path, action) ----

	@Test
	public void to_with_action_returns_transition_with_target_and_action() {
		AtomicBoolean invoked = new AtomicBoolean();
		AtomicReference<Signal> seenSignal = new AtomicReference<>();
		TransitionAction<TestCtx> action = (ctx, sig) -> {
			invoked.set(true);
			seenSignal.set(sig);
		};
		Transition<TestCtx> t = Transitions.to("targetPath", action);

		Assertions.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assertions.assertFalse(t.isInternal());

		t.execute(m_ctx, m_signal);
		Assertions.assertTrue(invoked.get());
		Assertions.assertSame(m_signal, seenSignal.get());
	}

	@Test
	public void to_with_action_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.to(null, (c, s) -> {});
			});
	}

	@Test
	public void to_with_action_null_action_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.to("path", (TransitionAction<TestCtx>)null);
			});
	}

	// ---- to(path) ----

	@Test
	public void to_returns_transition_with_target_and_no_op_execute() {
		Transition<TestCtx> t = Transitions.to("targetPath");

		Assertions.assertEquals(Optional.of("targetPath"), t.getTargetStatePath());
		Assertions.assertFalse(t.isInternal());

		t.execute(m_ctx, m_signal);   // 예외 발생 없이 통과해야 함
	}

	@Test
	public void to_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Transitions.to((String)null);
			});
	}

	// ---- internal ----

	@Test
	public void internal_has_no_target_and_runs_action() {
		AtomicReference<Signal> received = new AtomicReference<>();
		Transition<TestCtx> t = Transitions.internal((ctx, sig) -> received.set(sig));

		Assertions.assertTrue(t.getTargetStatePath().isEmpty());
		Assertions.assertTrue(t.isInternal());

		t.execute(m_ctx, m_signal);
		Assertions.assertSame(m_signal, received.get());
	}

	@Test
	public void internal_null_action_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Transitions.<TestCtx>internal(null));
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
		Assertions.assertTrue(t.isInternal());

		t.execute(m_ctx, m_signal);   // stay()의 execute는 no-op
	}

	// ---- fail ----

	@Test
	public void fail_has_target_and_is_not_self_transition() {
		Transition<TestCtx> t = Transitions.fail("/Error", sig -> new IllegalStateException());

		Assertions.assertEquals(Optional.of("/Error"), t.getTargetStatePath());
		Assertions.assertFalse(t.isInternal());
		Assertions.assertTrue(t.toString().contains("/Error"));
	}

	@Test
	public void fail_null_arguments_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> Transitions.<TestCtx>fail(null, sig -> new IllegalStateException()));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Transitions.<TestCtx>fail("/Error", null));
	}

	@Test
	public void fail_without_chart_throws() {
		Transition<TestCtx> t = Transitions.fail("/Error", sig -> new IllegalStateException());

		// m_ctx는 어떤 차트에도 연결되어 있지 않다.
		Assertions.assertThrows(IllegalStateException.class, () -> t.execute(m_ctx, m_signal));
	}

	// ---- toString ----

	@Test
	public void toString_includes_target_path() {
		Assertions.assertTrue(Transitions.<TestCtx>to("X").toString().contains("X"));
		Assertions.assertEquals("stay", Transitions.<TestCtx>stay().toString());
	}
}
