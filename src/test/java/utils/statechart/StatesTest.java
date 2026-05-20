package utils.statechart;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 상태 구현체들의 동작을 검증한다:
 * {@link AbstractState}, {@link SinkState}, {@link ExceptionState},
 * {@link SingleOutState}, {@link DefaultCompositeState}.
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

	// ---- AbstractState (SinkState를 통해 검증) ----

	@Test
	public void abstractState_path_context_segments() {
		SinkState<TestCtx> s = new SinkState<>("a.b.c", m_ctx);

		Assertions.assertEquals("a.b.c", s.getPath());
		Assertions.assertSame(m_ctx, s.getContext());
		Assertions.assertEquals(List.of("a", "b", "c"), s.getPathSegments());
		Assertions.assertTrue(s.toString().contains("a.b.c"));
	}

	@Test
	public void abstractState_null_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new SinkState<TestCtx>(null, m_ctx);
			});
	}

	@Test
	public void abstractState_null_context_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new SinkState<TestCtx>("x", null);
			});
	}

	// ---- SinkState ----

	@Test
	public void sinkState_exit_throws() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new SinkState<>("x", m_ctx).exit();
			});
	}

	@Test
	public void sinkState_selectTransition_throws() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new SinkState<>("x", m_ctx).selectTransition(new Signal() {});
			});
	}

	@Test
	public void sinkState_enter_is_noop() {
		new SinkState<>("x", m_ctx).enter();   // 예외 발생 없이 통과해야 함
	}

	// ---- ExceptionState ----

	@Test
	public void exceptionState_failureCause_default_null() {
		Assertions.assertNull(new ExceptionState<>("err", m_ctx).getFailureCause());
	}

	@Test
	public void exceptionState_failureCause_set_get() {
		ExceptionState<TestCtx> s = new ExceptionState<>("err", m_ctx);
		Throwable cause = new RuntimeException("boom");

		s.setFailureCause(cause);

		Assertions.assertSame(cause, s.getFailureCause());
	}

	@Test
	public void exceptionState_inherits_sink_exit() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> {
			new ExceptionState<>("err", m_ctx).exit();
			});
	}

	// ---- SingleOutState ----

	@Test
	public void singleOutState_matching_signal_returns_transition() {
		Signal trigger = new Signal() {};
		Transition<TestCtx> t = Transitions.noop("next");
		SingleOutState<TestCtx> s = new SingleOutState<>("x", m_ctx, trigger, t);

		Optional<Transition<TestCtx>> result = s.selectTransition(trigger);
		Assertions.assertTrue(result.isPresent());
		Assertions.assertSame(t, result.get());
	}

	@Test
	public void singleOutState_non_matching_signal_returns_empty() {
		Signal trigger = new Signal() {};
		Signal other = new Signal() {};
		SingleOutState<TestCtx> s = new SingleOutState<>("x", m_ctx, trigger,
									Transitions.<TestCtx>noop("next"));

		Assertions.assertTrue(s.selectTransition(other).isEmpty());
	}

	@Test
	public void singleOutState_null_signal_to_select_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new SingleOutState<>("x", m_ctx, new Signal() {},
								Transitions.<TestCtx>noop("y")).selectTransition(null);
			});
	}

	@Test
	public void singleOutState_null_trigger_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new SingleOutState<>("x", m_ctx, null, Transitions.<TestCtx>noop("y"));
			});
	}

	@Test
	public void singleOutState_null_supplier_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new SingleOutState<>("x", m_ctx, new Signal() {},
								(Supplier<Transition<TestCtx>>) null);
			});
	}

	@Test
	public void singleOutState_supplier_invoked_each_match() {
		Signal trigger = new Signal() {};
		AtomicInteger calls = new AtomicInteger();
		Transition<TestCtx> t = Transitions.noop("next");
		SingleOutState<TestCtx> s = new SingleOutState<>("x", m_ctx, trigger, () -> {
			calls.incrementAndGet();
			return t;
		});

		s.selectTransition(trigger);
		s.selectTransition(trigger);

		Assertions.assertEquals(2, calls.get());
	}

	@Test
	public void singleOutState_non_matching_does_not_invoke_supplier() {
		Signal trigger = new Signal() {};
		Signal other = new Signal() {};
		AtomicInteger calls = new AtomicInteger();
		SingleOutState<TestCtx> s = new SingleOutState<>("x", m_ctx, trigger, () -> {
			calls.incrementAndGet();
			return Transitions.<TestCtx>noop("next");
		});

		s.selectTransition(other);

		Assertions.assertEquals(0, calls.get());
	}

	// ---- DefaultCompositeState ----

	@Test
	public void compositeState_enter_drives_initial_state_lifecycle() {
		AtomicInteger childEnter = new AtomicInteger();
		AtomicInteger childExit = new AtomicInteger();
		State<TestCtx> child = new AbstractState<TestCtx>("child", m_ctx) {
			@Override public void enter() { childEnter.incrementAndGet(); }
			@Override public void exit() { childExit.incrementAndGet(); }
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal s) {
				return Optional.empty();
			}
		};
		DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("comp", m_ctx);
		comp.setInitialState(child);

		Assertions.assertSame(child, comp.getInitialState());
		Assertions.assertNull(comp.getCurrentState());

		comp.enter();
		Assertions.assertEquals(1, childEnter.get());
		Assertions.assertSame(child, comp.getCurrentState());

		comp.exit();
		Assertions.assertEquals(1, childExit.get());
		Assertions.assertNull(comp.getCurrentState());
	}

	@Test
	public void compositeState_double_enter_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("comp", m_ctx);
			comp.setInitialState(new SinkState<>("child", m_ctx));   // SinkState.enter()는 no-op
			comp.enter();
			comp.enter();
			});
	}

	@Test
	public void compositeState_exit_before_enter_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("comp", m_ctx);
			comp.setInitialState(new SinkState<>("child", m_ctx));
			comp.exit();
			});
	}

	@Test
	public void compositeState_selectTransition_delegates_to_currentState() {
		Signal trigger = new Signal() {};
		Transition<TestCtx> tt = Transitions.noop("target");
		State<TestCtx> child = new SingleOutState<>("child", m_ctx, trigger, tt);
		DefaultCompositeState<TestCtx> comp = new DefaultCompositeState<>("comp", m_ctx);
		comp.setInitialState(child);
		comp.enter();

		Optional<Transition<TestCtx>> sel = comp.selectTransition(trigger);

		Assertions.assertTrue(sel.isPresent());
		Assertions.assertSame(tt, sel.get());
	}
}
