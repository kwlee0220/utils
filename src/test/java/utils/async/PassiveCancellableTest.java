package utils.async;


import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PassiveCancellableTest {
	@Test
	public void test01() throws Exception {
		PassiveTask task = new PassiveTask(null);
		
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
		PassiveTask task = new PassiveTask(null);
		
		Assert.assertEquals(0, task.getState());
		CompletableFuture.runAsync(task);
		
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Thread.sleep(100);
		Assert.assertEquals(true, task.cancel());
		
		task.waitForFinished();
		Assert.assertEquals(-1, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		PassiveTask task = new PassiveTask(null);
		
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
		PassiveTask task = new PassiveTask(new RuntimeException());
		
		Assert.assertEquals(0, task.getState());
		CompletableFuture.runAsync(task);

		task.waitForFinished();
		Assert.assertEquals(1, task.getState());
		Assert.assertEquals(true, task.isDone());
		Assert.assertEquals(true, task.isFailed());
	}
	
//	@Test
//	public void test02() throws Exception {
//		CancellableTask task = new CancellableTask();
//		
//		Assert.assertEquals(0, task.getState());
//		
//		CompletableFuture<Void> future = Asyncs.runAsync(task);
//		future.whenComplete((v,e) -> {
//			if ( e != null && e instanceof CancellationException ) {
//				m_done = true;
//			}
//		});
//		Thread.sleep(100);
//		Assert.assertEquals(0, task.getState());
//		
//		Assert.assertEquals(true, future.cancel(true));
//		
//		Result<Void> result = Asyncs.getResult(future);
//		Assert.assertEquals(true, result.isEmpty());
//		Thread.sleep(100);
//		Assert.assertEquals(true, m_done);
//	}
//	
//	@Test
//	public void test03() throws Exception {
//		CancellableTask task = new CancellableTask();
//		
//		Assert.assertEquals(0, task.getState());
//		
//		CompletableFuture<Void> future = Asyncs.runAsync(task);
//		future.whenComplete((v,e) -> {
//			if ( e != null && e instanceof IllegalStateException ) {
//				m_done = true;
//			}
//		});
//		Thread.sleep(100);
//		Assert.assertEquals(0, task.getState());
//		
//		Assert.assertEquals(true, future.completeExceptionally(new IllegalStateException("aaa")));
//		
//		Result<Void> result = Asyncs.getResult(future);
//		Assert.assertEquals(true, result.isFailure());
//		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
//		Assert.assertEquals("aaa", result.getCause().getMessage());
//		Thread.sleep(100);
//		Assert.assertEquals(true, m_done);
//	}
	
	private static class PassiveTask extends PassiveCancellable implements Runnable {
		private int m_state = 0;
		private RuntimeException m_error;
		
		PassiveTask(RuntimeException error) {
			m_error = error;
			
			setStartAction(() -> m_state = 1);
			setCancelAction(() -> m_state = -1);
			setCompleteAction(() -> m_state = 2);
		}
		
		public int getState() {
			getLock().lock();
			try {
				return m_state;
			}
			finally { getLock().unlock(); }
		}

		@Override
		public void run() {
			begin();
			
			try {
				for ( int i =0; i < 50; ++i ) {
					synchronized ( this ) {
						this.wait(50);
					}
					
					if ( i == 20 && m_error != null ) {
						if ( markFailed(m_error) ) {
							Throwables.throwIfUnchecked(m_error);
							throw new RuntimeException(m_error);
						}
						return;
					}
					
					if ( checkCancelled() ) {
						return;
					}
				}
			}
			catch ( InterruptedException e ) {
				if ( markFailed(e) ) {
					Throwables.throwIfUnchecked(e);
					throw new RuntimeException(e);
				}
				return;
			}
			complete();
		}
	}
}
