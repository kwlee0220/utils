package utils.async;


import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(300));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, target.isStarted());
		
		exec.waitForFinished();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(true, target.isCompleted());
		Assert.assertEquals(false, exec.isTimedout());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		exec.waitForFinished();
		
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
		Assert.assertEquals(true, exec.isTimedout());
	}
	
	@Test
	public void test03() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		StartableExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		exec.cancel(true);
		
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		StartableExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		target.cancel(true);
		Thread.sleep(100);
		
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, target.isCancelled());
	}
	
	@Test
	public void test05() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(300));
		target = AsyncExecutions.sequential(target, AsyncExecutions.throwAsync(m_error));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);
		
		exec.start();
		exec.waitForFinished();
		Thread.sleep(30);
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(true, target.isFailed());
	}
}
