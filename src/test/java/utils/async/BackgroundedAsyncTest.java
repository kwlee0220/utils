package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;
import utils.async.op.BackgroundedAsyncExecution;
import utils.func.Result;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class BackgroundedAsyncTest {
	private final Exception m_error = new Exception();
	
	@Mock Consumer<AsyncResult<Integer>> m_doneListener;
	
	@BeforeEach
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(50));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Thread.sleep(30);
		Assertions.assertEquals(true, exec.isStarted());
		Assertions.assertEquals(true, fg.isStarted());
		Assertions.assertEquals(true, bg.isStarted());
		
		fg.waitForFinished();
		Thread.sleep(100);
		Assertions.assertEquals(true, exec.isCompleted());
		Assertions.assertEquals(true, fg.isCompleted());
		Assertions.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.idle(null, Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		boolean ok = exec.cancel(true);
		Assertions.assertEquals(true, ok);
		
		Thread.sleep(30);
		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(true, fg.isCancelled());
		Assertions.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		StartableExecution<String> base = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> fg = AsyncExecutions.sequential(base, AsyncExecutions.throwAsync(m_error));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<?> exec = AsyncExecutions.backgrounded(fg, bg);
		
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForFinished();
		
		Assertions.assertEquals(true, exec.isFailed());
		Assertions.assertEquals(true, fg.isFailed());
		Assertions.assertEquals(true, bg.isCancelled());
	}
	
	@Test
	public void test04() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(300));
		StartableExecution<?> bg = AsyncExecutions.throwAsync(m_error);
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());

		exec.start();
		bg.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isRunning());
		Assertions.assertEquals(true, fg.isRunning());
		Assertions.assertEquals(true, bg.isFailed());
	}

	@Test
	public void getters_return_constructor_args() {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(50));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofMillis(50));
		BackgroundedAsyncExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		Assertions.assertSame(fg, exec.getForegroundAsyncExecution());
		Assertions.assertSame(bg, exec.getBackgroundAsyncExecution());
	}

	@Test
	public void bg_completes_first_fg_keeps_running() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("done", Duration.ofMillis(400));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofMillis(100));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		exec.start();
		bg.waitForFinished();
		Thread.sleep(30);

		// bg가 먼저 끝나도 fg는 계속 진행.
		Assertions.assertEquals(true, bg.isCompleted());
		Assertions.assertEquals(true, fg.isRunning());
		Assertions.assertEquals(true, exec.isRunning());

		exec.waitForFinished();
		Assertions.assertEquals(true, exec.isCompleted());
		Assertions.assertEquals(true, fg.isCompleted());
	}

	@Test
	public void result_value_propagated_from_foreground() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("payload", Duration.ofMillis(100));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertEquals(true, exec.isCompleted());
		Assertions.assertEquals("payload", result.get());
	}

	@Test
	public void cancel_before_start_transitions_to_CANCELLED_without_starting_either() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(500));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofMillis(500));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		exec.cancel(true);

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(AsyncState.NOT_STARTED, fg.getState());
		Assertions.assertEquals(AsyncState.NOT_STARTED, bg.getState());

		// 이미 종료된 상태에서 start()를 호출해도 fg/bg는 시작되지 않아야 함.
		exec.start();
		Thread.sleep(50);
		Assertions.assertEquals(AsyncState.NOT_STARTED, fg.getState());
		Assertions.assertEquals(AsyncState.NOT_STARTED, bg.getState());
	}

	@Test
	public void fg_self_cancel_propagates_to_exec_and_cancels_bg() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(500));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		exec.start();
		exec.waitForStarted();
		fg.cancel(true);
		exec.waitForFinished();
		Thread.sleep(30);

		Assertions.assertEquals(true, exec.isCancelled());
		Assertions.assertEquals(true, fg.isCancelled());
		Assertions.assertEquals(true, bg.isCancelled());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Test
	public void whenFinished_fires_with_result_on_completion() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(100));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		Consumer listener = org.mockito.Mockito.mock(Consumer.class);
		exec.whenFinished(listener);

		exec.start();
		exec.waitForFinished();
		Thread.sleep(50);

		verify(listener, times(1)).accept(any());
	}

	@Test
	public void whenFinished_carries_correct_result_on_cancel() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(500));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		AtomicReference<Result<String>> received = new AtomicReference<>();
		exec.whenFinished(received::set);

		exec.start();
		exec.waitForStarted();
		exec.cancel(true);
		exec.waitForFinished();
		Thread.sleep(50);

		Assertions.assertNotNull(received.get());
		Assertions.assertEquals(true, received.get().isNone());
	}

	@Test
	public void state_transitions_pass_through_RUNNING_on_success() throws Exception {
		StartableExecution<String> fg = AsyncExecutions.idle("v", Duration.ofMillis(100));
		StartableExecution<?> bg = AsyncExecutions.idle(Duration.ofSeconds(10));
		StartableExecution<String> exec = AsyncExecutions.backgrounded(fg, bg);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		exec.start();
		exec.waitForStarted();
		Assertions.assertEquals(AsyncState.RUNNING, exec.getState());
		exec.waitForFinished();
		Assertions.assertEquals(AsyncState.COMPLETED, exec.getState());
	}
}
