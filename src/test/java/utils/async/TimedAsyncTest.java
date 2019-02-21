package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.Execution.State;
import utils.async.op.AsyncExecutions;
import utils.async.op.TimedAsyncExecution;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class TimedAsyncTest {
	private final ScheduledExecutorService m_scheduler = Executors.newScheduledThreadPool(4);
	private final Exception m_error = new Exception();
	
	@Mock Consumer<Result<Integer>> m_doneListener;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", 300, MILLISECONDS, m_scheduler);
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, 500, MILLISECONDS, m_scheduler);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, target.isStarted());
		
		exec.waitForDone();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(true, target.isCompleted());
		Assert.assertEquals(false, exec.isTimedout());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", 500, MILLISECONDS, m_scheduler);
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, 300, MILLISECONDS, m_scheduler);
		
		exec.start();
		exec.waitForDone();
		
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
		Assert.assertEquals(true, exec.isTimedout());
	}
	
	@Test
	public void test03() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", 500, MILLISECONDS, m_scheduler);
		StartableExecution<String> exec = AsyncExecutions.timed(target, 300, MILLISECONDS, m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		exec.cancel(true);
		
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", 500, MILLISECONDS, m_scheduler);
		StartableExecution<String> exec = AsyncExecutions.timed(target, 300, MILLISECONDS, m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		target.cancel(true);
		Thread.sleep(100);
		
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
	}
	
	@Test
	public void test05() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", 300, MILLISECONDS, m_scheduler);
		target = AsyncExecutions.sequential(target, AsyncExecutions.failure(m_error));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, 500, MILLISECONDS, m_scheduler);
		
		exec.start();
		exec.waitForDone();
		Thread.sleep(30);
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(true, target.isFailed());
	}
}
