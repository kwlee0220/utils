package utils.statechart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

	@BeforeEach
	public void setup() {
		m_ctx = new TestCtx();
		m_chart = new StateChart<>(m_ctx);
	}

	// ---- 생성자 / 컨텍스트 결합 ----

	@Test
	public void constructor_null_context_rejected() {
		Assertions.assertThrows(NullPointerException.class, () -> {
			new StateChart<TestCtx>(null);
			});
	}

	@Test
	public void constructor_attaches_chart_to_context() {
		Assertions.assertSame(m_chart, m_ctx.getStateChart());
	}

	@Test
	public void getContext_returns_provided_context() {
		Assertions.assertSame(m_ctx, m_chart.getContext());
	}

	// ---- 상태 등록 ----

	@Test
	public void addState_then_findState() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);

		Assertions.assertSame(a, m_chart.findState("a").orElseThrow());
	}

	@Test
	public void findState_unknown_path_returns_empty() {
		Assertions.assertTrue(m_chart.findState("unknown").isEmpty());
	}

	@Test
	public void setInitialState_unknown_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			m_chart.setInitialState("unknown");
			});
	}

	@Test
	public void addFinalState_unknown_path_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			m_chart.addFinalState("unknown");
			});
	}

	// ---- start 사전조건 ----

	@Test
	public void start_without_initialState_rejected() {
		Assertions.assertThrows(NullPointerException.class, () -> {
			CountedState a = new CountedState("a", m_ctx);
			m_chart.addState(a);
			m_chart.addFinalState("a");
			m_chart.start();
			});
	}

	@Test
	public void start_without_finalState_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			CountedState a = new CountedState("a", m_ctx);
			m_chart.addState(a);
			m_chart.setInitialState("a");
			m_chart.start();
			});
	}

	@Test
	public void start_initialState_equal_to_finalState_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			CountedState a = new CountedState("a", m_ctx);
			m_chart.addState(a);
			m_chart.setInitialState("a");
			m_chart.addFinalState("a");
			m_chart.start();
			});
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

		Assertions.assertEquals(AsyncState.RUNNING, m_chart.getState());
		Assertions.assertSame(a, m_chart.getCurrentState());
		Assertions.assertEquals(1, a.enterCount.get());
		Assertions.assertEquals(0, b.enterCount.get());
	}

	// ---- 시작 후 등록 거부 ----

	@Test
	public void addState_after_start_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			startSimpleChart();
			m_chart.addState(new CountedState("c", m_ctx));
			});
	}

	@Test
	public void setInitialState_after_start_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			startSimpleChart();
			m_chart.setInitialState("a");
			});
	}

	@Test
	public void addFinalState_after_start_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			startSimpleChart();
			m_chart.addFinalState("b");
			});
	}

	// ---- handleSignal ----

	@Test
	public void handleSignal_in_NOT_STARTED_returns_empty_and_ignores() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);

		Optional<Transition<TestCtx>> result = m_chart.handleSignal(new Signal() {});

		Assertions.assertTrue(result.isEmpty());
		Assertions.assertEquals(0, a.enterCount.get());
	}

	@Test
	public void handleSignal_null_rejected() {
		Assertions.assertThrows(NullPointerException.class, () -> {
			m_chart.handleSignal(null);
			});
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

		Assertions.assertTrue(result.isEmpty());
		Assertions.assertSame(a, m_chart.getCurrentState());
		Assertions.assertEquals(0, a.exitCount.get());
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
		a.trans.put(s, Transitions.to("b"));
		m_chart.setInitialState("a");
		m_chart.addFinalState("c");
		m_chart.start();

		m_chart.handleSignal(s);

		Assertions.assertEquals(1, a.exitCount.get());
		Assertions.assertEquals(1, b.enterCount.get());
		Assertions.assertSame(b, m_chart.getCurrentState());
	}

	@Test
	public void handleSignal_executes_transition_action() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		Signal s = new Signal() {};
		AtomicInteger actionInvoked = new AtomicInteger();
		a.trans.put(s, Transitions.to("b", (ctx, sig) -> actionInvoked.incrementAndGet()));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		m_chart.handleSignal(s);

		Assertions.assertEquals(1, actionInvoked.get());
		Assertions.assertSame(b, m_chart.getCurrentState());
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

		Assertions.assertEquals(beforeEnter, a.enterCount.get());
		Assertions.assertEquals(beforeExit, a.exitCount.get());
		Assertions.assertSame(a, m_chart.getCurrentState());
	}

	@Test
	public void handleSignal_to_finalState_completes_chart() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.to("b"));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		m_chart.handleSignal(s);

		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void handleSignal_to_failureState_fails_chart() {
		CountedState a = new CountedState("a", m_ctx);
		FailureState<TestCtx> err = new FailureState<>("err", m_ctx);
		Throwable cause = new IllegalStateException("boom");
		m_chart.addState(a);
		m_chart.addState(err);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.to("err",
				(ctx, sig) -> ((FailureState<TestCtx>)ctx.getStateChart().findState("err").orElseThrow())
									.setFailureCause(cause)));
		m_chart.setInitialState("a");
		m_chart.addFinalState("err");
		m_chart.start();

		m_chart.handleSignal(s);

		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	// ---- complete / fail ----

	@Test
	public void complete_transitions_chart_to_COMPLETED() {
		startSimpleChart();
		m_chart.complete();
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void complete_in_NOT_STARTED_rejected() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			// notifyCompleted는 RUNNING/CANCELLING 상태에서만 동작; NOT_STARTED에서는 false 반환 → ISE.
			m_chart.complete();
			});
	}

	@Test
	public void fail_transitions_chart_to_FAILED() {
		startSimpleChart();
		m_chart.fail(new RuntimeException("boom"));
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	@Test
	public void fail_null_cause_rejected_and_chart_keeps_running() {
		startSimpleChart();

		Assertions.assertThrows(IllegalArgumentException.class, () -> m_chart.fail(null));
		Assertions.assertEquals(AsyncState.RUNNING, m_chart.getState());
	}

	@Test
	public void failureState_without_cause_fails_with_descriptive_cause_and_runs_listener() throws Exception {
		CountedState a = new CountedState("a", m_ctx);
		FailureState<TestCtx> err = new FailureState<>("err", m_ctx);
		m_chart.addState(a);
		m_chart.addState(err);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.to("err"));		// 원인을 설정하지 않는 전이
		m_chart.setInitialState("a");
		m_chart.addFinalState("err");
		AtomicInteger finished = new AtomicInteger();
		m_chart.whenFinished(result -> finished.incrementAndGet());
		m_chart.start();

		m_chart.handleSignal(s);

		// 원인이 빠졌다는 것과 그 상태를 알려 주는 예외로 실패하고, 종료 리스너도 실행된다.
		Throwable cause = m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause();
		Assertions.assertTrue(cause instanceof IllegalStateException, "cause=" + cause);
		Assertions.assertTrue(cause.getMessage().contains("state=err"), cause.getMessage());
		Assertions.assertEquals(1, finished.get());
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

		Assertions.assertTrue(result);
		Assertions.assertEquals(1, a.exitCount.get());
		Assertions.assertNull(m_chart.getCurrentState());
	}

	@Test
	public void cancelWork_when_no_currentState_returns_true_immediately() {
		// 시작하지 않은 차트는 currentState == null 상태이므로 즉시 true를 반환한다.
		boolean result = m_chart.cancelWork();

		Assertions.assertTrue(result);
	}

	@Test
	public void cancel_reaches_CANCELLED_and_runs_finish_listener() throws Exception {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		AtomicInteger finished = new AtomicInteger();
		m_chart.whenFinished(result -> finished.incrementAndGet());
		m_chart.start();

		Assertions.assertTrue(m_chart.cancel(true));

		// 취소가 확정되므로 제한 시간 안에 종료되고, 종료 리스너가 한 번 실행된다.
		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(AsyncState.CANCELLED, m_chart.getState());
		Assertions.assertEquals(1, finished.get());
		Assertions.assertEquals(1, a.exitCount.get());
		Assertions.assertEquals(0, b.enterCount.get());		// 최종 상태로 진입하지 않는다.
		Assertions.assertNull(m_chart.getCurrentState());
	}

	@Test
	public void setTimeout_cancels_running_chart() throws Exception {
		startSimpleChart();

		m_chart.setTimeout(100, TimeUnit.MILLISECONDS);

		Assertions.assertTrue(m_chart.waitForFinished(5, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(AsyncState.CANCELLED, m_chart.getState());
	}

	@Test
	public void cancelWork_after_completion_does_nothing() {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		Signal toB = new Signal() { };
		a.trans.put(toB, Transitions.to("b"));
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();
		m_chart.handleSignal(toB);
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());

		// 취소 요청과 경쟁하던 전이가 먼저 끝난 경우: 결과를 바꾸지 않고 최종 상태에서 진출하지도 않는다.
		Assertions.assertFalse(m_chart.cancelWork());
		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
		Assertions.assertEquals(0, b.exitCount.get());
	}

	@Test
	public void cancel_is_confirmed_even_if_exit_throws() throws Exception {
		CountedState a = new CountedState("a", m_ctx) {
			@Override public void exit() {
				super.exit();
				throw new IllegalStateException("exit failure");
			}
		};
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		Assertions.assertTrue(m_chart.cancel(true));

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(1, a.exitCount.get());
		Assertions.assertNull(m_chart.getCurrentState());
	}

	// ---- 신호 처리 순서와 결과 ----

	@Test
	public void processSignal_reports_status() throws Exception {
		Signal unknown = new Signal() {};
		Signal toB = new Signal() {};
		CountedState a = new CountedState("a", m_ctx);
		a.trans.put(toB, Transitions.to("b"));
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");

		Assertions.assertEquals(SignalResult.Status.IGNORED, m_chart.processSignal(toB).status());

		m_chart.start();
		Assertions.assertEquals(SignalResult.Status.UNHANDLED, m_chart.processSignal(unknown).status());

		SignalResult<TestCtx> result = m_chart.processSignal(toB);
		Assertions.assertTrue(result.isTransitioned());
		Assertions.assertSame(a.trans.get(toB), result.getTransition().get());

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		Assertions.assertEquals(SignalResult.Status.IGNORED, m_chart.processSignal(toB).status());
		Assertions.assertTrue(m_chart.processSignal(toB).getTransition().isEmpty());
	}

	@Test
	public void signal_sent_in_enter_is_processed_after_current_transition() {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.to("/B")));
		m_chart.addState(new LogState("/B") {
			@Override public void enter() {
				super.enter();
				// 전이 도중에 보낸 신호는 지금 처리되지 않고 큐에 들어간다.
				m_log.add("sent:" + m_chart.processSignal(m_toDone).status());
			}
		}.on(m_toDone, Transitions.to("/C")));
		m_chart.addState(new LogState("/C"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertTrue(m_chart.handleSignal(m_toY).isPresent());

		// 큐에 넣은 신호는 /B 진입이 끝난 뒤에 처리된다.
		Assertions.assertEquals(List.of("exit:/A", "enter:/B", "sent:QUEUED", "exit:/B", "enter:/C"), m_log);
		Assertions.assertEquals("/C", m_chart.getCurrentState().getPath());
	}

	@Test
	public void signal_sent_in_initial_enter_is_processed_after_start() {
		m_chart.addState(new LogState("/A") {
			@Override public void enter() {
				super.enter();
				m_log.add("sent:" + m_chart.processSignal(m_toY).status());
			}
		}.on(m_toY, Transitions.to("/B")));
		m_chart.addState(new LogState("/B"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");

		m_chart.start();

		// 이전에는 RUNNING 전이라 이 신호가 무시되었다.
		Assertions.assertEquals(List.of("enter:/A", "sent:QUEUED", "exit:/A", "enter:/B"), m_log);
		Assertions.assertTrue(m_chart.isRunning());
		Assertions.assertEquals("/B", m_chart.getCurrentState().getPath());
	}

	/**
	 * {@code /A}에서 {@code m_toY}를 받으면 {@code release}가 열릴 때까지 전이 작업에서 멈춘 뒤 {@code /B}로 간다.
	 * {@code /B}는 받은 신호를 {@code received}에 기록하고 머문다. {@code m_toDone}을 받으면 예외를 던진다.
	 */
	private void buildBlockingChart(CountDownLatch inAction, CountDownLatch release, List<Signal> received) {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.to("/B", (ctx, sig) -> {
			inAction.countDown();
			try {
				release.await();
			}
			catch ( InterruptedException e ) {
				throw new AssertionError(e);
			}
		})));
		m_chart.addState(new AbstractState<TestCtx>("/B", m_ctx) {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				if ( sig == m_toDone ) {
					throw new IllegalStateException("bad signal");
				}
				synchronized ( received ) {
					received.add(sig);
				}
				return Optional.of(Transitions.stay());
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
	}

	@Test
	public void signals_posted_while_another_is_processed_keep_their_order() throws Exception {
		CountDownLatch inAction = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<Signal> received = new ArrayList<>();
		buildBlockingChart(inAction, release, received);

		CompletableFuture<Optional<Transition<TestCtx>>> first
										= CompletableFuture.supplyAsync(() -> m_chart.handleSignal(m_toY));
		Assertions.assertTrue(inAction.await(2, TimeUnit.SECONDS));

		// 다른 스레드가 처리 중이므로 postSignal()은 큐에 넣기만 하고 바로 반환한다.
		List<Signal> sent = new ArrayList<>();
		List<CompletableFuture<SignalResult<TestCtx>>> futures = new ArrayList<>();
		for ( int i = 0; i < 5; ++i ) {
			Signal sig = new Signal() {};
			sent.add(sig);
			futures.add(m_chart.postSignal(sig));
		}
		Assertions.assertTrue(futures.stream().noneMatch(CompletableFuture::isDone));

		release.countDown();
		Assertions.assertTrue(first.get(2, TimeUnit.SECONDS).isPresent());
		for ( CompletableFuture<SignalResult<TestCtx>> f: futures ) {
			Assertions.assertTrue(f.get(2, TimeUnit.SECONDS).isTransitioned());
		}
		Assertions.assertEquals(sent, received);
	}

	@Test
	public void exception_goes_to_sender_even_if_another_thread_processes_it() throws Exception {
		CountDownLatch inAction = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		buildBlockingChart(inAction, release, new ArrayList<>());

		CompletableFuture<Optional<Transition<TestCtx>>> first
										= CompletableFuture.supplyAsync(() -> m_chart.handleSignal(m_toY));
		Assertions.assertTrue(inAction.await(2, TimeUnit.SECONDS));

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread sender = new Thread(() -> {
			try {
				m_chart.handleSignal(m_toDone);
			}
			catch ( Throwable e ) {
				thrown.set(e);
			}
		});
		sender.start();
		awaitWaiting(sender);

		release.countDown();
		sender.join(2000);

		// 처리한 스레드가 아니라 신호를 보낸 스레드가 예외를 받는다.
		Assertions.assertTrue(first.get(2, TimeUnit.SECONDS).isPresent());
		Assertions.assertTrue(thrown.get() instanceof IllegalStateException, "" + thrown.get());
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	@Test
	public void sending_signal_while_holding_chart_lock_does_not_deadlock() throws Exception {
		CountedState a = new CountedState("a", m_ctx);
		Signal s1 = new Signal() {};
		Signal s2 = new Signal() {};
		a.trans.put(s1, Transitions.stay());
		a.trans.put(s2, Transitions.to("b"));
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		// 차트 lock을 쥔 스레드(동기 종료 리스너 등)가, 차트 lock을 기다리는 다른 처리 스레드가 있는 상태에서
		// 신호를 보내는 상황. 기다리면 서로를 기다리게 된다.
		CompletableFuture<SignalResult.Status> holder = CompletableFuture.supplyAsync(() -> {
			m_chart.getChartLock().lock();
			try {
				Thread dispatcher = new Thread(() -> m_chart.handleSignal(s1));
				dispatcher.start();
				awaitWaiting(dispatcher);
				return m_chart.processSignal(s2).status();
			}
			catch ( InterruptedException e ) {
				throw new AssertionError(e);
			}
			finally {
				m_chart.getChartLock().unlock();
			}
		});

		Assertions.assertEquals(SignalResult.Status.QUEUED, holder.get(5, TimeUnit.SECONDS));
		// lock을 놓으면 처리 스레드가 s1, s2를 차례로 처리한다.
		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void onUnhandledSignal_called_only_for_running_unhandled_signal() throws Exception {
		List<Signal> unhandled = new ArrayList<>();
		TestCtx ctx = new TestCtx();
		StateChart<TestCtx> chart = new StateChart<>(ctx) {
			@Override protected void onUnhandledSignal(Signal signal) {
				unhandled.add(signal);
			}
		};
		Signal toB = new Signal() {};
		Signal unknown = new Signal() {};
		CountedState a = new CountedState("a", ctx);
		a.trans.put(toB, Transitions.to("b"));
		chart.addState(a);
		chart.addState(new TerminalState<>("b", ctx));
		chart.setInitialState("a");
		chart.addFinalState("b");

		chart.handleSignal(unknown);		// 시작 전: 무시
		chart.start();
		chart.handleSignal(unknown);		// 처리할 전이 없음
		chart.handleSignal(toB);			// 전이 수행
		chart.handleSignal(unknown);		// 종료 후: 무시

		Assertions.assertEquals(List.of(unknown), unhandled);
	}

	@Test
	public void posted_signal_is_processed_even_if_its_future_is_cancelled_or_timed_out() throws Exception {
		CountDownLatch inAction = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<Signal> received = new ArrayList<>();
		buildBlockingChart(inAction, release, received);

		CompletableFuture<Optional<Transition<TestCtx>>> first
										= CompletableFuture.supplyAsync(() -> m_chart.handleSignal(m_toY));
		Assertions.assertTrue(inAction.await(2, TimeUnit.SECONDS));

		// 처리되기 전에 future를 취소하거나 시간 제한으로 완료시켜도 신호는 철회되지 않는다.
		Signal cancelled = new Signal() {};
		Signal timedOut = new Signal() {};
		Assertions.assertTrue(m_chart.postSignal(cancelled).cancel(false));
		CompletableFuture<SignalResult<TestCtx>> limited = m_chart.postSignal(timedOut)
																	.orTimeout(1, TimeUnit.MILLISECONDS);
		Assertions.assertThrows(java.util.concurrent.ExecutionException.class, limited::get);

		release.countDown();
		Assertions.assertTrue(first.get(2, TimeUnit.SECONDS).isPresent());
		// 동기 신호 하나를 보내 앞선 신호가 모두 처리될 때까지 기다린다.
		Signal last = new Signal() {};
		Assertions.assertTrue(m_chart.processSignal(last).isTransitioned());
		Assertions.assertEquals(List.of(cancelled, timedOut, last), received);
	}

	@Test
	public void start_while_holding_chart_lock_rejected() {
		m_chart.addState(new LogState("/A"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");

		// 다른 스레드가 신호를 처리하는 중이면 교착될 수 있으므로, 처리 중인 스레드가 없어도 항상 거부한다.
		m_chart.getChartLock().lock();
		try {
			Assertions.assertThrows(IllegalStateException.class, m_chart::start);
		}
		finally {
			m_chart.getChartLock().unlock();
		}
		Assertions.assertEquals(AsyncState.NOT_STARTED, m_chart.getState());
		Assertions.assertEquals(List.of(), m_log);

		m_chart.start();
		Assertions.assertTrue(m_chart.isRunning());
	}

	@Test
	public void transition_to_unregistered_target_fails_chart_and_reaches_sender() {
		Signal toNowhere = new Signal() {};
		CountedState a = new CountedState("a", m_ctx);
		a.trans.put(toNowhere, Transitions.to("nowhere"));
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("done", m_ctx));
		m_chart.setInitialState("a");
		m_chart.start();

		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
															() -> m_chart.handleSignal(toNowhere));
		Assertions.assertTrue(e.getMessage().contains("nowhere"), e.getMessage());
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	@Test
	public void completion_exception_thrown_by_state_reaches_sender_as_is() {
		// 상태가 직접 던진 CompletionException의 원인만 꺼내 전달하던 문제의 회귀 테스트.
		java.util.concurrent.CompletionException original
					= new java.util.concurrent.CompletionException("outer", new IOException("inner"));
		Signal bad = new Signal() {};
		m_chart.addState(new AbstractState<TestCtx>("/A", m_ctx) {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				throw original;
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();

		java.util.concurrent.CompletionException thrown
				= Assertions.assertThrows(java.util.concurrent.CompletionException.class, () -> m_chart.handleSignal(bad));
		Assertions.assertSame(original, thrown);
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
	}

	/** 스레드가 lock이나 future를 기다리는 상태가 될 때까지 기다린다. */
	private static void awaitWaiting(Thread thread) throws InterruptedException {
		long due = System.currentTimeMillis() + 2000;
		while ( thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING ) {
			Assertions.assertTrue(System.currentTimeMillis() < due, "thread did not block: " + thread.getState());
			Thread.sleep(10);
		}
	}

	// ---- Error 처리 ----

	@Test
	public void transition_action_error_fails_chart_and_is_absorbed() throws Exception {
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		CountedState c = new CountedState("c", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.addState(c);
		AssertionError error = new AssertionError("action error");
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.to("b", (ctx, sig) -> { throw error; }));
		m_chart.setInitialState("a");
		m_chart.addFinalState("c");
		AtomicInteger finished = new AtomicInteger();
		m_chart.whenFinished(r -> finished.incrementAndGet());
		m_chart.start();

		// 전이 수행 중의 Error도 Exception처럼 흡수된다.
		Assertions.assertTrue(m_chart.handleSignal(s).isPresent());

		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals(1, finished.get());
		Assertions.assertEquals(0, b.enterCount.get());
	}

	@Test
	public void transition_virtualMachineError_fails_chart_then_rethrown() throws Exception {
		InternalError error = new InternalError("fatal");
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx) {
			@Override public void enter() { throw error; }
		};
		CountedState c = new CountedState("c", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.addState(c);
		Signal s = new Signal() {};
		a.trans.put(s, Transitions.to("b"));
		m_chart.setInitialState("a");
		m_chart.addFinalState("c");
		AtomicInteger finished = new AtomicInteger();
		m_chart.whenFinished(r -> finished.incrementAndGet());
		m_chart.start();

		Assertions.assertSame(error, Assertions.assertThrows(InternalError.class, () -> m_chart.handleSignal(s)));

		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals(1, finished.get());
	}

	@Test
	public void selectTransition_error_fails_chart_and_propagates() throws Exception {
		AssertionError error = new AssertionError("select error");
		CountedState a = new CountedState("a", m_ctx) {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) { throw error; }
		};
		CountedState b = new CountedState("b", m_ctx);
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		Assertions.assertSame(error, Assertions.assertThrows(AssertionError.class,
														() -> m_chart.handleSignal(new Signal() {})));

		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void start_initial_enter_error_fails_chart() throws Exception {
		AssertionError error = new AssertionError("enter error");
		CountedState a = new CountedState("a", m_ctx) {
			@Override public void enter() { throw error; }
		};
		m_chart.addState(a);
		m_chart.addState(new CountedState("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");

		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, m_chart::start);

		Assertions.assertSame(error, thrown.getCause());
		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void start_initial_enter_virtualMachineError_fails_chart_then_rethrown() throws Exception {
		InternalError error = new InternalError("fatal");
		CountedState a = new CountedState("a", m_ctx) {
			@Override public void enter() { throw error; }
		};
		m_chart.addState(a);
		m_chart.addState(new CountedState("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");

		Assertions.assertSame(error, Assertions.assertThrows(InternalError.class, m_chart::start));

		Assertions.assertEquals(AsyncState.FAILED, m_chart.getState());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void cancel_is_confirmed_even_if_exit_throws_error() throws Exception {
		CountedState a = new CountedState("a", m_ctx) {
			@Override public void exit() { throw new AssertionError("exit error"); }
		};
		m_chart.addState(a);
		m_chart.addState(new CountedState("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		Assertions.assertTrue(m_chart.cancel(true));

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertNull(m_chart.getCurrentState());
	}

	@Test
	public void cancel_exit_virtualMachineError_confirms_cancel_then_rethrown() throws Exception {
		InternalError error = new InternalError("fatal");
		CountedState a = new CountedState("a", m_ctx) {
			@Override public void exit() { throw error; }
		};
		m_chart.addState(a);
		m_chart.addState(new CountedState("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		AtomicInteger finished = new AtomicInteger();
		m_chart.whenFinished(r -> finished.incrementAndGet());
		m_chart.start();

		Assertions.assertSame(error, Assertions.assertThrows(InternalError.class, () -> m_chart.cancel(true)));

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(1, finished.get());
		Assertions.assertNull(m_chart.getCurrentState());
	}

	// ---- 내부 전이 ----

	@Test
	public void internal_transition_runs_action_without_exit_and_enter() {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.internal((ctx, sig) -> m_log.add("action:" + sig))));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		SignalResult<TestCtx> result = m_chart.processSignal(m_toY);
		m_chart.processSignal(m_toY);

		Assertions.assertTrue(result.isTransitioned());
		Assertions.assertEquals(List.of("action:toY", "action:toY"), m_log);
		Assertions.assertEquals("/A", m_chart.getCurrentState().getPath());
		Assertions.assertTrue(m_chart.isRunning());
	}

	@Test
	public void internal_transition_of_composite_keeps_substate() {
		LogComposite a = buildNestedChart();
		a.on(m_toY, Transitions.internal((ctx, sig) -> m_log.add("action")));
		((LogState)m_chart.findState("/A/X").orElseThrow()).trans.clear();		// /A/X는 toY를 처리하지 않는다.
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertEquals(List.of("action"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/X"), activePaths());
	}

	@Test
	public void internal_transition_action_failure_fails_chart() throws Exception {
		IllegalStateException error = new IllegalStateException("action failure");
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.internal((ctx, sig) -> { throw error; })));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		// 다른 전이처럼 예외는 흡수되고 차트가 실패한다.
		Assertions.assertTrue(m_chart.handleSignal(m_toY).isPresent());

		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		// 내부 전이라 진출·진입은 없지만, 차트가 실패하면 진입해 있던 /A를 정리(진출)한다.
		Assertions.assertEquals(List.of("exit:/A"), m_log);
	}

	@Test
	public void explicit_transition_to_current_state_reenters_it_unlike_internal() {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.to("/A", (ctx, sig) -> m_log.add("action"))));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertEquals(List.of("exit:/A", "action", "enter:/A"), m_log);
	}

	// ---- Transitions.fail ----

	private record Failed(Throwable cause) implements Signal { }

	private void buildFailChart(Transition<TestCtx> toError) {
		m_chart.addState(new LogState("/A").on(m_toDone, toError));
		m_chart.addState(new FailureState<>("/Error", m_ctx));
		m_chart.addState(new LogState("/B"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Error");
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
	}

	@Test
	public void transitions_fail_sets_cause_and_fails_chart() throws Exception {
		IOException cause = new IOException("disconnected");
		m_chart.addState(new LogState("/A") {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				return (sig instanceof Failed)
						? Optional.of(Transitions.fail("/Error", s -> ((Failed)s).cause()))
						: Optional.empty();
			}
		});
		m_chart.addState(new FailureState<>("/Error", m_ctx));
		m_chart.addFinalState("/Error");
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(new Failed(cause));

		Assertions.assertSame(cause, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals("/Error", m_chart.getCurrentState().getPath());
	}

	@Test
	public void transitions_fail_to_non_failure_state_fails_chart_with_reason() throws Exception {
		buildFailChart(Transitions.fail("/B", sig -> new IOException("never used")));

		m_chart.handleSignal(m_toDone);

		Throwable cause = m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause();
		Assertions.assertTrue(cause instanceof IllegalStateException, "" + cause);
		Assertions.assertTrue(cause.getMessage().contains("not a FailureState"), cause.getMessage());
		Assertions.assertTrue(cause.getMessage().contains("/B"), cause.getMessage());
	}

	@Test
	public void transitions_fail_with_null_cause_fails_chart_with_reason() throws Exception {
		buildFailChart(Transitions.fail("/Error", sig -> null));

		m_chart.handleSignal(m_toDone);

		Throwable cause = m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause();
		Assertions.assertTrue(cause instanceof IllegalStateException, "" + cause);
		Assertions.assertTrue(cause.getMessage().contains("returned null"), cause.getMessage());
	}

	// ---- 동시성 스트레스 ----

	@Test
	public void concurrent_signals_are_all_processed_one_at_a_time() throws Exception {
		int threads = 8;
		int perThread = 200;
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger maxInFlight = new AtomicInteger();
		AtomicInteger processed = new AtomicInteger();
		CountedState a = new CountedState("a", m_ctx) {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				return Optional.of(Transitions.internal((ctx, s) -> {
					int n = inFlight.incrementAndGet();
					maxInFlight.accumulateAndGet(n, Math::max);
					processed.incrementAndGet();
					inFlight.decrementAndGet();
				}));
			}
		};
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("b", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		m_chart.start();

		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<?>> senders = new ArrayList<>();
			for ( int t = 0; t < threads; ++t ) {
				boolean post = (t % 2 == 0);
				senders.add(executor.submit(() -> {
					go.await();
					for ( int i = 0; i < perThread; ++i ) {
						if ( post ) {
							m_chart.postSignal(new Signal() {});
						}
						else {
							m_chart.handleSignal(new Signal() {});
						}
					}
					return null;
				}));
			}
			go.countDown();
			for ( Future<?> f: senders ) {
				f.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}
		// postSignal()로 넣은 신호는 다른 스레드가 처리 중일 수 있다. 신호 하나를 동기로 보내 큐가 빌 때까지 기다린다.
		Assertions.assertTrue(m_chart.processSignal(new Signal() {}).isTransitioned());

		Assertions.assertEquals(threads * perThread + 1, processed.get());
		Assertions.assertEquals(1, maxInFlight.get());
		Assertions.assertTrue(m_chart.isRunning());
	}

	@Test
	public void start_racing_cancel_always_terminates() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for ( int i = 0; i < 100; ++i ) {
				TestCtx ctx = new TestCtx();
				StateChart<TestCtx> chart = new StateChart<>(ctx);
				chart.addState(new CountedState("a", ctx));
				chart.addState(new TerminalState<>("b", ctx));
				chart.setInitialState("a");
				chart.addFinalState("b");

				CountDownLatch go = new CountDownLatch(1);
				Future<?> starter = executor.submit(() -> {
					go.await();
					try {
						chart.start();
					}
					catch ( IllegalStateException expected ) {
						// 취소가 먼저 끝나면 시작이 거부된다.
					}
					return null;
				});
				Future<Boolean> canceller = executor.submit(() -> {
					go.await();
					return chart.cancel(true);
				});
				go.countDown();
				starter.get(5, TimeUnit.SECONDS);
				canceller.get(5, TimeUnit.SECONDS);

				// 어느 쪽이 먼저든 교착 없이 끝난다. 시작이 이겼다면 다시 취소해 끝까지 가는지 확인한다.
				if ( chart.isRunning() ) {
					Assertions.assertTrue(chart.cancel(true), "round " + i);
				}
				Assertions.assertTrue(chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled(), "round " + i);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	// ---- 복합 상태 ----

	private final List<String> m_log = new ArrayList<>();
	private final Signal m_toY = new Signal() { public String toString() { return "toY"; } };
	private final Signal m_toDone = new Signal() { public String toString() { return "toDone"; } };
	private final Signal m_reset = new Signal() { public String toString() { return "reset"; } };

	/** 진입·진출을 {@link #m_log}에 기록하는 상태. */
	private class LogState extends AbstractState<TestCtx> {
		final Map<Signal, Transition<TestCtx>> trans = new HashMap<>();

		LogState(String path) { super(path, m_ctx); }

		@Override public LogState on(Signal signal, Transition<TestCtx> transition) {
			trans.put(signal, transition);
			return this;
		}

		@Override public void enter() { m_log.add("enter:" + getPath()); }
		@Override public void exit() { m_log.add("exit:" + getPath()); }
		@Override public Optional<Transition<TestCtx>> selectTransition(Signal s) {
			return Optional.ofNullable(trans.get(s));
		}
	}

	/** 진입·진출을 {@link #m_log}에 기록하는 복합 상태. */
	private class LogComposite extends DefaultCompositeState<TestCtx> {
		final Map<Signal, Transition<TestCtx>> trans = new HashMap<>();

		LogComposite(String path, String initialStatePath) { super(path, m_ctx, initialStatePath); }

		@Override public LogComposite on(Signal signal, Transition<TestCtx> transition) {
			trans.put(signal, transition);
			return this;
		}

		@Override public void enter() { m_log.add("enter:" + getPath()); }
		@Override public void exit() { m_log.add("exit:" + getPath()); }
		@Override public Optional<Transition<TestCtx>> selectTransition(Signal s) {
			return Optional.ofNullable(trans.get(s));
		}
	}

	/**
	 * {@code /A}(복합, 최초 {@code /A/X}) 아래 {@code /A/X}, {@code /A/Y}와 최상위 {@code /B}, 최종 {@code /Done}을 등록한다.
	 * {@code /A/X}는 {@code toY}로 {@code /A/Y}에, {@code /A}는 {@code toDone}으로 {@code /Done}에,
	 * {@code reset}으로 자기 자신에게 간다.
	 */
	private LogComposite buildNestedChart() {
		LogComposite a = new LogComposite("/A", "/A/X")
								.on(m_toDone, Transitions.to("/Done"))
								.on(m_reset, Transitions.to("/A"));
		m_chart.addState(a);
		m_chart.addState(new LogState("/A/X").on(m_toY, Transitions.to("/A/Y")));
		m_chart.addState(new LogState("/A/Y"));
		m_chart.addState(new LogState("/B"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		return a;
	}

	private List<String> activePaths() {
		return m_chart.getActiveStates().stream().map(State::getPath).toList();
	}

	@Test
	public void start_enters_composite_and_its_initial_substate() {
		buildNestedChart();
		m_chart.setInitialState("/A");
		m_chart.start();

		Assertions.assertEquals(List.of("enter:/A", "enter:/A/X"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/X"), activePaths());
		Assertions.assertEquals("/A/X", m_chart.getCurrentState().getPath());
	}

	@Test
	public void start_with_nested_initial_state_enters_its_ancestors_first() {
		buildNestedChart();
		m_chart.setInitialState("/A/Y");
		m_chart.start();

		Assertions.assertEquals(List.of("enter:/A", "enter:/A/Y"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/Y"), activePaths());
	}

	@Test
	public void transition_between_substates_keeps_composite() {
		buildNestedChart();
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertTrue(m_chart.handleSignal(m_toY).isPresent());

		Assertions.assertEquals(List.of("exit:/A/X", "enter:/A/Y"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/Y"), activePaths());
	}

	@Test
	public void composite_handles_signal_not_handled_by_substate() throws Exception {
		buildNestedChart();
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		// /A/X는 toDone을 처리하지 않으므로 /A의 전이가 선택된다.
		Assertions.assertTrue(m_chart.handleSignal(m_toDone).isPresent());

		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
		Assertions.assertEquals(List.of("/Done"), activePaths());
		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void substate_transition_takes_priority_over_composite() {
		LogComposite a = buildNestedChart();
		a.on(m_toY, Transitions.to("/B"));		// /A/X도 toY를 처리한다.
		m_chart.setInitialState("/A");
		m_chart.start();

		m_chart.handleSignal(m_toY);

		Assertions.assertEquals(List.of("/A", "/A/Y"), activePaths());
	}

	@Test
	public void transition_into_composite_enters_down_to_initial_substate() {
		buildNestedChart();
		((LogState)m_chart.findState("/B").orElseThrow()).on(m_toY, Transitions.to("/A"));
		m_chart.setInitialState("/B");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertEquals(List.of("exit:/B", "enter:/A", "enter:/A/X"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/X"), activePaths());
	}

	@Test
	public void transition_to_active_composite_reenters_it() {
		buildNestedChart();
		m_chart.setInitialState("/A");
		m_chart.start();
		m_chart.handleSignal(m_toY);
		m_log.clear();

		m_chart.handleSignal(m_reset);

		Assertions.assertEquals(List.of("exit:/A/Y", "exit:/A", "enter:/A", "enter:/A/X"), m_log);
		Assertions.assertEquals(List.of("/A", "/A/X"), activePaths());
	}

	@Test
	public void transition_action_runs_between_exits_and_entries() {
		buildNestedChart();
		((LogState)m_chart.findState("/A/X").orElseThrow())
				.on(m_toY, Transitions.to("/B", (ctx, sig) -> m_log.add("action")));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A", "action", "enter:/B"), m_log);
	}

	@Test
	public void deeply_nested_states_enter_and_exit_in_order() {
		m_chart.addState(new LogComposite("/A", "/A/B"));
		m_chart.addState(new LogComposite("/A/B", "/A/B/C"));
		m_chart.addState(new LogState("/A/B/C").on(m_toDone, Transitions.to("/D")));
		m_chart.addState(new LogState("/D"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();

		Assertions.assertEquals(List.of("enter:/A", "enter:/A/B", "enter:/A/B/C"), m_log);
		m_log.clear();

		m_chart.handleSignal(m_toDone);

		Assertions.assertEquals(List.of("exit:/A/B/C", "exit:/A/B", "exit:/A", "enter:/D"), m_log);
		Assertions.assertEquals(List.of("/D"), activePaths());
	}

	@Test
	public void final_substate_completes_chart() throws Exception {
		buildNestedChart();
		m_chart.addState(new TerminalState<>("/A/End", m_ctx));
		m_chart.addFinalState("/A/End");
		((LogState)m_chart.findState("/A/X").orElseThrow()).on(m_toY, Transitions.to("/A/End"));
		m_chart.setInitialState("/A");
		m_chart.start();

		m_log.clear();
		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		// 종료하기 전에 최종 상태를 둘러싼 /A를 진출한다. 최종 상태만 진입한 채 남는다.
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
		Assertions.assertEquals(List.of("/A/End"), activePaths());
	}

	@Test
	public void enclosing_states_are_exited_before_finish_listeners_run() throws Exception {
		// /O(복합) > /O/I(복합) > /O/I/X 에서 /O/I/End(최종)로 간다.
		m_chart.addState(new LogComposite("/O", "/O/I"));
		m_chart.addState(new LogComposite("/O/I", "/O/I/X"));
		m_chart.addState(new LogState("/O/I/X").on(m_toDone, Transitions.to("/O/I/End")));
		m_chart.addState(new TerminalState<>("/O/I/End", m_ctx));
		m_chart.addFinalState("/O/I/End");
		m_chart.setInitialState("/O");
		List<String> seenByListener = new ArrayList<>();
		m_chart.whenFinished(r -> seenByListener.addAll(m_log));
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toDone);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		List<String> expected = List.of("exit:/O/I/X", "exit:/O/I", "exit:/O");
		Assertions.assertEquals(expected, m_log);
		Assertions.assertEquals(expected, seenByListener);
		Assertions.assertEquals(List.of("/O/I/End"), activePaths());
	}

	@Test
	public void enclosing_states_are_exited_before_failing_at_nested_failure_state() throws Exception {
		IllegalStateException cause = new IllegalStateException("job failed");
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new LogState("/A/X").on(m_toDone, Transitions.fail("/A/Error", sig -> cause)));
		m_chart.addState(new FailureState<>("/A/Error", m_ctx));
		m_chart.addFinalState("/A/Error");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toDone);

		Assertions.assertSame(cause, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
	}

	@Test
	public void failing_exit_of_enclosing_state_does_not_stop_completion() throws Exception {
		m_chart.addState(new LogComposite("/O", "/O/I"));
		m_chart.addState(new LogComposite("/O/I", "/O/I/X") {
			@Override public void exit() {
				super.exit();
				throw new IllegalStateException("exit failed");
			}
		});
		m_chart.addState(new LogState("/O/I/X").on(m_toDone, Transitions.to("/O/I/End")));
		m_chart.addState(new TerminalState<>("/O/I/End", m_ctx));
		m_chart.addFinalState("/O/I/End");
		m_chart.setInitialState("/O");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toDone);

		// 취소할 때와 같이, 진출 실패는 경고만 남기고 나머지 바깥 상태의 진출과 종료를 계속한다.
		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		Assertions.assertEquals(List.of("exit:/O/I/X", "exit:/O/I", "exit:/O"), m_log);
		Assertions.assertEquals(List.of("/O/I/End"), activePaths());
	}

	@Test
	public void cancel_exits_all_active_states_innermost_first() throws Exception {
		buildNestedChart();
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertTrue(m_chart.cancel(true));

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
		Assertions.assertNull(m_chart.getCurrentState());
	}

	@Test
	public void cancel_exits_outer_states_even_if_inner_exit_throws() throws Exception {
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new LogState("/A/X") {
			@Override public void exit() { throw new IllegalStateException("exit failure"); }
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertTrue(m_chart.cancel(true));

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(List.of("exit:/A"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void flat_chart_has_single_active_state() {
		startSimpleChart();

		Assertions.assertEquals(List.of("a"), activePaths());
	}

	// ---- 복합 상태 구성 검증 ----

	@Test
	public void start_rejects_unregistered_parent() {
		m_chart.addState(new LogState("/A/X"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A/X");

		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, m_chart::start);
		Assertions.assertTrue(e.getMessage().contains("/A/X"), e.getMessage());
		Assertions.assertEquals(AsyncState.NOT_STARTED, m_chart.getState());
	}

	@Test
	public void start_rejects_parent_that_is_not_composite() {
		m_chart.addState(new LogState("/A"));
		m_chart.addState(new LogState("/A/X"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
	}

	@Test
	public void start_rejects_unregistered_initial_substate() {
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
	}

	@Test
	public void start_rejects_initial_substate_that_is_not_direct_child() {
		m_chart.addState(new LogComposite("/A", "/B"));
		m_chart.addState(new LogState("/B"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.addFinalState("/Done");
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
	}

	@Test
	public void start_rejects_composite_final_state() {
		buildNestedChart();
		m_chart.addFinalState("/A");
		m_chart.setInitialState("/B");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
	}

	@Test
	public void start_rejects_composite_initial_state_whose_initial_substate_is_final() {
		m_chart.addState(new LogComposite("/A", "/A/End"));
		m_chart.addState(new TerminalState<>("/A/End", m_ctx));
		m_chart.addFinalState("/A/End");
		m_chart.setInitialState("/A");

		Assertions.assertThrows(IllegalStateException.class, m_chart::start);
	}

	// ---- 전이 도중 종료 ----

	/** {@code /A}(toY → {@code /B}), {@code /B}, 최종 {@code /Done}을 등록하고 {@code /A}에서 시작한다. */
	private void startFlatChart(LogState a, LogState b) {
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();
	}

	@Test
	public void cancel_in_transition_action_stops_before_entering_target() throws Exception {
		LogState a = new LogState("/A").on(m_toY, Transitions.to("/B", (ctx, sig) -> {
			m_log.add("action");
			ctx.getStateChart().cancel(true);
		}));
		startFlatChart(a, new LogState("/B"));

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		// 취소된 뒤에는 /B에 진입하지 않는다.
		Assertions.assertEquals(List.of("exit:/A", "action"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void cancel_in_exit_skips_action_and_exits_each_state_once() throws Exception {
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new LogState("/A/X") {
			@Override public void exit() {
				super.exit();
				m_chart.cancel(true);
			}
		}.on(m_toY, Transitions.to("/B", (ctx, sig) -> m_log.add("action"))));
		m_chart.addState(new LogState("/B"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		// /A/X는 한 번만 진출하고, 취소가 /A를 진출시킨다. 전이 액션과 /B 진입은 일어나지 않는다.
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void cancel_in_enter_exits_that_state() throws Exception {
		LogState b = new LogState("/B") {
			@Override public void enter() {
				super.enter();
				m_chart.cancel(true);
			}
		};
		startFlatChart(new LogState("/A").on(m_toY, Transitions.to("/B")), b);

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		// 자기 enter()에서 취소한 상태도 진출시켜, 진입한 상태가 남지 않게 한다.
		Assertions.assertEquals(List.of("exit:/A", "enter:/B", "exit:/B"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());

		// 다시 취소해도 진출은 더 일어나지 않는다.
		m_chart.cancel(true);
		Assertions.assertEquals(List.of("exit:/A", "enter:/B", "exit:/B"), m_log);
	}

	@Test
	public void cancel_in_composite_enter_does_not_enter_substate() throws Exception {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.to("/W")));
		m_chart.addState(new LogComposite("/W", "/W/X") {
			@Override public void enter() {
				super.enter();
				m_chart.cancel(true);
			}
		});
		m_chart.addState(new LogState("/W/X"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(List.of("exit:/A", "enter:/W", "exit:/W"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void complete_in_transition_action_stops_before_entering_target() throws Exception {
		LogState a = new LogState("/A").on(m_toY, Transitions.to("/B", (ctx, sig) -> {
			m_log.add("action");
			ctx.getStateChart().complete();
		}));
		startFlatChart(a, new LogState("/B"));

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		Assertions.assertEquals(List.of("exit:/A", "action"), m_log);
	}

	@Test
	public void addState_rejects_state_of_another_context() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_chart.addState(new CountedState("a", new TestCtx())));
		Assertions.assertTrue(m_chart.findState("a").isEmpty());
	}

	// ---- 종료 요청: 접수 → 콜백 반환 → 정리 → 통지 ----

	@Test
	public void cancel_in_child_enter_exits_child_then_parent_before_listener() throws Exception {
		AtomicBoolean accepted = new AtomicBoolean();
		m_chart.addState(new LogComposite("/P", "/P/A"));
		m_chart.addState(new LogState("/P/A").on(m_toY, Transitions.to("/P/B")));
		m_chart.addState(new LogState("/P/B") {
			@Override public void enter() {
				super.enter();
				accepted.set(m_chart.cancel(true));
				m_log.add("cancel-returned");
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/P");
		m_chart.whenFinished(r -> m_log.add("listener"));
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(accepted.get());
		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		// 취소는 enter()가 반환된 뒤 자식 → 부모 순으로 정리하고, 그 다음에 종료 리스너가 실행된다.
		Assertions.assertEquals(List.of("exit:/P/A", "enter:/P/B", "cancel-returned",
										"exit:/P/B", "exit:/P", "listener"), m_log);
	}

	@Test
	public void termination_requested_in_cleanup_exit_does_not_override_failure() throws Exception {
		IllegalStateException error = new IllegalStateException("action failed");
		AtomicBoolean cancelAccepted = new AtomicBoolean(true);
		m_chart.addState(new LogState("/A") {
			@Override public void exit() {
				super.exit();
				cancelAccepted.set(m_chart.cancel(true));
				m_chart.complete();		// 이미 실패가 접수되어 거부된다(IllegalStateException은 진출 실패로 기록).
			}
		}.on(m_toY, Transitions.internal((ctx, sig) -> { throw error; })));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertEquals(SignalResult.Status.FAILED, m_chart.processSignal(m_toY).status());

		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertFalse(cancelAccepted.get());
		Assertions.assertEquals(List.of("exit:/A"), m_log);
	}

	@Test
	@org.junit.jupiter.api.Timeout(value = 2, unit = TimeUnit.SECONDS)
	public void cancel_in_initial_enter_is_applied_after_start_without_waiting() throws Exception {
		AtomicBoolean accepted = new AtomicBoolean();
		m_chart.addState(new LogState("/A") {
			@Override public void enter() {
				super.enter();
				// 부모 구현은 STARTING이 끝나기를 기다리다 제한 시간(기본 3초) 뒤 실패했다.
				accepted.set(m_chart.cancel(true));
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.whenFinished(r -> m_log.add("listener"));

		m_chart.start();

		Assertions.assertTrue(accepted.get());
		Assertions.assertEquals(AsyncState.CANCELLED, m_chart.getState());
		Assertions.assertEquals(List.of("enter:/A", "exit:/A", "listener"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void start_listener_cannot_override_accepted_cancel() throws Exception {
		AtomicBoolean completeRejected = new AtomicBoolean();
		m_chart.addState(new LogState("/A") {
			@Override public void enter() {
				super.enter();
				m_chart.cancel(true);
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		// 취소를 확정하기 전에 시작을 확정하면서 실행되는 리스너. 이미 접수된 취소를 바꾸지 못해야 한다.
		m_chart.whenStarted(() -> {
			try {
				m_chart.complete();
			}
			catch ( IllegalStateException expected ) {
				completeRejected.set(true);
			}
		});

		m_chart.start();

		Assertions.assertTrue(completeRejected.get());
		Assertions.assertEquals(AsyncState.CANCELLED, m_chart.getState());
		Assertions.assertEquals(List.of("enter:/A", "exit:/A"), m_log);
	}

	@Test
	public void sneaky_checked_exception_from_selectTransition_reaches_processSignal_caller() throws Exception {
		IOException cause = new IOException("select failed");
		m_chart.addState(new LogState("/A") {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				return utils.Throwables.sneakyThrow(cause);
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		utils.RuntimeExecutionException thrown = Assertions.assertThrows(utils.RuntimeExecutionException.class,
																	() -> m_chart.processSignal(m_toY));
		Assertions.assertSame(cause, thrown.getCause());
		Assertions.assertSame(cause, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void sneaky_checked_exception_from_selectTransition_fails_posted_future() throws Exception {
		IOException cause = new IOException("select failed");
		m_chart.addState(new LogState("/A") {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				return utils.Throwables.sneakyThrow(cause);
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		CompletableFuture<SignalResult<TestCtx>> future = m_chart.postSignal(m_toY);

		java.util.concurrent.ExecutionException thrown
				= Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
		Assertions.assertSame(cause, thrown.getCause());
	}

	@Test
	public void complete_in_initial_enter_completes_after_start() throws Exception {
		m_chart.addState(new LogState("/A") {
			@Override public void enter() {
				super.enter();
				m_chart.complete();
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");

		m_chart.start();

		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
		// complete()를 직접 호출한 종료이므로 상태를 진출하지 않는다(취소와 다르다).
		Assertions.assertEquals(List.of("enter:/A"), m_log);
	}

	@Test
	public void fatal_error_in_cleanup_exit_propagates_after_failure() throws Exception {
		IllegalStateException error = new IllegalStateException("action failed");
		InternalError fatal = new InternalError("exit crashed");
		m_chart.addState(new LogState("/A") {
			@Override public void exit() {
				super.exit();
				throw fatal;
			}
		}.on(m_toY, Transitions.internal((ctx, sig) -> { throw error; })));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		InternalError thrown = Assertions.assertThrows(InternalError.class, () -> m_chart.handleSignal(m_toY));

		Assertions.assertSame(fatal, thrown);
		// 원래 실패 원인으로 확정한 뒤 치명 오류를 다시 던진다.
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	// ---- 실패 시 정리 ----

	@Test
	public void processSignal_reports_FAILED_when_transition_throws() throws Exception {
		IllegalStateException error = new IllegalStateException("action failed");
		Transition<TestCtx> failing = Transitions.to("/B", (ctx, sig) -> { throw error; });
		startFlatChart(new LogState("/A").on(m_toY, failing), new LogState("/B"));

		SignalResult<TestCtx> result = m_chart.processSignal(m_toY);

		Assertions.assertEquals(SignalResult.Status.FAILED, result.status());
		Assertions.assertFalse(result.isTransitioned());
		Assertions.assertSame(failing, result.getTransition().orElseThrow());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void reaching_failureState_is_TRANSITIONED_not_FAILED() throws Exception {
		Signal toError = new Signal() {};
		IllegalStateException cause = new IllegalStateException("job failed");
		m_chart.addState(new LogState("/A").on(toError, Transitions.fail("/Error", sig -> cause)));
		m_chart.addState(new FailureState<>("/Error", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();

		// 전이는 정상 수행되었고, 도달한 상태가 차트를 실패로 끝낸 것이다.
		Assertions.assertEquals(SignalResult.Status.TRANSITIONED, m_chart.processSignal(toError).status());
		Assertions.assertSame(cause, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
	}

	@Test
	public void start_failure_exits_entered_outer_states_and_keeps_cause() throws Exception {
		IllegalStateException error = new IllegalStateException("child enter failed");
		m_chart.addState(new LogComposite("/W", "/W/X"));
		m_chart.addState(new LogState("/W/X") {
			@Override public void enter() {
				super.enter();
				throw error;
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/W");

		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, m_chart::start);

		Assertions.assertSame(error, thrown.getCause());
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		// 진입을 마친 /W는 진출한다. enter()가 실패한 /W/X는 진입하지 않은 것으로 보아 진출시키지 않는다.
		Assertions.assertEquals(List.of("enter:/W", "enter:/W/X", "exit:/W"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void transition_failure_exits_remaining_active_states() throws Exception {
		IllegalStateException error = new IllegalStateException("action failed");
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new LogState("/A/X").on(m_toY, Transitions.to("/A/Y", (ctx, sig) -> { throw error; })));
		m_chart.addState(new LogState("/A/Y"));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		// /A/X는 전이 과정에서 이미 진출했다. 실패하면 남아 있던 /A도 진출한다.
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
		Assertions.assertTrue(m_chart.getActiveStates().isEmpty());
	}

	@Test
	public void selectTransition_failure_exits_active_states() throws Exception {
		IllegalStateException error = new IllegalStateException("select failed");
		m_chart.addState(new LogComposite("/A", "/A/X"));
		m_chart.addState(new LogState("/A/X") {
			@Override public Optional<Transition<TestCtx>> selectTransition(Signal sig) {
				throw error;
			}
		});
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		Assertions.assertSame(error, Assertions.assertThrows(IllegalStateException.class,
															() -> m_chart.handleSignal(m_toY)));
		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
	}

	@Test
	public void exit_failure_during_cleanup_keeps_original_cause() throws Exception {
		IllegalStateException error = new IllegalStateException("action failed");
		m_chart.addState(new LogComposite("/A", "/A/X") {
			@Override public void exit() {
				super.exit();
				throw new IllegalStateException("exit failed");
			}
		});
		m_chart.addState(new LogState("/A/X").on(m_toY, Transitions.internal((ctx, sig) -> { throw error; })));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		m_chart.handleSignal(m_toY);

		Assertions.assertSame(error, m_chart.waitForFinished(2, TimeUnit.SECONDS).getFailureCause());
		Assertions.assertEquals(List.of("exit:/A/X", "exit:/A"), m_log);
	}

	@Test
	public void internal_action_cancelling_then_throwing_does_not_break() throws Exception {
		m_chart.addState(new LogState("/A").on(m_toY, Transitions.internal((ctx, sig) -> {
			ctx.getStateChart().cancel(true);
			throw new IllegalStateException("after cancel");
		})));
		m_chart.addState(new TerminalState<>("/Done", m_ctx));
		m_chart.setInitialState("/A");
		m_chart.start();
		m_log.clear();

		// 취소로 현재 상태가 비어도 실패 처리의 로그가 NPE를 내지 않는다. 차트는 먼저 확정된 취소로 끝난다.
		m_chart.handleSignal(m_toY);

		Assertions.assertTrue(m_chart.waitForFinished(2, TimeUnit.SECONDS).isCancelled());
		Assertions.assertEquals(List.of("exit:/A"), m_log);
	}

	// ---- 구성 검증 ----

	@Test
	public void addState_rejects_invalid_path() {
		for ( String path: java.util.List.of("", "/", "/A/", "//A", "/A//B") ) {
			Assertions.assertThrows(IllegalArgumentException.class,
									() -> m_chart.addState(new CountedState(path, m_ctx)), path);
		}
		Assertions.assertTrue(m_chart.findState("/A/").isEmpty());
	}

	@Test
	public void configuration_lookups_do_not_wait_for_chart_lock() throws Exception {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		m_chart.setInitialState("a");

		// 다른 스레드가 차트 lock을 쥐고 있어도 구성 조회는 기다리지 않는다. 실행 상태 lock만 쥔 코드에서
		// 불러도 lock 순서가 뒤집히지 않게 하기 위해서다.
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			m_chart.getChartLock().lock();
			try {
				locked.countDown();
				release.await();
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
			}
			finally {
				m_chart.getChartLock().unlock();
			}
		});
		holder.start();
		Assertions.assertTrue(locked.await(2, TimeUnit.SECONDS));
		try {
			CompletableFuture<Boolean> lookups = CompletableFuture.supplyAsync(
					() -> m_chart.findState("a").orElseThrow() == a && m_chart.getInitialState() == a);
			Assertions.assertTrue(lookups.get(2, TimeUnit.SECONDS));
		}
		finally {
			release.countDown();
			holder.join();
		}
	}

	@Test
	public void getInitialState_returns_configured_state() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		Assertions.assertNull(m_chart.getInitialState());

		m_chart.setInitialState("a");

		Assertions.assertSame(a, m_chart.getInitialState());
	}

	@Test
	public void name_defaults_to_class_and_identity_and_can_be_set() {
		Assertions.assertTrue(m_chart.getName().startsWith("StateChart@"), m_chart.getName());
		Assertions.assertTrue(m_chart.toString().startsWith(m_chart.getName() + "["), m_chart.toString());

		m_chart.setName("job-42");
		Assertions.assertEquals("job-42", m_chart.getName());
		Assertions.assertTrue(m_chart.toString().startsWith("job-42["), m_chart.toString());

		m_chart.setName(null);
		Assertions.assertTrue(m_chart.getName().startsWith("StateChart@"), m_chart.getName());

		// 익명 하위 클래스도 이름이 비지 않는다.
		StateChart<TestCtx> anonymous = new StateChart<>(new TestCtx()) { };
		Assertions.assertTrue(anonymous.getName().startsWith("StateChart@"), anonymous.getName());
	}

	@Test
	public void addState_same_path_rejected_and_keeps_first() {
		CountedState first = new CountedState("a", m_ctx);
		m_chart.addState(first);

		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_chart.addState(new CountedState("a", m_ctx)));
		Assertions.assertSame(first, m_chart.findState("a").orElseThrow());
	}

	@Test
	public void terminalState_becomes_final_state_when_added() {
		CountedState a = new CountedState("a", m_ctx);
		Signal toSink = new Signal() {};
		a.trans.put(toSink, Transitions.to("sink"));
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("sink", m_ctx));
		m_chart.addState(new FailureState<>("error", m_ctx));		// FailureState도 TerminalState다.
		m_chart.setInitialState("a");

		// addFinalState()를 부르지 않아도 TerminalState가 최종 상태이므로 시작할 수 있다.
		m_chart.start();
		m_chart.handleSignal(toSink);

		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
	}

	@Test
	public void start_rejects_terminalState_as_initial_state() {
		m_chart.addState(new TerminalState<>("sink", m_ctx));
		m_chart.setInitialState("sink");

		// 자동으로 최종 상태가 되므로, 시작 상태로 쓰면 "시작 상태는 최종 상태일 수 없다"로 거부된다.
		Assertions.assertThrows(IllegalStateException.class, () -> m_chart.start());
		Assertions.assertEquals(AsyncState.NOT_STARTED, m_chart.getState());
	}

	@Test
	public void start_accepts_terminalStates_registered_as_final() {
		CountedState a = new CountedState("a", m_ctx);
		m_chart.addState(a);
		m_chart.addState(new TerminalState<>("done", m_ctx));
		m_chart.addState(new FailureState<>("error", m_ctx));
		m_chart.setInitialState("a");
		m_chart.addFinalState("done");
		m_chart.addFinalState("error");

		m_chart.start();
		Assertions.assertEquals(AsyncState.RUNNING, m_chart.getState());
	}

	// ---- toString / lock 순서 ----

	@Test
	public void toString_does_not_wait_for_chart_lock() throws Exception {
		startSimpleChart();

		withChartLockHeldByAnotherThread(() -> {
			// 차트 lock을 잡으면 다른 스레드가 놓을 때까지 블록되어 시간 초과가 난다.
			String str = CompletableFuture.supplyAsync(() -> m_chart.toString()).get(2, TimeUnit.SECONDS);
			Assertions.assertTrue(str.contains("state="), str);
		});
	}

	@Test
	public void cancel_before_start_does_not_wait_for_chart_lock() throws Exception {
		// 시작 전 cancel()은 부모의 실행 상태 lock 안에서 로그로 this를 출력한다. toString()이 차트 lock을
		// 잡으면, 차트 lock을 잡은 채 실행 상태 lock을 기다리는 start()와 교착될 수 있었다.
		withChartLockHeldByAnotherThread(() -> {
			Boolean cancelled = CompletableFuture.supplyAsync(() -> m_chart.cancel(true))
												.get(2, TimeUnit.SECONDS);
			Assertions.assertTrue(cancelled);
			Assertions.assertEquals(AsyncState.CANCELLED, m_chart.getState());
		});
	}

	@Test
	public void sync_finish_listener_runs_while_chart_lock_is_held() throws Exception {
		// 동기 종료 리스너는 차트 lock을 잡은 채 실행된다(README 7절). 리스너 안에서 다른 스레드가
		// 차트 lock이 필요한 호출을 하면 리스너가 끝날 때까지 기다린다.
		CountedState a = new CountedState("a", m_ctx);
		CountedState b = new CountedState("b", m_ctx);
		Signal toB = new Signal() { };
		a.trans.put(toB, Transitions.to("b"));
		m_chart.addState(a);
		m_chart.addState(b);
		m_chart.setInitialState("a");
		m_chart.addFinalState("b");
		AtomicBoolean otherThreadBlocked = new AtomicBoolean(false);
		AtomicBoolean sameThreadReentered = new AtomicBoolean(false);
		m_chart.whenFinished(result -> {
			// 같은 스레드는 lock을 다시 얻을 수 있다(재진입).
			sameThreadReentered.set(m_chart.getCurrentState() == b);
			try {
				CompletableFuture.supplyAsync(() -> m_chart.getCurrentState()).get(300, TimeUnit.MILLISECONDS);
			}
			catch ( TimeoutException expected ) {
				otherThreadBlocked.set(true);
			}
			catch ( Exception e ) {
				throw new AssertionError(e);
			}
		});
		m_chart.start();

		m_chart.handleSignal(toB);

		Assertions.assertEquals(AsyncState.COMPLETED, m_chart.getState());
		Assertions.assertTrue(sameThreadReentered.get());
		Assertions.assertTrue(otherThreadBlocked.get());
	}

	/**
	 * 다른 스레드가 차트 lock을 잡고 있는 동안 주어진 작업을 수행한다.
	 */
	private void withChartLockHeldByAnotherThread(utils.func.CheckedRunnable work) throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			m_chart.getChartLock().lock();
			try {
				locked.countDown();
				release.await();
			}
			catch ( InterruptedException ignored ) { }
			finally {
				m_chart.getChartLock().unlock();
			}
		});
		holder.start();
		try {
			Assertions.assertTrue(locked.await(2, TimeUnit.SECONDS));
			work.run();
		}
		catch ( Throwable e ) {
			if ( e instanceof Exception ex ) {
				throw ex;
			}
			throw new AssertionError(e);
		}
		finally {
			release.countDown();
			holder.join(2000);
		}
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
