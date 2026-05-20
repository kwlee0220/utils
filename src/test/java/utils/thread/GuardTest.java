package utils.thread;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.Throwables;
import utils.thread.Guard.AwaitCondition;
import utils.thread.Guard.TimedAwaitCondition;
import utils.func.Unchecked;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardTest {
	private static final long OVERHEAD = 30;
	private static final Duration TIMEOUT = Duration.ofMillis(300);

	private Guard m_guard;
	private boolean m_interrupted = false;

	@BeforeEach
	public void setup() {
		m_guard = Guard.create();
	}

	@Test
	public void testAwaitFor() throws Exception {
		long started = System.currentTimeMillis();

		m_guard.lock();
		try {
			m_guard.awaitSignal(Duration.ofMillis(300));
			long elapsed = System.currentTimeMillis() - started;
			long gap = Math.abs(elapsed - 300L);
			assertTrue(gap < OVERHEAD, () -> String.format("%d < %d", gap, OVERHEAD));
		}
		finally {
			m_guard.unlock();
		}
	}

	@Test
	public void testAwaitUntil() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean done = m_guard.awaitCondition(() -> m_interrupted, TIMEOUT).andReturn();
				assertFalse(done);
			}
			catch ( InterruptedException e ) {
				fail("should not throw InterruptedException");
			}
		});

		future.join();
	}

	@Test
	public void testAwaitUntilWithInterrupt() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean done = m_guard.awaitCondition(() -> m_interrupted, TIMEOUT).andReturn();
				assertFalse(done);
			}
			catch ( Throwable e ) {
				fail("should not throw Throwable");
			}
		});
		Thread.sleep(100);
		m_guard.lock(); try { m_guard.signalAll(); } finally { m_guard.unlock(); }
		Thread.sleep(50);
		m_guard.lock(); try { m_guard.signalAll(); } finally { m_guard.unlock(); }
		future.join();
	}

	@Test
	public void testAwaitUntilWithCancel() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean done = m_guard.awaitCondition(() -> m_interrupted, TIMEOUT).andReturn();
				assertTrue(done);
			}
			catch ( Throwable e ) {
				fail("should not throw Throwable");
			}
		});
		Thread.sleep(100);
		m_guard.run(() -> m_interrupted = true);
		future.join();
	}

	private volatile boolean m_executed = false;
	private boolean m_go = false;
	private volatile Throwable m_failure = null;
	private volatile Thread m_thread;

	@Test
	public void run() throws Exception {
		m_guard.run(() -> m_executed = true);
		assertEquals(true, m_executed);
	}

	@Test
	public void awaitPrecondition() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			Unchecked.runOrIgnore(() -> Thread.sleep(50));
			try {
				m_guard.awaitCondition(() -> m_go)
						.andRun(() -> m_executed = true);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);

		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(100));

		assertEquals(true, m_executed);
		assertTrue(future.isDone());
	}

	@Test
	public void awaitBeforeTimeout() throws Exception {
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, Duration.ofMillis(200));
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);

		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(50));

		assertEquals(true, m_executed);
		assertTrue(future.isDone());
	}

	@Test
	public void awaitTimeout() throws Exception {
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, Duration.ofMillis(200));

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
				fail("should throw RuntimeTimeoutException");
			}
			catch ( TimeoutException e ) {
				m_failure = e;
				throw new CompletionException(e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);
		assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		assertTrue(m_failure != null && m_failure instanceof TimeoutException);
		assertTrue(future.isCompletedExceptionally());
	}

	@Test
	public void awaitBeforeDue() throws Exception {
		Date due = new Date(System.currentTimeMillis() + 200);
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, due);

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);

		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(50));

		assertEquals(true, m_executed);
		assertTrue(future.isDone());
	}

	@Test
	public void awaitDue() throws Exception {
		Date due = new Date(System.currentTimeMillis() + 200);
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, due);

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
				fail("should throw RuntimeTimeoutException");
			}
			catch ( TimeoutException e ) {
				m_failure = e;
				throw new CompletionException(e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);
		assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		assertTrue(m_failure != null && m_failure instanceof TimeoutException);
		assertTrue(future.isCompletedExceptionally());
	}

	@Test
	public void awaitThrowInterrupted() throws Exception {
		AwaitCondition act = m_guard.awaitCondition(() -> m_go);

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_thread = Thread.currentThread();
				act.andRun(() -> m_executed = true);
			}
			catch ( InterruptedException e ) {
				m_failure = e.getCause();
				throw new CompletionException(e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		assertEquals(false, m_executed);
		assertTrue(!future.isDone());

		m_thread.interrupt();
		CompletionException ce = assertThrows(CompletionException.class, future::join,
												"should throw CompletionException wrapping InterruptedException");
		Throwable cause = Throwables.unwrapThrowable(ce);
		assertTrue(cause instanceof InterruptedException);
	}

	@Test
	public void awaitThrowExecutionException() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			m_guard.run(() ->  {
				throw new CompletionException(new Exception("test"));
			});
		});
		CompletionException ce = assertThrows(CompletionException.class, future::join,
												"should throw CompletionException wrapping ExecutionException");
		Throwable cause = Throwables.unwrapThrowable(ce);
		assertEquals("test", cause.getMessage());
	}

	private void letGo() {
		m_guard.lock();
		try {
			m_go = true;
			m_guard.signalAll();
		}
		finally {
			m_guard.unlock();
		}
	}

	@Test
	public void andRunChecked_propagatesInterruptedExceptionFromTask_unwrapped() throws Exception {
		AtomicBoolean interruptFlagPreserved = new AtomicBoolean(false);
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_guard.awaitCondition(() -> true).andRunChecked(() -> {
					throw new InterruptedException("from task");
				});
				fail("expected InterruptedException to propagate");
			}
			catch ( InterruptedException expected ) {
				// task가 던진 InterruptedException은 ExecutionException으로 wrapping되지 않고 그대로 전파.
				interruptFlagPreserved.set(Thread.currentThread().isInterrupted());
			}
			catch ( ExecutionException e ) {
				throw new CompletionException("InterruptedException was wrapped into ExecutionException", e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		future.join();
		assertTrue(interruptFlagPreserved.get(),
					"InterruptedException 처리 시 인터럽트 플래그가 보존되어야 함");
	}

	@Test
	public void andRunChecked_wrapsRuntimeExceptionFromTask() throws Exception {
		CompletableFuture<Throwable> future = CompletableFuture.supplyAsync(() -> {
			try {
				m_guard.awaitCondition(() -> true).andRunChecked(() -> {
					throw new IllegalStateException("from task");
				});
				return null;
			}
			catch ( ExecutionException ee ) {
				return ee.getCause();
			}
			catch ( Throwable e ) {
				return e;
			}
		});
		Throwable cause = future.join();
		assertTrue(cause instanceof IllegalStateException,
					() -> "기대: IllegalStateException, 실제: " + cause);
		assertEquals("from task", cause.getMessage());
	}

	@Test
	public void andGetChecked_propagatesInterruptedExceptionFromSupplier_unwrapped() throws Exception {
		AtomicBoolean ok = new AtomicBoolean(false);
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_guard.awaitCondition(() -> true).andGetChecked(() -> {
					throw new InterruptedException("from supplier");
				});
				fail("expected InterruptedException to propagate");
			}
			catch ( InterruptedException expected ) {
				ok.set(true);
			}
			catch ( ExecutionException e ) {
				throw new CompletionException("InterruptedException was wrapped into ExecutionException", e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		future.join();
		assertTrue(ok.get());
	}

	@Test
	public void andGetChecked_wrapsRuntimeExceptionFromSupplier() throws Exception {
		CompletableFuture<Throwable> future = CompletableFuture.supplyAsync(() -> {
			try {
				m_guard.awaitCondition(() -> true).andGetChecked(() -> {
					throw new IllegalStateException("from supplier");
				});
				return null;
			}
			catch ( ExecutionException ee ) {
				return ee.getCause();
			}
			catch ( Throwable e ) {
				return e;
			}
		});
		Throwable cause = future.join();
		assertTrue(cause instanceof IllegalStateException);
		assertEquals("from supplier", cause.getMessage());
	}

	@Test
	public void preAction_runsExactlyOnceBeforeAwait() throws Exception {
		AtomicInteger preActionCount = new AtomicInteger(0);

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			Unchecked.runOrIgnore(() -> Thread.sleep(50));
			try {
				m_guard.preAction(preActionCount::incrementAndGet)
						.awaitCondition(() -> m_go)
						.andRun(() -> m_executed = true);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});

		// preAction 후 condition false → 한 번만 실행되었는지 확인하려고 잠시 대기
		Unchecked.runOrIgnore(() -> Thread.sleep(150));

		// condition을 만족시켜 종료
		letGo();
		future.join();

		assertEquals(1, preActionCount.get(), "preAction은 정확히 한 번만 실행되어야 함");
		assertTrue(m_executed);
	}

	@Test
	public void preAction_exceptionWakesOtherWaiters() throws Exception {
		// 다른 쓰레드가 같은 Guard에서 대기 중일 때, preAction이 실패하면 signalAll로 깨워주는지 검증.
		AtomicBoolean waiterWoken = new AtomicBoolean(false);
		AtomicBoolean waiterFlag = new AtomicBoolean(false);
		CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> {
			m_guard.lock();
			try {
				while ( !waiterFlag.get() ) {
					m_guard.awaitSignal();
				}
				waiterWoken.set(true);
			}
			catch ( InterruptedException e ) {
				/* not expected */
			}
			finally {
				m_guard.unlock();
			}
		});

		// waiter가 awaitSignal에 들어갈 때까지 잠시 대기
		Thread.sleep(100);
		assertFalse(waiter.isDone(), "waiter should still be waiting");

		// preAction이 실패하는 작업을 시작 — 실패 전에 signalAll을 보내야 함
		CompletableFuture<Throwable> failer = CompletableFuture.supplyAsync(() -> {
			try {
				m_guard.preAction(() -> {
					// preAction 안에서 waiter가 깨도록 플래그를 set한 뒤 예외 발생
					waiterFlag.set(true);
					throw new IllegalStateException("preAction failed");
				}).awaitCondition(() -> true).andRun(() -> {});
				return null;
			}
			catch ( Throwable e ) {
				return e;
			}
		});

		Throwable thrown = failer.join();
		waiter.join();

		assertTrue(thrown instanceof IllegalStateException, "preAction 예외는 그대로 전파");
		assertTrue(waiterWoken.get(), "preAction 실패 시 signalAll로 다른 대기 쓰레드가 깨어나야 함");
	}

	@Test
	public void preAction_exceptionPropagatesUnwrapped() throws Exception {
		CompletableFuture<Throwable> future = CompletableFuture.supplyAsync(() -> {
			try {
				m_guard.preAction(() -> { throw new IllegalStateException("preAction failed"); })
						.awaitCondition(() -> true)
						.andRunChecked(() -> { /* never reached */ });
				return null;
			}
			catch ( ExecutionException ee ) {
				// preAction 예외는 ExecutionException으로 wrapping되지 않아야 함
				return ee;
			}
			catch ( Throwable e ) {
				return e;
			}
		});
		Throwable thrown = future.join();
		assertTrue(thrown instanceof IllegalStateException,
					() -> "preAction 예외는 wrapping 없이 전파되어야 함. 실제: " + thrown);
		assertEquals("preAction failed", thrown.getMessage());
	}

	@Test
	public void awaitSignal_rejectsNullDuration() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> {
			m_guard.lock();
			try {
				m_guard.awaitSignal((Duration)null);
			}
			finally {
				m_guard.unlock();
			}
		});
	}

	@Test
	public void awaitSignal_rejectsZeroDuration() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> {
			m_guard.lock();
			try {
				m_guard.awaitSignal(Duration.ZERO);
			}
			finally {
				m_guard.unlock();
			}
		});
	}

	@Test
	public void awaitSignal_rejectsNullDate() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> {
			m_guard.lock();
			try {
				m_guard.awaitSignal((Date)null);
			}
			finally {
				m_guard.unlock();
			}
		});
	}

	@Test
	public void timedAwaitCondition_zeroLongTimeoutFiresImmediatelyWhenConditionFalse() throws Exception {
		// condition이 false인 상태에서 timeout=0이면 즉시 TimeoutException 발생.
		assertThrows(TimeoutException.class,
						() -> m_guard.awaitCondition(() -> false, 0L, java.util.concurrent.TimeUnit.MILLISECONDS)
										.andRun(() -> {}));
	}

	@Test
	public void timedAwaitCondition_zeroDurationFiresImmediatelyWhenConditionFalse() throws Exception {
		// condition이 false인 상태에서 timeout=0이면 즉시 TimeoutException 발생.
		assertThrows(TimeoutException.class,
						() -> m_guard.awaitCondition(() -> false, Duration.ZERO).andRun(() -> {}));
	}

	@Test
	public void timedAwaitCondition_zeroTimeoutPassesWhenConditionTrue() throws Exception {
		// condition이 이미 true이면 timeout=0이어도 정상 통과.
		m_guard.awaitCondition(() -> true, Duration.ZERO).andRun(() -> {});
		m_guard.awaitCondition(() -> true, 0L, java.util.concurrent.TimeUnit.MILLISECONDS)
				.andRun(() -> {});
	}

	@Test
	public void timedAwaitCondition_rejectsNegativeDuration() throws Exception {
		assertThrows(IllegalArgumentException.class,
						() -> m_guard.awaitCondition(() -> true, Duration.ofMillis(-100)));
	}
}
