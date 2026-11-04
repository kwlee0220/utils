package utils.statechart;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.async.AsyncState;
import utils.statechart.AbstractTimedState.TimedOut;

/**
 * {@link AbstractTimedState}의 타이머 수명(진입·재시작·진출), 오래된 만료 신호 처리, 부모·자식 타이머,
 * 진입 실패와 직접 종료 시 정리를 검증한다.
 * <p>
 * 대부분의 시험은 예약만 기록하는 {@link ManualScheduler}로 만료 시점을 직접 정한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractTimedStateTest {
	private static final Signal TICK = new Signal() {};
	private static final Signal GO_B = new Signal() {};
	private static final Signal GO_A = new Signal() {};

	private static class TestCtx extends AbstractStateContext<TestCtx> { }

	/** 예약한 작업을 실행하지 않고 기록만 한다. 시험이 {@link #fire(int)}로 직접 실행한다. */
	private static class ManualScheduler extends ScheduledThreadPoolExecutor {
		final List<Runnable> tasks = new CopyOnWriteArrayList<>();
		final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();

		ManualScheduler() {
			super(1);
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
			tasks.add(command);
			ScheduledFuture<?> future = super.schedule(() -> { }, 1, TimeUnit.DAYS);
			futures.add(future);
			return future;
		}

		void fire(int index) {
			tasks.get(index).run();
		}
	}

	private class TimedState extends AbstractTimedState<TestCtx> {
		final String m_target;
		final AtomicInteger m_timeoutCount = new AtomicInteger();
		volatile Duration m_timeout = Duration.ofSeconds(10);
		volatile RuntimeException m_enterFailure;

		TimedState(String path, String target, Executor signalExecutor) {
			super(path, m_ctx, m_scheduler, signalExecutor);
			m_target = target;
		}

		@Override
		protected Duration getTimeout() {
			return m_timeout;
		}

		@Override
		protected Transition<TestCtx> onTimeout(Duration timeout) {
			m_timeoutCount.incrementAndGet();
			return Transitions.to(m_target);
		}

		@Override
		protected void onEnter() {
			if ( m_enterFailure != null ) {
				throw m_enterFailure;
			}
		}
	}

	/** 초기 하위 상태가 {@code /P/C}인, 타이머를 가진 복합 상태. */
	private class TimedComposite extends TimedState implements CompositeState<TestCtx> {
		TimedComposite() {
			super("/P", "/ParentDone", null);
		}

		@Override
		public String getInitialStatePath() {
			return "/P/C";
		}
	}

	private TestCtx m_ctx;
	private ManualScheduler m_scheduler;
	private List<Signal> m_unhandled;
	private StateChart<TestCtx> m_chart;

	@BeforeEach
	public void setup() {
		m_ctx = new TestCtx();
		m_scheduler = new ManualScheduler();
		m_unhandled = new CopyOnWriteArrayList<>();
		m_chart = new StateChart<>(m_ctx) {
			@Override
			protected void onUnhandledSignal(Signal signal) {
				m_unhandled.add(signal);
			}
		};
	}

	@AfterEach
	public void tearDown() {
		m_scheduler.shutdownNow();
	}

	private TimedState addTimedState(String path, String target) {
		TimedState state = new TimedState(path, target, null);
		m_chart.addState(state);
		return state;
	}

	@Test
	public void expiry_selects_the_timeout_transition() {
		TimedState a = addTimedState("/A", "/Done");
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		Assertions.assertEquals(1, m_scheduler.tasks.size());
		m_scheduler.fire(0);

		Assertions.assertEquals(1, a.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void restart_ignores_the_previous_timer() {
		TimedState a = addTimedState("/A", "/Done");
		a.on(TICK, Transitions.internal((ctx, sig) -> a.restartTimeout()));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(TICK);
		Assertions.assertEquals(2, m_scheduler.tasks.size());
		Assertions.assertTrue(m_scheduler.futures.get(0).isCancelled());

		// 재시작 직전에 실행을 시작한 만료 작업: 신호를 보내지 않는다.
		m_scheduler.fire(0);
		Assertions.assertEquals(0, a.m_timeoutCount.get());
		Assertions.assertEquals("/A", m_chart.getCurrentState().getPath());

		m_scheduler.fire(1);
		Assertions.assertEquals(1, a.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void stale_signal_already_queued_is_consumed_with_stay() {
		TimedState a = addTimedState("/A", "/Done");
		a.on(TICK, Transitions.internal((ctx, sig) -> a.restartTimeout()));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		// 진입이 건 첫 타이머는 세대 1. 재시작하면 세대 2가 된다.
		m_chart.handleSignal(TICK);
		SignalResult<TestCtx> result = m_chart.processSignal(new TimedOut(a, 1, Duration.ofSeconds(10)));

		Assertions.assertEquals(SignalResult.Status.TRANSITIONED, result.status());
		Assertions.assertEquals(0, a.m_timeoutCount.get());
		Assertions.assertEquals("/A", m_chart.getCurrentState().getPath());
		Assertions.assertTrue(m_unhandled.isEmpty());
	}

	@Test
	public void exit_cancels_the_timer_and_late_signal_is_unhandled() {
		TimedState a = addTimedState("/A", "/Done");
		a.on(GO_B, Transitions.to("/B"));
		m_chart.addState(new DefaultState<>("/B", m_ctx));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(GO_B);
		Assertions.assertTrue(m_scheduler.futures.get(0).isCancelled());

		// 진출 후 실행된 만료 작업은 신호를 보내지 않는다.
		m_scheduler.fire(0);
		Assertions.assertTrue(m_unhandled.isEmpty());

		// 진출 전에 이미 큐에 들어간 신호는 아무 상태도 처리하지 않는다.
		TimedOut late = new TimedOut(a, 1, Duration.ofSeconds(10));
		Assertions.assertEquals(SignalResult.Status.UNHANDLED, m_chart.processSignal(late).status());
		Assertions.assertEquals(List.of(late), m_unhandled);
		Assertions.assertEquals("/B", m_chart.getCurrentState().getPath());
	}

	@Test
	public void restart_after_exit_does_not_arm_a_timer() {
		TimedState a = addTimedState("/A", "/Done");
		a.on(GO_B, Transitions.to("/B"));
		m_chart.addState(new DefaultState<>("/B", m_ctx));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(GO_B);
		// 진출한 뒤 비동기 콜백 등에서 재시작을 요청한 경우. 진출하면서 제한 시간 정보를 정리했더라도
		// getTimeout()을 부르지 않으므로 예외가 나지 않는다.
		a.m_timeout = null;
		a.restartTimeout();
		Assertions.assertEquals(1, m_scheduler.tasks.size());
	}

	@Test
	public void signal_from_previous_entry_is_ignored_after_reentry() {
		TimedState a = addTimedState("/A", "/Done");
		a.on(GO_B, Transitions.to("/B"));
		m_chart.addState(new DefaultState<TestCtx>("/B", m_ctx).on(GO_A, Transitions.to("/A")));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(GO_B);
		m_chart.handleSignal(GO_A);
		Assertions.assertEquals(2, m_scheduler.tasks.size());

		m_scheduler.fire(0);
		m_chart.processSignal(new TimedOut(a, 1, Duration.ofSeconds(10)));
		Assertions.assertEquals(0, a.m_timeoutCount.get());
		Assertions.assertEquals("/A", m_chart.getCurrentState().getPath());

		m_scheduler.fire(1);
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void parent_and_child_timers_are_independent() {
		TimedComposite p = new TimedComposite();
		m_chart.addState(p);
		TimedState c = addTimedState("/P/C", "/ChildDone");
		m_chart.addState(new TerminalState<>("/ParentDone", m_ctx));
		m_chart.addState(new TerminalState<>("/ChildDone", m_ctx));
		m_chart.setInitialState("/P");
		m_chart.start();

		// 부모가 먼저 진입해 타이머를 건다.
		Assertions.assertEquals(2, m_scheduler.tasks.size());
		Assertions.assertFalse(m_scheduler.futures.get(0).isCancelled());
		Assertions.assertFalse(m_scheduler.futures.get(1).isCancelled());

		// 부모의 만료 신호는 자식이 소비하지 않고 부모까지 올라간다.
		m_scheduler.fire(0);
		Assertions.assertEquals(0, c.m_timeoutCount.get());
		Assertions.assertEquals(1, p.m_timeoutCount.get());
		Assertions.assertEquals("/ParentDone", m_chart.getCurrentState().getPath());
		Assertions.assertTrue(m_scheduler.futures.get(1).isCancelled());
	}

	@Test
	public void child_expiry_does_not_touch_parent_timer() {
		TimedComposite p = new TimedComposite();
		m_chart.addState(p);
		TimedState c = addTimedState("/P/C", "/P/D");
		m_chart.addState(new DefaultState<>("/P/D", m_ctx));
		m_chart.addState(new TerminalState<>("/ParentDone", m_ctx));
		m_chart.setInitialState("/P");
		m_chart.start();

		m_scheduler.fire(1);
		Assertions.assertEquals(1, c.m_timeoutCount.get());
		Assertions.assertEquals("/P/D", m_chart.getCurrentState().getPath());
		Assertions.assertFalse(m_scheduler.futures.get(0).isCancelled());

		m_scheduler.fire(0);
		Assertions.assertEquals(1, p.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void failing_onEnter_cancels_the_timer() {
		TimedState a = addTimedState("/A", "/Done");
		a.m_enterFailure = new IllegalStateException("boom");
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertTrue(m_scheduler.futures.get(0).isCancelled());
	}

	@Test
	public void non_positive_timeout_fails_the_entry() {
		TimedState a = addTimedState("/A", "/Done");
		a.m_timeout = Duration.ZERO;
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
		Assertions.assertTrue(m_scheduler.tasks.isEmpty());
	}

	@Test
	public void direct_completion_cancels_the_timer_and_expiry_posts_nothing() {
		List<Runnable> posted = new CopyOnWriteArrayList<>();
		TimedState a = new TimedState("/A", "/Done", posted::add);
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		// 직접 종료하면 exit()가 실행되지 않지만, 종료 리스너가 예약을 해제한다.
		m_chart.complete();
		Assertions.assertTrue(m_scheduler.futures.get(0).isCancelled());

		m_scheduler.fire(0);
		Assertions.assertTrue(posted.isEmpty());
	}

	@Test
	public void child_catch_all_transition_does_not_intercept_parent_timeout() {
		TimedComposite p = new TimedComposite();
		m_chart.addState(p);
		// 모든 신호에 머무는 자식. 만료 신호를 전이 표로 넘기면 부모의 만료를 삼킨다.
		m_chart.addState(new DefaultState<TestCtx>("/P/C", m_ctx).on(Signal.class, Transitions.stay()));
		m_chart.addState(new TerminalState<>("/ParentDone", m_ctx));
		m_chart.setInitialState("/P");
		m_chart.start();

		m_scheduler.fire(0);
		Assertions.assertEquals(1, p.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void rejected_signal_executor_falls_back_to_the_scheduler_thread() {
		TimedState a = new TimedState("/A", "/Done", task -> {
			throw new RejectedExecutionException("shut down");
		});
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_scheduler.fire(0);
		Assertions.assertEquals(1, a.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void signal_is_posted_through_the_signal_executor() {
		List<Runnable> posted = new CopyOnWriteArrayList<>();
		TimedState a = new TimedState("/A", "/Done", posted::add);
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_scheduler.fire(0);
		Assertions.assertEquals(1, posted.size());
		Assertions.assertEquals(0, a.m_timeoutCount.get());

		posted.get(0).run();
		Assertions.assertEquals(1, a.m_timeoutCount.get());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void expires_with_a_real_scheduler() throws Exception {
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
		try {
			AtomicInteger count = new AtomicInteger();
			m_chart.addState(new AbstractTimedState<TestCtx>("/A", m_ctx, scheduler) {
				@Override
				protected Duration getTimeout() {
					return Duration.ofMillis(50);
				}

				@Override
				protected Transition<TestCtx> onTimeout(Duration timeout) {
					count.incrementAndGet();
					return Transitions.to("/Done");
				}
			});
			m_chart.addState(new TerminalState<>("/Done", m_ctx));
			m_chart.setInitialState("/A");
			m_chart.start();

			m_chart.waitForFinished(5, TimeUnit.SECONDS);
			Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
			Assertions.assertEquals(1, count.get());
		}
		finally {
			scheduler.shutdownNow();
		}
	}

	@Test
	public void constructor_rejects_null_scheduler() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new AbstractTimedState<TestCtx>("/A", m_ctx, null) {
			@Override
			protected Duration getTimeout() {
				return Duration.ofSeconds(1);
			}

			@Override
			protected Transition<TestCtx> onTimeout(Duration timeout) {
				return Transitions.stay();
			}
		});
	}
}
