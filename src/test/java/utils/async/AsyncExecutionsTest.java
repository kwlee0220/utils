package utils.async;


import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;


/**
 * {@link AsyncExecutions} 정적 팩토리 메소드의 라이프사이클 검증.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class AsyncExecutionsTest {

	@Test
	public void nop_completes_synchronously_with_given_result() throws Exception {
		AbstractAsyncExecution<String> exec = AsyncExecutions.nop("abc");

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());

		exec.start();

		Assertions.assertTrue(exec.isStarted());
		Assertions.assertTrue(exec.isCompleted());
		Assertions.assertEquals("abc", exec.get());
	}

	@Test
	public void idle_completes_after_duration() throws Exception {
		Duration duration = Duration.ofMillis(300);
		StartableExecution<Void> exec = AsyncExecutions.idle(duration);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());

		long started = System.currentTimeMillis();
		exec.start();
		Assertions.assertTrue(exec.isStarted());

		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertTrue(result.isCompleted());
		Assertions.assertNull(result.get());
		Assertions.assertTrue(System.currentTimeMillis() - started >= duration.toMillis(), "최소 지연이 경과해야 함");
	}

	@Test
	public void idle_cancel_before_completion_transitions_to_cancelled() throws Exception {
		// duration이 짧으면 cancel 호출 전에 future가 완료되어 cancel(true)가 false를 반환한다.
		// 따라서 cancel-while-running 의미론을 검증하려면 충분히 긴 duration이 필요하다.
		StartableExecution<Void> exec = AsyncExecutions.idle(Duration.ofSeconds(10));

		exec.start();
		Assertions.assertTrue(exec.isStarted());

		boolean cancelled = exec.cancel(true);

		Assertions.assertTrue(cancelled, "RUNNING 상태에서 cancel(true)는 성공해야 함");
		// cancel(true)는 CANCELLING 전이만 보장하므로 terminal CANCELLED까지 기다림.
		AsyncResult<Void> result = exec.waitForFinished();
		Assertions.assertTrue(result.isCancelled());
		Assertions.assertTrue(exec.isCancelled());
	}
}
