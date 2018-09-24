package utils.async;


import java.util.concurrent.CancellationException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncsTest {
	private boolean m_done = false;
	
	@Before
	public void setup() {
		m_done = false;
	}
	
	@Test
	public void test01() throws Exception {
		ActiveTask task = new ActiveTask(null);
		
		Assert.assertEquals(0, task.getTaskState());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());
		
		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCompleted());
		Assert.assertEquals(2, task.getTaskState());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test02() throws Exception {
		ActiveTask task = new ActiveTask(null);
		
		Assert.assertEquals(0, task.getTaskState());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());
		
		Assert.assertEquals(true, task.cancel());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCancelled());
		Assert.assertEquals(-1, task.getTaskState());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test03() throws Exception {
		ActiveTask task = new ActiveTask(new IllegalStateException("aaa"));
		
		Assert.assertEquals(0, task.getTaskState());

		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals(1, task.getTaskState());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test11() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertEquals(0, task.getTaskState());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());
		
		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCompleted());
		Assert.assertEquals(2, task.getTaskState());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test12() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertEquals(0, task.getTaskState());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());
		
		Assert.assertEquals(true, task.cancel());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCancelled());
		Assert.assertEquals(-1, task.getTaskState());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test13() throws Exception {
		PassiveTask task = new PassiveTask(new IllegalStateException("aaa"));
		Assert.assertEquals(0, task.getTaskState());
		
		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assert.assertEquals(1, task.getTaskState());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals(1, task.getTaskState());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}

	private static class ActiveTask extends ThreadedAsyncExecution<Void> {
		private Thread m_thread;
		private int m_state = 0;
		private RuntimeException m_error;
		
		ActiveTask(RuntimeException error) {
			m_error = error;
			
			setStartHook(() -> {
				m_state = 1;
				m_thread = Thread.currentThread();
			});
			setCancelHook(() -> m_state = -1);
			setCompleteHook(() -> m_state = 2);
		}
		
		public int getTaskState() {
			return m_guard.get(() -> m_state);
		}

		@Override
		protected Void runTask() throws Exception {
			if ( m_error != null ) {
				throw m_error;
			}
			Thread.sleep(500);
			
			return null;
		}

		@Override
		public void cancelTask() {
			m_thread.interrupt();
		}
	}
	
	private static class PassiveTask extends ThreadedAsyncExecution<Void> {
		private int m_state = 0;
		private RuntimeException m_error;
		
		PassiveTask(RuntimeException error) {
			m_error = error;
			
			setStartHook(() -> m_state = 1);
			setCancelHook(() -> m_state = -1);
			setCompleteHook(() -> m_state = 2);
		}
		
		public int getTaskState() {
			return m_guard.get(() -> m_state);
		}
		
		@Override
		protected Void runTask() throws Throwable {
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
	}
}
