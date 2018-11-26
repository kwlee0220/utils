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
		
		Assert.assertTrue(!task.isStarted());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());
		
		Result<Void> result = task.waitForResult();
		Assert.assertTrue(result.isCompleted());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test02() throws Exception {
		ActiveTask task = new ActiveTask(null);

		Assert.assertTrue(!task.isStarted());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());
		
		Assert.assertEquals(true, task.cancel());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCancelled());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test03() throws Exception {
		ActiveTask task = new ActiveTask(new IllegalStateException("aaa"));

		Assert.assertTrue(!task.isStarted());

		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test11() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertTrue(!task.isStarted());

		task.whenCompleted(r -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());
		
		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCompleted());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test12() throws Exception {
		PassiveTask task = new PassiveTask(null);
		Assert.assertTrue(!task.isStarted());

		task.whenCancelled(() -> m_done = true);
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());
		
		Assert.assertEquals(true, task.cancel());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isCancelled());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}
	
	@Test
	public void test13() throws Exception {
		PassiveTask task = new PassiveTask(new IllegalStateException("aaa"));
		Assert.assertTrue(!task.isStarted());
		
		task.whenFailed(e -> {
			if ( e instanceof IllegalStateException ) {
				m_done = true;
			}
		});
		task.start();
		task.waitForStarted();
		Assert.assertTrue(task.isStarted());

		Result<Void> result = task.waitForResult();
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals(IllegalStateException.class, result.getCause().getClass());
		Assert.assertEquals("aaa", result.getCause().getMessage());
		Thread.sleep(100);
		Assert.assertEquals(true, m_done);
	}

	private static class ActiveTask extends ExecutableExecution<Void> {
		private RuntimeException m_error;
		
		ActiveTask(RuntimeException error) {
			m_error = error;
		}

		@Override
		public Void executeWork() throws Exception {
			if ( m_error != null ) {
				throw m_error;
			}
			Thread.sleep(500);
			
			return null;
		}
	}
	
	private static class PassiveTask extends ExecutableExecution<Void> {
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
	}
}
