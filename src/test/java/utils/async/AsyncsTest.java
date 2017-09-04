package utils.async;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.Lambdas;
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
		CancellableTask task = new CancellableTask();
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Asyncs.runAsync(task);
		future.thenAccept((v) -> { m_done = true; });
		Thread.sleep(100);
		Assert.assertEquals(0, task.getState());
		
		Result<Void> result = Asyncs.getResult(future);
		Assert.assertEquals(true, result.isSuccess());
		Assert.assertEquals(1, task.getState());
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test02() throws Exception {
		CancellableTask task = new CancellableTask();
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Asyncs.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof CancellationException ) {
				m_done = true;
			}
		});
		Thread.sleep(100);
		Assert.assertEquals(0, task.getState());
		
		Assert.assertEquals(true, future.cancel(true));
		
		Result<Void> result = Asyncs.getResult(future);
		Assert.assertEquals(true, result.isEmpty());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test03() throws Exception {
		CancellableTask task = new CancellableTask();
		
		Assert.assertEquals(0, task.getState());
		
		CompletableFuture<Void> future = Asyncs.runAsync(task);
		future.whenComplete((v,e) -> {
			if ( e != null && e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		Thread.sleep(100);
		Assert.assertEquals(0, task.getState());
		
		Assert.assertEquals(true, future.completeExceptionally(new IllegalStateException("aaa")));
		
		Result<Void> result = Asyncs.getResult(future);
		Assert.assertEquals(true, result.isFailure());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	private static class CancellableTask implements Runnable, Cancellable {
		private final ReentrantLock m_lock = new ReentrantLock();
		private final Condition m_cond = m_lock.newCondition();
		private Thread m_thread;
		private int m_state = 0;
		
		public int getState() {
			m_lock.lock();
			try {
				return m_state;
			}
			finally { m_lock.unlock(); }
		}

		@Override
		public void run() {
			Lambdas.guraded(m_lock, () -> {
				m_thread = Thread.currentThread();
				m_cond.signalAll();
			});
			
			try {
				Thread.sleep(1000);
				Lambdas.guraded(m_lock, () -> {
					m_state = 1;
					m_cond.signalAll();
				});
			}
			catch ( InterruptedException e ) {
				Lambdas.guraded(m_lock, () -> {
					m_state = -1;
					m_cond.signalAll();
				});
			}
		}

		@Override
		public boolean cancel() {
			Lambdas.guraded(m_lock, () -> {
				while ( m_thread == null ) {
					try {
						m_cond.await();
					}
					catch ( InterruptedException e ) { }
				}
				
				m_thread.interrupt();
			});
			
			return true;
		}
		
	}
}
