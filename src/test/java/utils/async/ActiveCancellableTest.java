package utils.async;


import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ActiveCancellableTest {
	@Test
	public void test01() throws Exception {
		CancellableTask task = new CancellableTask(null);
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture.runAsync(task);
		
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		task.waitForFinished();
		Assert.assertEquals(2, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isCompleted());
	}
	
	@Test
	public void test02() throws Exception {
		CancellableTask task = new CancellableTask(null);
		
		Assert.assertEquals(0, task.getState());
		CompletableFuture.runAsync(task);
		
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Thread.sleep(50);
		Assert.assertEquals(true, task.cancel());
		
		task.waitForFinished();
		Assert.assertEquals(-1, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		CancellableTask task = new CancellableTask(null);
		
		Assert.assertEquals(0, task.getState());
		CompletableFuture.runAsync(task);

		task.waitForFinished();
		Assert.assertEquals(false, task.cancel());
		
		task.waitForFinished();
		Assert.assertEquals(2, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isCompleted());
	}
	
	@Test
	public void test04() throws Exception {
		CancellableTask task = new CancellableTask(new RuntimeException());
		
		Assert.assertEquals(0, task.getState());
		CompletableFuture.runAsync(task);

		task.waitForFinished();
		Assert.assertEquals(1, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isFailed());
	}
	
	private static class CancellableTask extends ActiveCancellableRunnable {
		private Thread m_thread;
		private int m_state = 0;
		private RuntimeException m_error;
		
		CancellableTask(RuntimeException error) {
			m_error = error;
			
			setStartAction(() -> {
				m_state = 1;
				m_thread = Thread.currentThread();
			});
			setCancelAction(() -> m_state = -1);
			setCompleteAction(() -> m_state = 2);
		}
		
		public int getState() {
			m_cancellableLock.lock();
			try {
				return m_state;
			}
			finally { m_cancellableLock.unlock(); }
		}

		@Override
		protected void runTask() throws Exception {
			if ( m_error != null ) {
				throw m_error;
			}
			Thread.sleep(500);
		}

		@Override
		public boolean cancelTask() {
			m_thread.interrupt();
			checkCancelled();
			
			return true;
		}
	}
}
