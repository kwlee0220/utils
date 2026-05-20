package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;
import utils.func.Result;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class FoldAsyncTest {
	private FStream<StartableExecution<Integer>> m_gen;
	private FStream<StartableExecution<Integer>> m_gen2;
	private FStream<StartableExecution<Integer>> m_gen3;
	private final Exception m_error = new Exception();
	
	@Mock Consumer<Result<Integer>> m_doneListener;
	
	@BeforeEach
	public void setup() {
		m_gen = FStream.range(0, 5)
						.map(idx -> AsyncExecutions.idle(idx, Duration.ofMillis(100)));
		m_gen2 = m_gen.concatWith(AsyncExecutions.throwAsync(m_error));
		m_gen3 = m_gen.concatWith(AsyncExecutions.cancelAsync());
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assertions.assertEquals(true, exec.isStarted());
		
		MILLISECONDS.sleep(50);
		Assertions.assertEquals(true, exec.isStarted());
		
		exec.waitForFinished();
		Assertions.assertEquals(10, (int)exec.get());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		MILLISECONDS.sleep(100);
		verify(m_doneListener, times(1)).accept(Result.success(Integer.valueOf(10)));
	}

	@Test
	public void test03() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen, 0, (a,n) -> a+n);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		Assertions.assertEquals(true, exec.waitForFinished(230, TimeUnit.MILLISECONDS).isRunning());
		
		exec.cancel(true);
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(Result.none());
	}

	@Test
	public void test04() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen2, 0, (a,n) -> a+n);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		
		Assertions.assertEquals(true, exec.isFailed());
		Assertions.assertEquals(m_error, exec.poll().getFailureCause());
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(Result.failure(m_error));
	}
	
	@Test
	public void test05() throws Exception {
		StartableExecution<Integer> exec = AsyncExecutions.fold(m_gen3, 0, (a,n) -> a+n);
		exec.whenFinishedAsync(m_doneListener);
		
		exec.start();
		exec.waitForFinished();
		
		Assertions.assertEquals(true, exec.isCancelled());
		MILLISECONDS.sleep(50);
		verify(m_doneListener, times(1)).accept(Result.none());
	}
}
