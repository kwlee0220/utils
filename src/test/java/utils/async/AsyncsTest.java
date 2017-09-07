package utils.async;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.Throwables;
import utils.func.Result;

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
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.thenAccept((v) -> { m_done = true; });
		Thread.sleep(100);
		Assert.assertEquals(1, task.getState());
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isSuccess());
		Assert.assertEquals(2, task.getState());
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test02() throws Exception {
		ActiveTask task = new ActiveTask(null);
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof CancellationException ) {
				m_done = true;
			}
		});
		Thread.sleep(100);
		Assert.assertEquals(1, task.getState());
		
		Assert.assertEquals(true, future.cancel(true));
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isEmpty());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test03() throws Exception {
		ActiveTask task = new ActiveTask(null);
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		Thread.sleep(100);
		Assert.assertEquals(1, task.getState());
		
		Assert.assertEquals(true, future.completeExceptionally(new IllegalStateException("aaa")));
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isFailure());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test11() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.thenAccept((v) -> { m_done = true; });
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isSuccess());
		Assert.assertEquals(2, task.getState());
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test12() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof CancellationException ) {
				m_done = true;
			}
		});
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Assert.assertEquals(true, future.cancel(true));
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isEmpty());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test13() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Assert.assertEquals(true, future.completeExceptionally(new IllegalStateException("aaa")));
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isFailure());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test14() throws Exception {
		PassiveTask task = new PassiveTask(new IllegalStateException("aaa"));
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Futures.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.waitForStarted();
		Assert.assertEquals(1, task.getState());
		
		Result<Void> result = Futures.getResult(future);
		Assert.assertEquals(true, result.isFailure());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	private static class ActiveTask extends ActiveCancellableRunnable {
		private Thread m_thread;
		private int m_state = 0;
		private RuntimeException m_error;
		
		ActiveTask(RuntimeException error) {
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
							throw Throwables.toRuntimeException(m_error);
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
					throw Throwables.toRuntimeException(e);
				}
				return;
			}
			complete();
		}
	}
}
