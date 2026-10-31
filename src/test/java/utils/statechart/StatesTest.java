package utils.statechart;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 상태 구현체들의 동작을 검증한다:
 * {@link AbstractState}, {@link TerminalState}, {@link FailureState},
 * {@link TriggeredState}, {@link DefaultCompositeState}.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StatesTest {

	private static class TestCtx implements StateContext<TestCtx> {
		private StateChart<TestCtx> m_chart;
		@Override public StateChart<TestCtx> getStateChart() { return m_chart; }
		@Override public void setStateChart(StateChart<TestCtx> m) { m_chart = m; }
	}

	private final TestCtx m_ctx = new TestCtx();

	// ---- AbstractState (TerminalState를 통해 검증) ----

	@Test
	public void abstractState_path_context_segments() {
		TerminalState<TestCtx> s = new TerminalState<>("/a/b/c", m_ctx);

		Assertions.assertEquals("/a/b/c", s.getPath());
		Assertions.assertSame(m_ctx, s.getContext());
		Assertions.assertEquals(List.of("a", "b", "c"), s.getPathSegments());
		Assertions.assertTrue(s.toString().contains("/a/b/c"));
	}

	@Test
	public void abstractState_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TerminalState<TestCtx>(null, m_ctx);
			});
	}

	@Test
	public void abstractState_null_context_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TerminalState<TestCtx>("x", null);
			});
	}

	// ---- TerminalState ----

	@Test
	public void terminalState_exit_throws() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new TerminalState<>("x", m_ctx).exit();
			});
	}

	@Test
	public void terminalState_selectTransition_throws() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new TerminalState<>("x", m_ctx).selectTransition(new Signal() {});
			});
	}

	@Test
	public void terminalState_enter_is_noop() {
		new TerminalState<>("x", m_ctx).enter();   // 예외 발생 없이 통과해야 함
	}

	// ---- FailureState ----

	@Test
	public void failureState_failureCause_default_null() {
		Assertions.assertNull(new FailureState<>("err", m_ctx).getFailureCause());
	}

	@Test
	public void failureState_failureCause_set_get() {
		FailureState<TestCtx> s = new FailureState<>("err", m_ctx);
		Throwable cause = new RuntimeException("boom");

		s.setFailureCause(cause);

		Assertions.assertSame(cause, s.getFailureCause());
	}

	@Test
	public void failureState_inherits_sink_exit() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new FailureState<>("err", m_ctx).exit();
			});
	}

	// ---- TriggeredState ----

	@Test
	public void triggeredState_matching_signal_returns_transition() {
		Signal trigger = new Signal() {};
		Transition<TestCtx> t = Transitions.to("next");
		TriggeredState<TestCtx> s = new TriggeredState<>("x", m_ctx, trigger, t);

		Optional<Transition<TestCtx>> result = s.selectTransition(trigger);
		Assertions.assertTrue(result.isPresent());
		Assertions.assertSame(t, result.get());
	}

	@Test
	public void triggeredState_non_matching_signal_returns_empty() {
		Signal trigger = new Signal() {};
		Signal other = new Signal() {};
		TriggeredState<TestCtx> s = new TriggeredState<>("x", m_ctx, trigger,
									Transitions.<TestCtx>to("next"));

		Assertions.assertTrue(s.selectTransition(other).isEmpty());
	}

	@Test
	public void triggeredState_null_signal_to_select_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TriggeredState<>("x", m_ctx, new Signal() {},
								Transitions.<TestCtx>to("y")).selectTransition(null);
			});
	}

	@Test
	public void triggeredState_null_trigger_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TriggeredState<>("x", m_ctx, null, Transitions.<TestCtx>to("y"));
			});
	}

	@Test
	public void triggeredState_null_static_transition_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TriggeredState<>("x", m_ctx, new Signal() {}, (Transition<TestCtx>) null);
			});
	}

	@Test
	public void triggeredState_null_supplier_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TriggeredState<>("x", m_ctx, new Signal() {},
								(Supplier<Transition<TestCtx>>) null);
			});
	}

	@Test
	public void triggeredState_supplier_returning_null_fails_with_state_path() {
		Signal trigger = new Signal() {};
		TriggeredState<TestCtx> s = new TriggeredState<>("/X", m_ctx, trigger, () -> null);

		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
															() -> s.selectTransition(trigger));
		Assertions.assertTrue(e.getMessage().contains("/X"), e.getMessage());
	}

	@Test
	public void triggeredState_supplier_invoked_each_match() {
		Signal trigger = new Signal() {};
		AtomicInteger calls = new AtomicInteger();
		Transition<TestCtx> t = Transitions.to("next");
		TriggeredState<TestCtx> s = new TriggeredState<>("x", m_ctx, trigger, () -> {
			calls.incrementAndGet();
			return t;
		});

		s.selectTransition(trigger);
		s.selectTransition(trigger);

		Assertions.assertEquals(2, calls.get());
	}

	@Test
	public void triggeredState_non_matching_does_not_invoke_supplier() {
		Signal trigger = new Signal() {};
		Signal other = new Signal() {};
		AtomicInteger calls = new AtomicInteger();
		TriggeredState<TestCtx> s = new TriggeredState<>("x", m_ctx, trigger, () -> {
			calls.incrementAndGet();
			return Transitions.<TestCtx>to("next");
		});

		s.selectTransition(other);

		Assertions.assertEquals(0, calls.get());
	}

	// ---- DefaultCompositeState ----
	// 계층 전이 동작은 StateChartTest의 "복합 상태" 테스트에서 검증한다.

	@Test
	public void compositeState_holds_initial_state_path() {
		DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("/comp", m_ctx, "/comp/child");

		Assertions.assertEquals("/comp", comp.getPath());
		Assertions.assertEquals("/comp/child", comp.getInitialStatePath());
	}

	@Test
	public void compositeState_null_initial_state_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new DefaultCompositeState<>("/comp", m_ctx, null));
	}

	@Test
	public void compositeState_selects_no_transition_by_default() {
		DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("/comp", m_ctx, "/comp/child");

		Assertions.assertTrue(comp.selectTransition(new Signal() {}).isEmpty());
	}
}
