package utils.async;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.RuntimeInterruptedException;
import utils.func.Unchecked;
import utils.thread.Timer;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class TimerTest {
	Timer m_timer;
	
	@BeforeEach
	public void setUp() {
		m_timer = new Timer();
	}
	
	@AfterEach
	public void tearDown() {
		m_timer.shutdown();
	}
	
	private StartableExecution<String> create(String ret, long timeout, boolean start) {
		StartableExecution<String> exec = Executions.supplyAsync(() -> {
			try {
				Thread.sleep(timeout);
			}
			catch ( InterruptedException e ) {
				throw new RuntimeInterruptedException(e);
			}
			return ret;
		});
		if ( start ) {
			exec.start();
		}
		return exec;
	}
	
	@Test
	public void cancels_an_execution_after_its_timeout() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		
		m_timer.setTimer(taskA, 300, TimeUnit.MILLISECONDS);
		taskA.waitForFinished();
		Assertions.assertEquals(true, taskA.isCancelled());
	}
	
	@Test
	public void timers_fire_in_deadline_order_independently() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		
		m_timer.setTimer(taskA, 500, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskB, 100, TimeUnit.MILLISECONDS);
		taskB.waitForFinished();
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isCancelled());
		
		Unchecked.runOrRTE(() -> Thread.sleep(500));
		Assertions.assertEquals(true, taskA.isCancelled());
	}
	
	@Test
	public void an_execution_finishing_before_its_timeout_completes_normally() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		StartableExecution<String> taskC = create("c", 300, true);
		
		m_timer.setTimer(taskA, 400, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskB, 100, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskC, 700, TimeUnit.MILLISECONDS);
		taskA.waitForFinished();
		
		Assertions.assertEquals(true, taskA.isCancelled());
		Assertions.assertEquals(true, taskB.isCancelled());
		Assertions.assertEquals(true, taskC.isCompleted());
	}
	
	@Test
	public void more_than_64_pending_timers_all_fire() throws Exception {
		// 이전 구현은 64개까지만 보관하고 넘치면 가장 늦은 기한의 예약을 알림 없이 버렸다.
		List<Execution<?>> execs = new ArrayList<>();
		for ( int i = 0; i < 100; ++i ) {
			Execution<?> exec = mock(Execution.class);
			execs.add(exec);
			m_timer.setTimer(exec, 100 + i, TimeUnit.MILLISECONDS);
		}
		for ( Execution<?> exec: execs ) {
			verify(exec, timeout(3000)).cancel(true);
		}
	}

	@Test
	public void a_finished_execution_releases_its_timer() throws Exception {
		// 이전 구현은 끝난 실행을 기한이 될 때까지 큐에 남겨 두어, 기한이 긴 예약이 자리를 차지했다.
		StartableExecution<String> done = create("a", 0, true);
		done.waitForFinished();
		Timer.TimeoutHandle releasedAtOnce = m_timer.setTimer(done, 1, TimeUnit.HOURS);
		Assertions.assertTrue(releasedAtOnce.isCancelled(), "an already finished execution must not keep a timer");

		StartableExecution<String> running = create("b", 100, true);
		Timer.TimeoutHandle releasedOnFinish = m_timer.setTimer(running, 1, TimeUnit.HOURS);
		Assertions.assertTrue(releasedOnFinish.isPending());
		running.waitForFinished();
		Assertions.assertTrue(releasedOnFinish.isCancelled(), "the timer must be released when the execution finishes");
		Assertions.assertTrue(running.isCompleted());
	}

	@Test
	public void a_slow_cancel_does_not_delay_other_timers() throws Exception {
		// 이전 구현은 타이머 lock을 쥔 채 cancel(true)를 불러, 취소 하나가 오래 걸리면 모든 타이머가 멈췄다.
		Execution<?> slow = mock(Execution.class);
		AtomicLong slowCancelReturnedAt = new AtomicLong(Long.MAX_VALUE);
		when(slow.cancel(true)).thenAnswer(inv -> {
			Thread.sleep(1500);
			slowCancelReturnedAt.set(System.nanoTime());
			return true;
		});
		Execution<?> other = mock(Execution.class);
		AtomicLong otherCancelledAt = new AtomicLong();
		when(other.cancel(true)).thenAnswer(inv -> {
			otherCancelledAt.set(System.nanoTime());
			return true;
		});

		m_timer.setTimer(slow, 50, TimeUnit.MILLISECONDS);
		m_timer.setTimer(other, 200, TimeUnit.MILLISECONDS);

		verify(other, timeout(1000)).cancel(true);
		Assertions.assertTrue(otherCancelledAt.get() < slowCancelReturnedAt.get(),
								"the other timer must fire while the slow cancel is still running");
	}

	@Test
	public void shutdown_releases_every_pending_handle() {
		// ScheduledThreadPoolExecutor.shutdownNow()는 대기 중인 예약의 future를 완료시키지 않는다. 핸들이 미완료로 남으면 안 된다.
		Timer.TimeoutHandle handle = m_timer.setTimer(mock(Execution.class), 1, TimeUnit.DAYS);
		Assertions.assertTrue(handle.isPending());

		m_timer.shutdown();

		Assertions.assertTrue(handle.isCancelled(), "" + handle);
		Assertions.assertFalse(handle.isPending());
	}

	@Test
	public void cancellation_threads_are_bounded() throws Exception {
		// 취소가 막혀도 취소 스레드는 최대 MAX_CANCELLATION_THREADS개이고, 나머지 요청은 앞의 것이 끝날 때까지 기다린다.
		int count = Timer.MAX_CANCELLATION_THREADS * 3;
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		AtomicInteger cancelled = new AtomicInteger();
		for ( int i = 0; i < count; ++i ) {
			Execution<?> exec = mock(Execution.class);
			when(exec.cancel(true)).thenAnswer(inv -> {
				maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
				release.await();
				running.decrementAndGet();
				cancelled.incrementAndGet();
				return true;
			});
			m_timer.setTimer(exec, 20, TimeUnit.MILLISECONDS);
		}

		long due = System.currentTimeMillis() + 3000;
		while ( running.get() < Timer.MAX_CANCELLATION_THREADS && System.currentTimeMillis() < due ) {
			Thread.sleep(10);
		}
		Thread.sleep(200);
		Assertions.assertEquals(Timer.MAX_CANCELLATION_THREADS, maxRunning.get());

		release.countDown();
		due = System.currentTimeMillis() + 5000;
		while ( cancelled.get() < count && System.currentTimeMillis() < due ) {
			Thread.sleep(10);
		}
		Assertions.assertEquals(count, cancelled.get(), "queued cancellations must run after the blocked ones");
		Assertions.assertEquals(Timer.MAX_CANCELLATION_THREADS, maxRunning.get());
	}

	@Test
	public void a_far_past_date_cancels_at_once() {
		// due - now가 넘치면(Long.MIN_VALUE) 먼 미래로 예약되던 문제.
		Execution<?> exec = mock(Execution.class);
		Timer.TimeoutHandle handle = m_timer.setTimer(exec, new Date(Long.MIN_VALUE));

		verify(exec, timeout(1000)).cancel(true);
		Assertions.assertTrue(handle.isExpired());
	}

	@Test
	public void cancelling_the_handle_unschedules_without_cancelling_the_execution() throws Exception {
		Execution<?> exec = mock(Execution.class);
		Timer.TimeoutHandle handle = m_timer.setTimer(exec, 100, TimeUnit.MILLISECONDS);

		Assertions.assertTrue(handle.cancel());
		Thread.sleep(300);

		verify(exec, org.mockito.Mockito.never()).cancel(true);
		Assertions.assertTrue(handle.isCancelled());
		Assertions.assertFalse(handle.isExpired());
	}

	@Test
	public void set_timer_after_shutdown_is_rejected() {
		m_timer.shutdown();

		Assertions.assertThrows(IllegalStateException.class,
								() -> m_timer.setTimer(mock(Execution.class), 1, TimeUnit.SECONDS));
	}
	
	@Test
	public void set_timeout_uses_the_shared_scheduler() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		StartableExecution<String> taskC = create("c", 1000000, true);
		
		taskA.setTimeout(700, TimeUnit.MILLISECONDS);
		taskB.setTimeout(400, TimeUnit.MILLISECONDS);
		taskC.setTimeout(100, TimeUnit.MILLISECONDS);
		
		taskC.waitForFinished();
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isRunning());
		Assertions.assertEquals(true, taskC.isCancelled());
		
		taskB.waitForFinished();
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isCancelled());
		
		taskA.waitForFinished();
		Assertions.assertEquals(true, taskA.isCancelled());
	}
}
