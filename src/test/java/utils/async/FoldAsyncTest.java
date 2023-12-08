package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static utils.async.op.AsyncExecutions.cancelled;

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
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class FoldAsyncTest {
	private FStream<StartableExecution<Integer>> m_gen;
	private FStream<StartableExecution<Integer>> m_gen2;
	private FStream<StartableExecution<Integer>> m_gen3;
	private final Exception m_error = new Exception();
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@Before
	public void setup() {
		ScheduledExecutorService executors = Executors.newScheduledThreadPool(4);
		m_gen = FStream.range(0, 5)
						.map(idx -> AsyncExecutions.idle(idx, 100, MILLISECONDS,
														executors));
		m_gen2 = FStream.concat(m_gen, AsyncExecutions.failure(m_error));
		m_gen3 = FStream.concat(m_gen, cancelled());
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		
		MILLISECONDS.sleep(50);
		Assert.assertEquals(true, exec.isStarted());
		
		exec.pollInfinite();
		Assert.assertEquals(10, (int)exec.get());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.pollInfinite();
		verify(m_doneListener, times(1)).accept(AsyncResult.completed(Integer.valueOf(10)));
	}

	@Test
	public void test03() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		Assert.assertEquals(true, exec.poll(230, MILLISECONDS).isRunning());
		
		exec.cancel(true);
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(AsyncResult.cancelled());
	}

	@Test
	public void test04() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen2, 0, (a,n) -> a+n);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.pollInfinite();
		
		Assert.assertEquals(true, exec.isFailed());
		Assert.assertEquals(m_error, exec.poll().getCause());
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(AsyncResult.failed(m_error));
	}
	
	@Test
	public void test05() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen3, 0, (a,n) -> a+n);
		exec.whenFinished(m_doneListener);
		
		exec.start();
		exec.pollInfinite();
		
		Assert.assertEquals(true, exec.isCancelled());
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(AsyncResult.cancelled());
	}
}
