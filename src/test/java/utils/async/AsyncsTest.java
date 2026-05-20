package utils.async;

import java.util.concurrent.CancellationException;

import javax.annotation.concurrent.GuardedBy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jetbrains.annotations.Nullable;

import utils.func.Try;
import utils.thread.Guard;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncsTest {
	private boolean m_done = false;
	
	@BeforeEach
	public void setup() {
		m_done = false;
	}
	
	@Test
	public void test01() throws Exception {
		ActiveTask task = new ActiveTask(null);
		
		Assertions.assertTrue(!task.isStarted());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());
		
		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertTrue(result.isCompleted());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}
	
	@Test
	public void test02() throws Exception {
		ActiveTask task = new ActiveTask(null);

		Assertions.assertTrue(!task.isStarted());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());
		
		Assertions.assertEquals(true, task.cancel(true));

		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertEquals(true, result.isCancelled());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}
	
	@Test
	public void test03() throws Exception {
		ActiveTask task = new ActiveTask(new IllegalStateException("aaa"));

		Assertions.assertTrue(!task.isStarted());

		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());

		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertEquals(true, result.isFailed());
		Assertions.assertEquals(IllegalStateException.class, result.getFailureCause().getClass());
		Assertions.assertEquals("aaa", result.getFailureCause().getMessage());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}
	
	@Test
	public void test11() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assertions.assertTrue(!task.isStarted());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());
		
		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertEquals(true, result.isCompleted());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}
	
	@Test
	public void test12() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assertions.assertTrue(!task.isStarted());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());
		
		Assertions.assertEquals(true, task.cancel(true));

		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertEquals(true, result.isCancelled());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}
	
	@Test
	public void test13() throws Exception {
		PassiveTask task = new PassiveTask(new IllegalStateException("aaa"));
		Assertions.assertTrue(!task.isStarted());
		
		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assertions.assertTrue(task.isStarted());

		AsyncResult<Void> result = task.waitForFinished();
		Assertions.assertEquals(true, result.isFailed());
		Assertions.assertEquals(IllegalStateException.class, result.getFailureCause().getClass());
		Assertions.assertEquals("aaa", result.getFailureCause().getMessage());
		Thread.sleep(100);
		Assertions.assertEquals(true, m_done);
	}

	private static class ActiveTask extends AbstractThreadedExecution<Void> implements CancellableWork {
		private Guard m_guard = Guard.create();
		@Nullable @GuardedBy("m_guard") private Thread m_thread = null;
		private RuntimeException m_error;
		
		ActiveTask(RuntimeException error) {
			m_error = error;
		}

		@Override
		public Void executeWork() throws Exception {
			m_guard.run(() -> m_thread = Thread.currentThread());
			
			if ( m_error != null ) {
				throw m_error;
			}
			Thread.sleep(500);
			
			return null;
		}

		@Override
		public boolean cancelWork() {
			return m_guard.get(() -> {
				if ( m_thread != null ) {
					m_thread.interrupt();
				}
				return Try.run(() -> waitForFinished()).isSuccessful();
			});
		}
	}
	
	private static class PassiveTask extends AbstractThreadedExecution<Void> implements CancellableWork {
		private RuntimeException m_error;
		
		PassiveTask(RuntimeException error) {
			m_error = error;
		}
		
		@Override
		public Void executeWork() throws Exception {
			for ( int i =0; i < 50; ++i ) {
				synchronized ( this ) {
					this.wait(50);
				}
				
				if ( i == 20 && m_error != null ) {
					throw m_error;
				}
				
				if ( isCancelRequested() ) {
					throw new CancellationException();
				}
			}
			
			return null;
		}

		@Override
		public boolean cancelWork() {
			return Try.run(() -> waitForFinished()).isSuccessful();
		}
	}
}
