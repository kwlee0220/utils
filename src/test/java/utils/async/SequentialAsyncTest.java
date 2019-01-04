package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import utils.async.op.SequentialAsyncExecution;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class SequentialAsyncTest {
	private FStream<AsyncExecution<?>> m_gen;
	private FStream<AsyncExecution<?>> m_gen2;
	private final Exception m_error = new Exception();
	
	@Mock SequentialAsyncExecution.SequenceListener m_listener;
	@Mock Consumer<Result<Integer>> m_doneListener;
	
	@Before
	public void setup() {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		m_gen = FStream.range(0, 5)
						.map(idx -> AsyncExecutions.idle(idx, 100, TimeUnit.MILLISECONDS, executors));
		m_gen2 = FStream.concat(m_gen, AsyncExecutions.failure(m_error));
	}
	
	@Test
	public void test01() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(0, exec.getCurrentExecutionIndex());
		
		AsyncExecution<?> elm = exec.getCurrentExecution();
		Assert.assertEquals(true, elm.isStarted());
		
		exec.waitForDone();
		Assert.assertEquals(4, (int)exec.get());
	}
	
	@Test
	public void test02() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		exec.setListener(m_listener);
		exec.whenDone(m_doneListener);
		
		exec.start();
		exec.waitForDone();
		verify(m_listener, times(6)).onForward(anyInt(), any(), any());
		verify(m_doneListener, times(1)).accept(Result.completed(Integer.valueOf(4)));
	}
	
	@Test
	public void test03() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		exec.setListener(m_listener);
		exec.whenDone(m_doneListener);
		
		exec.start();
		boolean ok = exec.waitForDone(200, TimeUnit.MILLISECONDS);
		exec.cancel(true);
		
		Assert.assertEquals(false, ok);
		verify(m_listener, times(3)).onForward(anyInt(), any(), any());
		verify(m_doneListener, times(1)).accept(Result.cancelled());
		Assert.assertEquals(2, exec.getCurrentExecutionIndex());
	}
	
	@Test
	public void test04() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen2);
		exec.setListener(m_listener);
		exec.whenDone(m_doneListener);
		
		exec.start();
		exec.waitForDone();
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(m_error, exec.pollResult().get().getCause());
		Thread.sleep(100);
		verify(m_listener, times(7)).onForward(anyInt(), any(), any());
		verify(m_doneListener, times(1)).accept(Result.failed(m_error));
		Assert.assertEquals(5, exec.getCurrentExecutionIndex());
	}
}
