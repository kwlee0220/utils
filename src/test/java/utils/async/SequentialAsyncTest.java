package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static utils.async.op.AsyncExecutions.cancelled;
import static utils.async.op.AsyncExecutions.failure;
import static utils.async.op.AsyncExecutions.idle;

import java.util.List;
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
import utils.async.op.SequentialAsyncExecution;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class SequentialAsyncTest {
	private List<StartableExecution<Integer>> m_execList;
	private StartableExecution<Integer> m_failed;
	private StartableExecution<Integer> m_cancelled;
	
	private FStream<StartableExecution<?>> m_gen;
	private FStream<StartableExecution<?>> m_gen2;
	private FStream<StartableExecution<?>> m_gen3;
	private final Exception m_error = new Exception();
	
	@Mock Consumer<Result<Integer>> m_doneListener;
	
	@Before
	public void setup() {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		
		m_execList = FStream.range(0, 5)
							.map(idx -> idle(idx, 100, MILLISECONDS, executors))
							.toList();
		m_failed = failure(m_error);
		m_cancelled = cancelled();
		
		m_gen = FStream.from(m_execList);
		m_gen2 = FStream.concat(m_gen, m_failed);
		m_gen3 = FStream.concat(m_gen, m_cancelled);
	}
	
	@Test
	public void test01() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		
		Assert.assertEquals(State.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(0, exec.getCurrentExecutionIndex());
		
		StartableExecution<?> elm = exec.getCurrentExecution();
		Assert.assertEquals(true, elm.isStarted());
		
		exec.waitForDone();
		Assert.assertEquals(4, (int)exec.get());

		MILLISECONDS.sleep(50);
		for ( StartableExecution<Integer> elmExec: m_execList ) {
			Assert.assertTrue(elmExec.isCompleted());
		}
	}
	
	@Test
	public void test02() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.waitForDone();
		
		// finish_listener가 호출될 때까지 일정시간동안 대기한다.
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(Result.completed(Integer.valueOf(4)));
	}
	
	@Test
	public void test03() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		boolean ok = exec.waitForDone(250, MILLISECONDS);
		exec.cancel(true);
		MILLISECONDS.sleep(50);
		
		Assert.assertEquals(false, ok);
		verify(m_doneListener, times(1)).accept(Result.cancelled());
		Assert.assertEquals(2, exec.getCurrentExecutionIndex());
		Assert.assertTrue(m_execList.get(2).isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen2);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.waitForDone();
		MILLISECONDS.sleep(50);

		Assert.assertEquals(true, m_failed.isFailed());
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(m_error, exec.pollResult().get().getCause());
		
		verify(m_doneListener, times(1)).accept(Result.failed(m_error));
		Assert.assertEquals(5, exec.getCurrentExecutionIndex());
	}
	
	@Test
	public void test05() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen3);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.waitForDone();
		MILLISECONDS.sleep(50);

		Assert.assertEquals(true, m_cancelled.isCancelled());
		Assert.assertEquals(true, exec.isCancelled());
		
		verify(m_doneListener, times(1)).accept(Result.cancelled());
		Assert.assertEquals(5, exec.getCurrentExecutionIndex());
	}
}
