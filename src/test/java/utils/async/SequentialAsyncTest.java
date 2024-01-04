package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static utils.async.op.AsyncExecutions.idle;

import java.util.List;
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

import utils.async.op.AsyncExecutions;
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
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@Before
	public void setup() {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		
		m_execList = FStream.range(0, 5)
							.map(idx -> idle(idx, 100, MILLISECONDS))
							.toList();
		m_failed = AsyncExecutions.throwAsync(m_error);
		m_cancelled = AsyncExecutions.cancelAsync();
		
		m_gen = FStream.from(m_execList);
		m_gen2 = m_gen.concatWith(m_failed);
		m_gen3 = m_gen.concatWith(m_cancelled);
	}
	
	@Test
	public void test01() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(0, exec.getCurrentExecutionIndex());
		
		StartableExecution<?> elm = exec.getCurrentExecution();
		Assert.assertEquals(true, elm.isStarted());
		
		exec.waitForFinished();
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
		exec.waitForFinished();
		
		// finish_listener가 호출될 때까지 일정시간동안 대기한다.
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(AsyncResult.completed(Integer.valueOf(4)));
	}
	
	@Test
	public void test03() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		boolean wasRunning = exec.waitForFinished(250, TimeUnit.MILLISECONDS).isRunning();
		exec.cancel(true);
		MILLISECONDS.sleep(50);
		
		Assert.assertEquals(true, wasRunning);
		verify(m_doneListener, times(1)).accept(AsyncResult.cancelled());
		Assert.assertEquals(2, exec.getCurrentExecutionIndex());
		Assert.assertTrue(m_execList.get(2).isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen2);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		MILLISECONDS.sleep(50);

		Assert.assertEquals(true, m_failed.isFailed());
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(m_error, exec.poll().getCause());
		
		verify(m_doneListener, times(1)).accept(AsyncResult.failed(m_error));
		Assert.assertEquals(5, exec.getCurrentExecutionIndex());
	}
	
	@Test
	public void test05() throws Exception {
		SequentialAsyncExecution<Integer> exec = SequentialAsyncExecution.of(m_gen3);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		MILLISECONDS.sleep(50);

		Assert.assertEquals(true, m_cancelled.isCancelled());
		Assert.assertEquals(true, exec.isCancelled());
		
		verify(m_doneListener, times(1)).accept(AsyncResult.cancelled());
		Assert.assertEquals(5, exec.getCurrentExecutionIndex());
	}
}
