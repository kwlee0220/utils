package utils.statechart;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.async.AsyncState;

/**
 * {@link StateChart}의 라이프사이클 (등록/start/handleSignal/complete/fail/cancel) 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChartTest {

	private static class TestCtx implements StateContext<TestCtx> {
		private StateChart<TestCtx> m_chart;
		@Override public StateChart<TestCtx> getStateChart() { return m_chart; }
		@Override public void setStateChart(StateChart<TestCtx> m) {
			if ( m_chart != null ) {
				throw new IllegalStateException("chart already set");
			}
			m_chart = m;
		}
	}

	private static class CountedState extends AbstractState<TestCtx> {
		final AtomicInteger enterCount = new AtomicInteger();
		final AtomicInteger exitCount = new AtomicInteger();
		final Map<Signal, Transition<TestCtx>> trans = new HashMap<>();

		CountedState(String path, TestCtx ctx) { super(path, ctx); }

		@Override public void enter() { enterCount.incrementAndGet(); }
		@Override public void exit() { exitCount.incrementAndGet(); }
		@Override public Optional<Transition<TestCtx>> selectTransition(Signal s) {
			return Optional.ofNullable(trans.get(s));
		}
	}

	private TestCtx m_ctx;
	private StateChart<TestCtx> m_chart;

	@Before
	public void setup() {
		m_ctx = new TestCtx();
		m_chart = new StateChart<>(m_ctx);
	}

	// ---- 생성자 / 컨텍스트 결합 ----

	@Test(expected = NullPointerException.class)
	public void constructor_null_context_rejected() {
		new StateChart<TestCtx>(null);
	}

	@Test
	public void constructor_attaches_chart_to_context() {
		Assert.assertSame(m_chart, m_ctx.getStateChart());
	}

	@Test
	public void getContext_returns_provided_context() {
		Assert.assertSame(m_ctx, m_chart.getContext());
	}

	// ---- 상태 등록 ----

	@Test
	public void addState_then_getState() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);

		Assert.assertSame(a, m_chart.getState("a"));
	}

	@Test
	public void getState_unknown_path_returns_null() {
		Assert.assertNull(m_chart.getState("unknown"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void setInitialState_unknown_path_rejected() {
		m_chart.setInitialState("unknown");
	}

	@Test(expected = IllegalArgumentException.class)
	public void addFinalState_unknown_path_rejected() {
		m_chart.addFinalState("unknown");
	}

	// ---- start 사전조건 ----

	@Test(expected = NullPointerException.class)
	public void start_without_initialState_rejected() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		m_chart.addFinalState("a");
		m_chart.start();
	}

	@Test(expected = IllegalStateException.class)
	public void start_without_finalState_rejected() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		m_chart.setInitialState("a");
		m_chart.start();
	}

	@Test(expected = IllegalStateException.class)
	public void start_initialState_equal_to_finalState_rejected() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		m_chart.setInitialState("a");
		m_chart.addFinalState("a");
		m_chart.start();
	}

	@Test
	public void start_transitions_to_RUNNING_with_initial_state() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");

		m_chart.start();

		Assert.assertEquals(AsyncState.RUNNING, m_chart.getState());
		Assert.assertSame(a, m_chart.getCurrentState());
		Assert.assertEquals(1, a.enterCount.get());
		Assert.assertEquals(0, b.enterCount.get());
	}

	// ---- 시작 후 등록 거부 ----

	@Test(expected = IllegalStateException.class)
	public void addState_after_start_rejected() {
		startSimpleChart();
		m_chart.addState(new CountedState("c", m_ctx));
	}

	@Test(expected = IllegalStateException.class)
	public void setInitialState_after_start_rejected() {
		startSimpleChart();
		m_chart.setInitialState("a");
	}

	@Test(expected = IllegalStateException.class)
	public void addFinalState_after_start_rejected() {
		startSimpleChart();
		m_chart.addFinalState("b");
	}

	// ---- handleSignal ----

	@Test
	public void handleSignal_in_NOT_STARTED_returns_empty_and_ignores() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);

		Optional<Transition<TestCtx>> result = m_chart.handleSignal(new Signal() {});

		Assert.assertTrue(result.isEmpty());
		Assert.assertEquals(0, a.enterCount.get());
	}

	@Test(expected = NullPointerException.class)
	public void handleSignal_null_rejected() {
		m_chart.handleSignal(null);
	}

	@Test
	public void handleSignal_no_matching_transition_returns_empty() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		Optional<Transition<TestCtx>> result = m_chart.handleSignal(new Signal() {});

		Assert.assertTrue(result.isEmpty());
		Assert.assertSame(a, m_chart.getCurrentState());
		Assert.assertEquals(0, a.exitCount.get());
	}

	@Test
	public void handleSignal_traverses_to_target_state() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		CountedState c = new CountedState("c", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.addState(c);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.noop("b"));
		m_chart.setInitialState("a");
		m_chart.addFinalState("c");
		m_chart.start();

		m_chart.handleSignal(s);

		Assert.assertEquals(1, a.exitCount.get());
		Assert.assertEquals(1, b.enterCount.get());
		Assert.assertSame(b, m_chart.getCurrentState());
	}

	@Test
	public void handleSignal_executes_transition_action() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		Signal s = new Signal() {};
		AtomicInteger actionInvoked = new AtomicInteger();
		a.trans.put(s, Transitions.create("b", (ctx, sig) -> actionInvoked.incrementAndGet()));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		m_chart.handleSignal(s);

		Assert.assertEquals(1, actionInvoked.get());
		Assert.assertSame(b, m_chart.getCurrentState());
	}

	@Test
	public void handleSignal_self_transition_no_state_change() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.stay());
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();
		int beforeEnter = a.enterCount.get();
		int beforeExit = a.exitCount.get();

		m_chart.handleSignal(s);

		Assert.assertEquals(beforeEnter, a.enterCount.get());
		Assert.assertEquals(beforeExit, a.exitCount.get());
		Assert.assertSame(a, m_chart.getCurrentState());
	}

	@Test
	public void handleSignal_to_finalState_completes_chart() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.noop("b"));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		m_chart.handleSignal(s);

		Assert.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void handleSignal_to_exceptionState_fails_chart() {
		CountedState a = new CountedState("a", m_ctx);
		ExceptionState<TestCtx> err = new ExceptionState<>("err", m_ctx);
		Throwable cause = new IllegalStateException("boom");
		m_chart.addState(a);
		m_chart.addState(err);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.create("err",
				(ctx, sig) -> ((ExceptionState<TestCtx>)ctx.getStateChart().getState("err"))
									.setFailureCause(cause)));
		m_chart.setInitialState("a");
		m_chart.addFinalState("err");
		m_chart.start();

		m_chart.handleSignal(s);

		Assert.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	// ---- complete / fail ----

	@Test
	public void complete_transitions_chart_to_COMPLETED() {
		startSimpleChart();
		m_chart.complete();
		Assert.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test(expected = IllegalStateException.class)
	public void complete_in_NOT_STARTED_rejected() {
		// notifyCompleted는 RUNNING/CANCELLING 상태에서만 동작; NOT_STARTED에서는 false 반환 → ISE.
		m_chart.complete();
	}

	@Test
	public void fail_transitions_chart_to_FAILED() {
		startSimpleChart();
		m_chart.fail(new RuntimeException("boom"));
		Assert.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	// ---- cancel ----

	@Test
	public void cancelWork_exits_current_state_and_returns_true() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		boolean result = m_chart.cancelWork();

		Assert.assertTrue(result);
		Assert.assertEquals(1, a.exitCount.get());
		Assert.assertNull(m_chart.getCurrentState());
	}

	@Test
	public void cancelWork_when_no_currentState_returns_true_immediately() {
		// 시작하지 않은 차트는 currentState == null 상태이므로 즉시 true를 반환한다.
		boolean result = m_chart.cancelWork();

		Assert.assertTrue(result);
	}

	// ---- helpers ----

	/**
	 * 두 state(a, b)를 등록하고 a를 initial, b를 final로 지정 후 차트를 시작한다.
	 */
	private void startSimpleChart() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();
	}
}
