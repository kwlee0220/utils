package utils.async;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.RuntimeInterruptedException;
import utils.RuntimeTimeoutException;
import utils.Throwables;
import utils.func.Unchecked;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class GuardedRunnableTest {
	private final Guard m_guard = Guard.create();
	private volatile boolean m_executed = false;
	private boolean m_go = false;
	private volatile Throwable m_failure = null;
	private volatile Thread m_thread;

	@Test
	public void run() throws Exception {
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true);
		
		m_task.run();
		Assert.assertEquals(true, m_executed);
	}
	
	@Test
	public void awaitPrecondition() throws Exception {
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go);
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			Unchecked.runOrIgnore(() -> Thread.sleep(50));
			m_task.run();
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
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go)
					                    		.timeout(Duration.ofMillis(200));
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			m_task.run();
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
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go)
					                    		.timeout(Duration.ofMillis(200));
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_task.run();
				Assert.fail("should throw RuntimeTimeoutException");
			}
			catch ( RuntimeTimeoutException e ) {
				m_failure = e;
				throw e;
			}
		});
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		Assert.assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		Assert.assertTrue(m_failure != null && m_failure instanceof RuntimeTimeoutException);
		Assert.assertTrue(future.isCompletedExceptionally());
	}
	
	@Test
	public void awaitBeforeDue() throws Exception {
		Date due = new Date(System.currentTimeMillis() + 200);
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go)
					                    		.due(due);
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			m_task.run();
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
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go)
					                    		.due(due);
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_task.run();
				Assert.fail("should throw RuntimeTimeoutException");
			}
			catch ( RuntimeTimeoutException e ) {
				m_failure = e;
				throw e;
			}
		});
		Assert.assertEquals(false, m_executed);
		Unchecked.runOrIgnore(() -> Thread.sleep(50));
		Assert.assertEquals(false, m_executed);
		Assert.assertEquals(null, m_failure);
		Unchecked.runOrIgnore(() -> Thread.sleep(300));
		Assert.assertTrue(m_failure != null && m_failure instanceof RuntimeTimeoutException);
		Assert.assertTrue(future.isCompletedExceptionally());
	}
	
	@Test
	public void awaitThrowInterrupted() throws Exception {
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> m_executed = true)
					                    		.preCondition(() -> m_go);
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				m_thread = Thread.currentThread();
				m_task.run();
			}
			catch ( RuntimeInterruptedException e ) {
				m_failure = e.getCause();
				throw e;
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
		GuardedRunnable m_task = GuardedRunnable.from(m_guard, () -> { throw new Exception("test"); });
		
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			m_task.run();
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
			m_guard.signalAllInGuard();
		}
		finally {
			m_guard.unlock();
		}
	}
}
