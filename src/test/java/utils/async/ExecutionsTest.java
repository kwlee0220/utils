package utils.async;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.Execution.State;
import utils.async.op.AsyncExecutions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutionsTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void testNop() throws Exception {
		StartableExecution<String> exec = AsyncExecutions.nop("abc");
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals("abc", exec.get());
	}
	
	@Test
	public void testIdle01() throws Exception {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		StartableExecution<Void> exec = AsyncExecutions.idle(300, TimeUnit.MILLISECONDS, executors);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		long started = System.currentTimeMillis();
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		exec.waitForDone();
		
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(null, exec.get());
		Assert.assertTrue(System.currentTimeMillis() - started >= 300);
	}
	
	@Test
	public void testIdle02() throws Exception {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		StartableExecution<Void> exec = AsyncExecutions.idle(300, TimeUnit.MILLISECONDS, executors);
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		
		boolean done = exec.cancel(true);
		Assert.assertEquals(true, done);
		Assert.assertEquals(true, exec.isCancelled());
	}
}
