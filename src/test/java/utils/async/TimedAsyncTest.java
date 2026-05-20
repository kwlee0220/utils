package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;
import utils.async.op.TimedAsyncExecution;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class TimedAsyncTest {
	private final ScheduledExecutorService m_scheduler = Executors.newScheduledThreadPool(4);
	private final Exception m_error = new Exception();
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@BeforeEach
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(300));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assertions.assertEquals(true, exec.isStarted());
		Assertions.assertEquals(true, target.isStarted());
		
		exec.waitForFinished();
		Thread.sleep(30);
		Assertions.assertEquals(true, exec.isCompleted());
		Assertions.assertEquals(true, target.isCompleted());
		Assertions.assertEquals(false, exec.isTimedoutCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		exec.waitForFinished();
		
		Thread.sleep(30);
		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(true, target.isCancelled());
		Assertions.assertEquals(true, exec.isTimedoutCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		StartableExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		exec.cancel(true);
		exec.waitForFinished();

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(true, target.isCancelled());
	}

	@Test
	public void test04() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		StartableExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);
		
		exec.start();
		Thread.sleep(100);
		target.cancel(true);
		Thread.sleep(100);
		
		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(true, target.isCancelled());
	}
	
	@Test
	public void test05() throws Exception {
		StartableExecution<Object> target = AsyncExecutions.idle("done", Duration.ofMillis(300));
		target = AsyncExecutions.sequential(target, AsyncExecutions.throwAsync(m_error));
		TimedAsyncExecution<Object> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		exec.start();
		exec.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isFailed());
		Assertions.assertEquals(true, target.isFailed());
	}

	@Test
	public void getters_return_constructor_args() {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(50));
		Duration timeout = Duration.ofMillis(200);
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, timeout, m_scheduler);

		Assertions.assertSame(target, exec.getTargetOperation());
		Assertions.assertEquals(timeout, exec.getTimeout());
	}

	@Test
	public void result_value_propagated_from_target() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("payload", Duration.ofMillis(100));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertEquals(true, exec.isCompleted());
		Assertions.assertEquals("payload", result.get());
	}

	@Test
	public void external_cancel_isTimedoutCancelled_false() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);

		exec.start();
		Thread.sleep(50);
		exec.cancel(true);
		exec.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(false, exec.isTimedoutCancelled());
	}

	@Test
	public void target_self_cancel_isTimedoutCancelled_false() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);

		exec.start();
		Thread.sleep(50);
		target.cancel(true);
		exec.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(false, exec.isTimedoutCancelled());
	}

	@Test
	public void failure_isTimedoutCancelled_false() throws Exception {
		StartableExecution<Object> target = AsyncExecutions.idle("done", Duration.ofMillis(100));
		target = AsyncExecutions.sequential(target, AsyncExecutions.throwAsync(m_error));
		TimedAsyncExecution<Object> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		exec.start();
		exec.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isFailed());
		Assertions.assertEquals(false, exec.isTimedoutCancelled());
	}

	@Test
	public void isTimedoutCancelled_false_before_done() {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);

		Assertions.assertEquals(false, exec.isTimedoutCancelled());
	}

	@Test
	public void cancel_before_start_transitions_to_CANCELLED_without_starting_target() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("done", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		exec.cancel(true);

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(false, exec.isTimedoutCancelled());
		Assertions.assertEquals(AsyncState.NOT_STARTED, target.getState());

		// 시작 시도해도 이미 종료 상태이므로 target은 시작되지 않아야 함.
		exec.start();
		Thread.sleep(50);
		Assertions.assertEquals(AsyncState.NOT_STARTED, target.getState());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void whenFinished_fires_with_completed_result() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(100));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		@SuppressWarnings("rawtypes")
		Consumer listener = org.mockito.Mockito.mock(Consumer.class);
		exec.whenFinished(listener);

		exec.start();
		exec.waitForFinished();
		Thread.sleep(50);

		verify(listener, times(1)).accept(any());
	}

	@Test
	public void whenFinished_fires_on_timeout() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(150), m_scheduler);

		AtomicReference<utils.func.Result<String>> received = new AtomicReference<>();
		exec.whenFinished(received::set);

		exec.start();
		exec.waitForFinished();
		Thread.sleep(50);

		Assertions.assertNotNull(received.get());
		Assertions.assertEquals(true, received.get().isNone());
		Assertions.assertEquals(true, exec.isTimedoutCancelled());
	}

	@Test
	public void whenFinished_fires_on_external_cancel() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(500));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(300), m_scheduler);

		AtomicReference<utils.func.Result<String>> received = new AtomicReference<>();
		exec.whenFinished(received::set);

		exec.start();
		Thread.sleep(50);
		exec.cancel(true);
		exec.waitForFinished();
		Thread.sleep(50);

		Assertions.assertNotNull(received.get());
		Assertions.assertEquals(true, received.get().isNone());
	}

	@Test
	public void multiple_start_calls_are_idempotent() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(100));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		exec.start();
		exec.start();  // 두 번째 호출은 무시되어야 함 (notifyStarting 멱등).
		exec.waitForFinished();

		Assertions.assertEquals(true, exec.isCompleted());
	}

	@Test
	public void state_transitions_pass_through_RUNNING_on_success() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(100));
		TimedAsyncExecution<String> exec = AsyncExecutions.timed(target, Duration.ofMillis(500), m_scheduler);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		exec.start();
		exec.waitForStarted();
		Assertions.assertEquals(AsyncState.RUNNING, exec.getState());
		exec.waitForFinished();
		Assertions.assertEquals(AsyncState.COMPLETED, exec.getState());
	}
}
