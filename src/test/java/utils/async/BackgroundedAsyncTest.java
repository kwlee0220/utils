package utils.async;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.Execution.State;
import utils.async.op.AsyncExecutions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class BackgroundedAsyncTest {
	private final ScheduledExecutorService m_scheduler = Executors.newScheduledThreadPool(4);
	private final Exception m_error = new Exception();
	
	@Mock Consumer<Result<Integer>> m_doneListener;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		AsyncExecution<String> fg = AsyncExecutions.idle("done", 300, TimeUnit.MILLISECONDS, m_scheduler);
		AsyncExecution<?> bg = AsyncExecutions.idle(10, TimeUnit.SECONDS, m_scheduler);
		AsyncExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, fg.isStarted());
		Assert.assertEquals(true, bg.isStarted());
		
		fg.waitForDone();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(true, fg.isCompleted());
		Assert.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		AsyncExecution<String> fg = AsyncExecutions.idle("done", 300, TimeUnit.MILLISECONDS, m_scheduler);
		AsyncExecution<?> bg = AsyncExecutions.idle(10, TimeUnit.SECONDS, m_scheduler);
		AsyncExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		boolean ok = exec.cancel(true);
		Assert.assertEquals(true, ok);
		
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isCancelled());
		Assert.assertEquals(true, fg.isCancelled());
		Assert.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		AsyncExecution<String> fg = AsyncExecutions.idle("done", 300, TimeUnit.MILLISECONDS, m_scheduler);
		fg = AsyncExecutions.sequential(fg, AsyncExecutions.failure(m_error));
		AsyncExecution<?> bg = AsyncExecutions.idle(10, TimeUnit.SECONDS, m_scheduler);
		AsyncExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForDone();
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(true, fg.isFailed());
		Assert.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		AsyncExecution<String> fg = AsyncExecutions.idle("done", 300, TimeUnit.MILLISECONDS, m_scheduler);
		AsyncExecution<?> bg = AsyncExecutions.failure(m_error);
		AsyncExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		bg.waitForDone();
		Thread.sleep(30);
		
		Assert.assertEquals(true, exec.isRunning());
		Assert.assertEquals(true, fg.isRunning());
		Assert.assertEquals(true, bg.isFailed());
	}
}
