package utils.async;


import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

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
}
