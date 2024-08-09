package utils.async;


import java.time.Duration;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.op.AsyncExecutions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class BackgroundedAsyncTest {
	private final Exception m_error = new Exception();
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(50));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, fg.isStarted());
		Assert.assertEquals(true, bg.isStarted());
		
		fg.waitForFinished();
		Thread.sleep(100);
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(true, fg.isCompleted());
		Assert.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.idle(null, Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
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
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		fg = AsyncExecutions.sequential(fg, AsyncExecutions.throwAsync(m_error));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForFinished();
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(true, fg.isFailed());
		Assert.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.throwAsync(m_error);
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		bg.waitForFinished();
		Thread.sleep(30);
		
		Assert.assertEquals(true, exec.isRunning());
		Assert.assertEquals(true, fg.isRunning());
		Assert.assertEquals(true, bg.isFailed());
	}
}
