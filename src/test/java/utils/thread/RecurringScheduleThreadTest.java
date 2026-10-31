package utils.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.thread.RecurringSchedule.State;


/**
 * {@link RecurringScheduleThread}의 상태 보고, 시작·중지 계약을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class RecurringScheduleThreadTest {
	/** {@code perform()}마다 주어진 동작을 실행하는 작업. */
	private static class Work implements RecurringWork {
		volatile Runnable m_onPerform = () -> { };
		volatile Runnable m_onStarted = () -> { };
		volatile Throwable m_startFailure;
		final CountDownLatch m_stopped = new CountDownLatch(1);
		final AtomicInteger m_performCount = new AtomicInteger();

		@Override
		public void onStarted(RecurringSchedule schedule) throws Throwable {
			m_onStarted.run();
			if ( m_startFailure != null ) {
				throw m_startFailure;
			}
		}

		@Override
		public void onStopped() {
			m_stopped.countDown();
		}

		@Override
		public void perform() throws Exception {
			m_performCount.incrementAndGet();
			m_onPerform.run();
		}
	}

	/** 별도 데몬 스레드에서 실행한다. lock 교착은 JUnit 타임아웃으로 끊을 수 없으므로 결과를 시간 제한으로 기다린다. */
	private static CompletableFuture<Void> runAsync(CheckedRunnable task) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		Thread thread = new Thread(() -> {
			try {
				task.run();
				future.complete(null);
			}
			catch ( Throwable e ) {
				future.completeExceptionally(e);
			}
		});
		thread.setDaemon(true);
		thread.start();
		return future;
	}

	private interface CheckedRunnable {
		void run() throws Throwable;
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
	}

	private static void awaitState(RecurringSchedule schedule, State state) throws InterruptedException {
		long due = System.currentTimeMillis() + 5000;
		while ( schedule.getState() != state ) {
			if ( System.currentTimeMillis() > due ) {
				throw new AssertionError("state did not become " + state + ": " + schedule.getState());
			}
			Thread.sleep(1);
		}
	}

	@Test
	public void get_state_reports_working_while_performing() throws Exception {
		// 이전에는 작업 중(RUNNING_WORKING)도 IDLE로 보고해 WORKING이 나오지 않았다.
		Work work = new Work();
		CountDownLatch performing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		work.m_onPerform = () -> {
			performing.countDown();
			try {
				release.await();
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
			}
		};
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);

		assertEquals(State.STOPPED, schedule.getState());	// 시작 전 (이전에는 STARTING)
		schedule.start();
		performing.await();
		assertEquals(State.WORKING, schedule.getState());

		release.countDown();
		schedule.stop(true);
		assertTrue(schedule.waitForStopped(5000));
		assertEquals(State.STOPPED, schedule.getState());
	}

	@Test
	public void timed_wait_for_stopped_from_perform_releases_the_lock() throws Exception {
		// 이전에는 lock을 잡은 뒤 try 밖에서 예외를 던져, 작업 스레드가 lock을 쥔 채 남았다.
		Work work = new Work();
		AtomicReference<RecurringScheduleThread> self = new AtomicReference<>();
		CompletableFuture<Throwable> rejected = new CompletableFuture<>();
		work.m_onPerform = () -> {
			try {
				self.get().waitForStopped(100);
			}
			catch ( Throwable e ) {
				rejected.complete(e);
			}
		};
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		self.set(schedule);
		schedule.start();

		assertTrue(rejected.get(5, TimeUnit.SECONDS) instanceof RuntimeException);
		// lock이 풀렸다면 다른 스레드의 호출이 막히지 않는다.
		CompletableFuture<State> state = CompletableFuture.supplyAsync(schedule::getState);
		state.get(2, TimeUnit.SECONDS);
		schedule.stop(true);
		assertTrue(schedule.waitForStopped(5000));
	}

	@Test
	public void set_logger_uses_the_given_logger_and_null_restores_the_default() {
		// 이전에는 조건이 반대라 넘긴 로거를 무시하고, null을 넘기면 null이 들어갔다.
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", new Work(), 1000);
		Logger custom = LoggerFactory.getLogger("custom");

		schedule.setLogger(custom);
		assertSame(custom, schedule.getLogger());

		schedule.setLogger(null);
		assertSame(RecurringScheduleThread.s_logger, schedule.getLogger());
	}

	@Test
	public void start_throws_when_on_started_fails() throws Exception {
		// 이전에는 상태만 STOPPED로 바꾸고 정상 반환했다.
		Work work = new Work();
		IOException failure = new IOException("init failed");
		work.m_startFailure = failure;
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);

		ExecutionException e = assertThrows(ExecutionException.class, schedule::start);
		assertSame(failure, e.getCause());
		assertEquals(State.STOPPED, schedule.getState());
		assertSame(failure, schedule.getFailureCause());
	}

	@Test
	public void start_does_not_throw_when_the_first_perform_fails_quickly() throws Exception {
		// 첫 perform()이 곧바로 실패해 start()가 깨기 전에 STOPPED가 되어도 시작 실패로 보지 않는다.
		Work work = new Work();
		work.m_onPerform = () -> { throw new IllegalStateException("perform failed"); };
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);

		schedule.start();
		assertTrue(schedule.waitForStopped(5000));
		assertTrue(schedule.getFailureCause() instanceof IllegalStateException);
	}

	@Test
	public void stop_before_start_stops_immediately() throws Exception {
		// 이전에는 STOPPING에 영원히 머물거나(mayInterruptIfRunning=false) NPE가 났다(true).
		RecurringScheduleThread interrupting = RecurringScheduleThread.newFixedDelaySchedule("test", new Work(), 1000);
		interrupting.stop(true);
		assertEquals(State.STOPPED, interrupting.getState());
		assertTrue(interrupting.waitForStopped(1000));

		Work work = new Work();
		RecurringScheduleThread plain = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		plain.stop(false);
		assertEquals(State.STOPPED, plain.getState());
		plain.waitForStopped();
		assertEquals(1, work.m_stopped.getCount(), "onStopped() must not run when onStarted() never ran");
	}

	/** {@code interrupt()}가 수행 중인 {@code perform()}이 끝나기를 기다리는 작업. */
	private static class WaitingInterruptableWork extends Work implements InterruptableWork {
		final CountDownLatch m_release = new CountDownLatch(1);
		final CountDownLatch m_performDone = new CountDownLatch(1);

		@Override
		public void interrupt() {
			m_release.countDown();
			await(m_performDone);
		}
	}

	@Test
	public void stop_calls_interruptable_work_outside_the_lock() throws Exception {
		// 이전에는 lock을 쥔 채 interrupt()를 불러, 그것이 기다리는 perform()이 getState()에서 lock을 기다리며 교착되었다.
		WaitingInterruptableWork work = new WaitingInterruptableWork();
		AtomicReference<RecurringScheduleThread> self = new AtomicReference<>();
		CountDownLatch performing = new CountDownLatch(1);
		work.m_onPerform = () -> {
			performing.countDown();
			await(work.m_release);
			self.get().getState();
			work.m_performDone.countDown();
		};
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		self.set(schedule);
		schedule.start();
		performing.await();

		runAsync(() -> schedule.stop(false)).get(5, TimeUnit.SECONDS);
		assertTrue(schedule.waitForStopped(5000));
		assertEquals(0, work.m_stopped.getCount());
	}

	@Test
	public void stop_from_on_started_stops_without_performing() throws Exception {
		// 이전에는 stop()이 STARTING이 끝나기를 기다려, onStarted() 안에서 부르면 작업 스레드와 start() 호출자가 함께 멈췄다.
		Work work = new Work();
		AtomicReference<RecurringScheduleThread> self = new AtomicReference<>();
		work.m_onStarted = () -> self.get().stop(true);
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		self.set(schedule);

		runAsync(schedule::start).get(5, TimeUnit.SECONDS);
		assertTrue(schedule.waitForStopped(5000));
		assertEquals(0, work.m_performCount.get());
		assertEquals(0, work.m_stopped.getCount(), "onStopped() runs since onStarted() succeeded");
	}

	@Test
	public void stop_during_on_started_returns_without_waiting() throws Exception {
		Work work = new Work();
		CountDownLatch starting = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		work.m_onStarted = () -> {
			starting.countDown();
			await(release);
		};
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		CompletableFuture<Void> started = runAsync(schedule::start);
		starting.await();

		runAsync(() -> schedule.stop(false)).get(5, TimeUnit.SECONDS);
		assertEquals(State.STOPPING, schedule.getState());
		assertFalse(started.isDone(), "start() waits until onStarted() finishes");

		release.countDown();
		started.get(5, TimeUnit.SECONDS);
		assertTrue(schedule.waitForStopped(5000));
		assertEquals(0, work.m_performCount.get());
	}

	@Test
	public void invalid_intervals_are_rejected() {
		Work work = new Work();
		assertThrows(IllegalArgumentException.class, () -> RecurringScheduleThread.newFixedRateSchedule("test", work, 0));
		assertThrows(IllegalArgumentException.class, () -> RecurringScheduleThread.newFixedDelaySchedule("test", work, -1));
		assertThrows(IllegalArgumentException.class,
					() -> RecurringScheduleThread.newFixedDelaySchedule("test", work, -1, 1000));
		assertThrows(IllegalArgumentException.class, () -> RecurringScheduleThread.newFixedDelaySchedule("test", null, 1000));

		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 1000);
		assertThrows(IllegalArgumentException.class, () -> schedule.setInterval(0));
		assertEquals(1000, schedule.getInterval());
	}

	@Test
	public void a_huge_interval_does_not_overflow() throws Exception {
		// 이전에는 interval * 1000000이 넘쳐 음수가 되어, 기다리지 않고 perform()을 계속 반복했다.
		Work work = new Work();
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, Long.MAX_VALUE);
		schedule.start();
		awaitState(schedule, State.IDLE);
		Thread.sleep(200);
		assertEquals(1, work.m_performCount.get());

		schedule.stop(false);
		assertTrue(schedule.waitForStopped(5000));
	}

	@Test
	public void perform_now_reports_whether_the_request_was_accepted() throws Exception {
		Work work = new Work();
		CountDownLatch performing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		work.m_onPerform = () -> {
			if ( work.m_performCount.get() == 1 ) {
				performing.countDown();
				await(release);
			}
		};
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedDelaySchedule("test", work, 60_000);
		assertFalse(schedule.performNow());		// 시작 전

		schedule.start();
		performing.await();
		assertFalse(schedule.performNow());		// 수행 중에는 버린다
		release.countDown();

		awaitState(schedule, State.IDLE);
		assertTrue(schedule.performNow());
		long due = System.currentTimeMillis() + 5000;
		while ( work.m_performCount.get() < 2 && System.currentTimeMillis() < due ) {
			Thread.sleep(1);
		}
		assertEquals(2, work.m_performCount.get());

		schedule.stop(false);
		assertTrue(schedule.waitForStopped(5000));
		assertFalse(schedule.performNow());		// 종료 후
	}

	@Test
	public void perform_now_does_not_lengthen_the_next_fixed_rate_interval() throws Exception {
		// 이전에는 performNow()로 일찍 깨어난 대기도 지연 보정 표본(약 -interval)으로 기록해 다음 대기가 두 배가 되었다.
		Work work = new Work();
		List<Long> starts = new CopyOnWriteArrayList<>();
		work.m_onPerform = () -> starts.add(System.nanoTime());
		RecurringScheduleThread schedule = RecurringScheduleThread.newFixedRateSchedule("test", work, 300);
		schedule.start();
		awaitState(schedule, State.IDLE);

		assertTrue(schedule.performNow());
		long due = System.currentTimeMillis() + 5000;
		while ( starts.size() < 3 && System.currentTimeMillis() < due ) {
			Thread.sleep(1);
		}
		schedule.stop(false);
		assertTrue(schedule.waitForStopped(5000));

		long gapMillis = (starts.get(2) - starts.get(1)) / 1_000_000;
		assertTrue(gapMillis < 450, "the interval after performNow() should stay near 300ms: " + gapMillis + "ms");
	}
}
