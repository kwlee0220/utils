package utils.async;


import java.util.concurrent.CancellationException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class QueuedExecutorTest {
	private boolean m_done = false;
	
	@Before
	public void setup() {
		m_done = false;
	}
	
	@Test
	public void test01() throws Exception {
		Executor<Integer> exector = Executors.newFixedThreadPool(1);

		Execution<Integer> handle = exector.submit(() -> next(0));
		Assert.assertEquals(false, handle.isStarted());
		sleep(100);
		Assert.assertEquals(true, handle.isStarted());
		Assert.assertEquals(1, (int)handle.get());
		
		exector.shutdown();
	}
	
	@Test(expected = CancellationException.class)
	public void test02() throws Exception {
		Executor<Integer> exector = Executors.newFixedThreadPool(1);

		Execution<Integer> handle = exector.submit(() -> next(0));
		Assert.assertEquals(true, handle.cancel());
		sleep(100);
		Assert.assertEquals(true, handle.isCancelled());
		Assert.assertEquals(1, (int)handle.get());
		
		exector.shutdown();
	}
	
	@Test(expected = CancellationException.class)
	public void test03() throws Exception {
		Executor<Integer> exector = Executors.newFixedThreadPool(1);

		Execution<Integer> handle = exector.submit(() -> next(0));
		sleep(200);
		Assert.assertEquals(true, handle.isStarted());
		Assert.assertEquals(true, handle.cancel());
		sleep(500);
		Assert.assertEquals(true, handle.isCancelled());
		Assert.assertEquals(1, (int)handle.get());
		
		exector.shutdown();
	}
	
	@Test
	public void test04() throws Exception {
		Executor<Integer> exector = Executors.newFixedThreadPool(1);
		
		Execution<Integer> handle = exector.submit(() -> next(0));
		Execution<Integer> handle1 = exector.submit(() -> next(1));
		Execution<Integer> handle2 = exector.submit(() -> next(2));
		
		handle.whenDone(() -> {
			sleep(200);
			Assert.assertEquals(true, handle1.isStarted());
			Assert.assertEquals(false, handle2.isStarted());
		});
		
		handle1.waitForDone();
		sleep(200);
		Assert.assertEquals(true, handle1.isStarted());

		Assert.assertEquals(3, (int)handle2.get());
		Assert.assertEquals(1, (int)handle.get());
		Assert.assertEquals(2, (int)handle1.get());
		
		exector.shutdown();
	}
	
	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch ( InterruptedException e ) { }
	}
	
	private int next(int v) throws InterruptedException {
		Thread.sleep(1000);
		
		return v + 1;
	}
}
