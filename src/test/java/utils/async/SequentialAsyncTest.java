package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static utils.async.op.AsyncExecutions.idle;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;
import utils.async.op.SequentialAsyncExecution;
import utils.func.Result;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class SequentialAsyncTest {
	private List<StartableExecution<Integer>> m_execList;
	private StartableExecution<Integer> m_failed;
	private StartableExecution<Integer> m_cancelled;
	
	private FStream<StartableExecution<?>> m_gen100x5;
	private FStream<StartableExecution<?>> m_gen2;
	private FStream<StartableExecution<?>> m_gen3;
	private final Exception m_error = new Exception();
	
	@Mock Consumer<Result<Object>> m_doneListener;
	
	@BeforeEach
	public void setup() {
		m_execList = FStream.range(0, 5)
							.map(idx -> idle(idx, Duration.ofMillis(100)))
							.toList();
		m_failed = AsyncExecutions.throwAsync(m_error);
		m_cancelled = AsyncExecutions.cancelAsync();
		
		m_gen100x5 = FStream.from(m_execList);
		m_gen2 = m_gen100x5.concatWith(m_failed);
		m_gen3 = m_gen100x5.concatWith(m_cancelled);
	}
	
	@Test
	public void test01() throws Exception {
		SequentialAsyncExecution exec = SequentialAsyncExecution.of(m_gen100x5);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assertions.assertEquals(true, exec.isStarted());
		Assertions.assertEquals(0, exec.getCurrentExecutionIndex());
		
		StartableExecution<?> elm = exec.getCurrentExecution();
		Assertions.assertEquals(true, elm.isStarted());
		
		exec.waitForFinished();
		Assertions.assertEquals(4, (int)exec.get());

		MILLISECONDS.sleep(50);
		for ( StartableExecution<Integer> elmExec: m_execList ) {
			Assertions.assertTrue(elmExec.isCompleted());
		}
	}
	
	@Test
	public void test02() throws Exception {
		SequentialAsyncExecution exec = SequentialAsyncExecution.of(m_gen100x5);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		
		// finish_listener가 호출될 때까지 일정시간동안 대기한다.
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(Result.success(Integer.valueOf(4)));
	}
	
	@Test
	public void test03() throws Exception {
		SequentialAsyncExecution exec = SequentialAsyncExecution.of(m_gen100x5);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		boolean wasRunning = exec.waitForFinished(250, TimeUnit.MILLISECONDS).isRunning();
		boolean cancelled = exec.cancel(true);
		Assertions.assertEquals(true, cancelled);
		MILLISECONDS.sleep(50);
		
		Assertions.assertEquals(true, wasRunning);
		verify(m_doneListener, times(1)).accept(Result.none());
		Assertions.assertEquals(2, exec.getCurrentExecutionIndex());
		Assertions.assertTrue(m_execList.get(2).isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		SequentialAsyncExecution exec = SequentialAsyncExecution.of(m_gen2);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		MILLISECONDS.sleep(50);

		Assertions.assertEquals(true, m_failed.isFailed());
		Assertions.assertEquals(true, exec.isFailed());
		Assertions.assertEquals(m_error, exec.poll().getFailureCause());
		
		verify(m_doneListener, times(1)).accept(Result.failure(m_error));
		Assertions.assertEquals(5, exec.getCurrentExecutionIndex());
	}
	
	@Test
	public void test05() throws Exception {
		SequentialAsyncExecution exec = SequentialAsyncExecution.of(m_gen3);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		MILLISECONDS.sleep(50);

		Assertions.assertEquals(true, m_cancelled.isCancelled());
		Assertions.assertEquals(true, exec.isCancelled());
		
		verify(m_doneListener, times(1)).accept(Result.none());
		Assertions.assertEquals(5, exec.getCurrentExecutionIndex());
	}
}
