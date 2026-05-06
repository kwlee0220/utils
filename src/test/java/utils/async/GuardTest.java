package utils.async;


import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.Throwables;
import utils.async.Guard.AwaitCondition;
import utils.async.Guard.TimedAwaitCondition;
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
	
	@Before
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
			Assert.assertTrue(String.format("%d < %d", gap, OVERHEAD), gap < OVERHEAD);
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
				Assert.assertFalse(done);
			}
			catch ( InterruptedException e ) {
				Assert.fail("should not throw InterruptedException");
			}
		});

		future.join();
	}
	
	@Test
	public void testAwaitUntilWithInterrupt() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean done = m_guard.awaitCondition(() -> m_interrupted, TIMEOUT).andReturn();
				Assert.assertFalse(done);
			}
			catch ( Throwable e ) {
				Assert.fail("should not throw Throwable");
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
				Assert.assertTrue(done);
			}
			catch ( Throwable e ) {
				Assert.fail("should not throw Throwable");
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
		Assert.assertEquals(true, m_executed);
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
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		
		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		
		Assert.assertEquals(true, m_executed);
		Assert.assertTrue(future.isDone());
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
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		
		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		
		Assert.assertEquals(true, m_executed);
		Assert.assertTrue(future.isDone());
	}
	
	@Test
	public void awaitTimeout() throws Exception {
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, Duration.ofMillis(200));
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
				Assert.fail("should throw RuntimeTimeoutException");
			}
			catch ( TimeoutException e ) {
				m_failure = e;
				throw new CompletionException(e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		Assert.assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		Assert.assertTrue(m_failure != null && m_failure instanceof TimeoutException);
		Assert.assertTrue(future.isCompletedExceptionally());
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
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		
		letGo();
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		
		Assert.assertEquals(true, m_executed);
		Assert.assertTrue(future.isDone());
	}
	
	@Test
	public void awaitDue() throws Exception {
		Date due = new Date(System.currentTimeMillis() + 200);
		TimedAwaitCondition act = m_guard.awaitCondition(() -> m_go, due);
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				act.andRun(() -> m_executed = true);
				Assert.fail("should throw RuntimeTimeoutException");
			}
			catch ( TimeoutException e ) {
				m_failure = e;
				throw new CompletionException(e);
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		});
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		Assert.assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		Assert.assertTrue(m_failure != null && m_failure instanceof TimeoutException);
		Assert.assertTrue(future.isCompletedExceptionally());
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
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		Assert.assertTrue(!future.isDone());
		
		m_thread.interrupt();
		try {
			future.join();
			Assert.fail("should throw RuntimeInterruptedException");
		}
		catch ( CompletionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			Assert.assertTrue(cause instanceof InterruptedException);
		}
	}
	
	@Test
	public void awaitThrowExecutionException() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			m_guard.run(() ->  {
				throw new CompletionException(new Exception("test")); 
			});
		});
		try {
			future.join();
			Assert.fail("should throw RuntimeExecutionException");
		}
		catch ( CompletionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			Assert.assertEquals("test", cause.getMessage());
		}
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
				Assert.fail("expected InterruptedException to propagate");
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
		Assert.assertTrue("InterruptedException 처리 시 인터럽트 플래그가 보존되어야 함",
							interruptFlagPreserved.get());
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
		Assert.assertTrue("기대: IllegalStateException, 실제: " + cause,
							cause instanceof IllegalStateException);
		Assert.assertEquals("from task", cause.getMessage());
	}

	@Test
	public void andGetChecked_propagatesInterruptedExceptionFromSupplier_unwrapped() throws Exception {
		AtomicBoolean ok = new AtomicBoolean(false);
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_guard.awaitCondition(() -> true).andGetChecked(() -> {
					throw new InterruptedException("from supplier");
				});
				Assert.fail("expected InterruptedException to propagate");
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
		Assert.assertTrue(ok.get());
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
		Assert.assertTrue(cause instanceof IllegalStateException);
		Assert.assertEquals("from supplier", cause.getMessage());
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

		Assert.assertEquals("preAction은 정확히 한 번만 실행되어야 함", 1, preActionCount.get());
		Assert.assertTrue(m_executed);
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
		Assert.assertFalse("waiter should still be waiting", waiter.isDone());

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

		Assert.assertTrue("preAction 예외는 그대로 전파", thrown instanceof IllegalStateException);
		Assert.assertTrue("preAction 실패 시 signalAll로 다른 대기 쓰레드가 깨어나야 함", waiterWoken.get());
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
		Assert.assertTrue("preAction 예외는 wrapping 없이 전파되어야 함. 실제: " + thrown,
							thrown instanceof IllegalStateException);
		Assert.assertEquals("preAction failed", thrown.getMessage());
	}

	@Test(expected = IllegalArgumentException.class)
	public void awaitSignal_rejectsNullDuration() throws Exception {
		m_guard.lock();
		try {
			m_guard.awaitSignal((Duration)null);
		}
		finally {
			m_guard.unlock();
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void awaitSignal_rejectsZeroDuration() throws Exception {
		m_guard.lock();
		try {
			m_guard.awaitSignal(Duration.ZERO);
		}
		finally {
			m_guard.unlock();
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void awaitSignal_rejectsNullDate() throws Exception {
		m_guard.lock();
		try {
			m_guard.awaitSignal((Date)null);
		}
		finally {
			m_guard.unlock();
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void timedAwaitCondition_rejectsZeroLongTimeout() throws Exception {
		m_guard.awaitCondition(() -> true, 0L, java.util.concurrent.TimeUnit.MILLISECONDS);
	}

	@Test(expected = IllegalArgumentException.class)
	public void timedAwaitCondition_rejectsZeroDuration() throws Exception {
		m_guard.awaitCondition(() -> true, Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void timedAwaitCondition_rejectsNegativeDuration() throws Exception {
		m_guard.awaitCondition(() -> true, Duration.ofMillis(-100));
	}
}
