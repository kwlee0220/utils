package utils.async.op;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.async.AsyncResult;
import utils.async.CompletableFutureAsyncExecution;
import utils.async.EventDrivenExecution;
import utils.async.Execution;
import utils.async.Executions;
import utils.async.StartableExecution;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;


/**
 * {@link DelayedAsyncExecution}의 지연 시작·결과 전파·예외 검증.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DelayedAsyncExecutionTest {

	// ---------- 기본 동작 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void getters_return_constructor_args() {
		StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(50));
		Duration delay = Duration.ofMillis(100);
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, delay);

		Assertions.assertSame(target, exec.getTargetExecution());
		Assertions.assertEquals(delay, exec.getDelay());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_with_zero_delay_runs_immediately_and_propagates_result() throws Exception {
		StartableExecution<String> target = AsyncExecutions.idle("payload", Duration.ofMillis(50));
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, Duration.ZERO);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isCompleted());
		Assertions.assertEquals("payload", result.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_with_positive_delay_postpones_target_start() throws Exception {
		AtomicBoolean targetStarted = new AtomicBoolean(false);
		EventDrivenExecution<String> target = new TrackingExecution<>(targetStarted);
		Duration delay = Duration.ofMillis(300);
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, delay);

		Instant t0 = Instant.now();
		exec.start();

		// 시작 직후에는 target이 아직 시작되지 않아야 함.
		Thread.sleep(50);
		Assertions.assertFalse(targetStarted.get(), "target은 delay 후에야 시작되어야 함");

		// target을 완료시켜 delay 종료 후 결과 전파 확인.
		// 적어도 delay 만큼 기다린 뒤 target 시작 확인.
		Thread.sleep(350);
		Assertions.assertTrue(targetStarted.get(), "delay 후 target이 시작되어야 함");
		target.notifyCompleted("done");

		AsyncResult<String> result = exec.waitForFinished();
		Assertions.assertEquals("done", result.get());
		Duration elapsed = Duration.between(t0, Instant.now());
		Assertions.assertTrue(elapsed.compareTo(delay) >= 0, "지연 시간이 적용되어야 함: 실제 " + elapsed);
	}

	// ---------- 결과 전파 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void target_failure_propagates() throws Exception {
		Exception cause = new RuntimeException("boom");
		StartableExecution<String> target = AsyncExecutions.throwAsync(cause);
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, Duration.ofMillis(50));

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isFailed());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void non_startable_target_already_started_propagates_result() throws Exception {
		EventDrivenExecution<String> target = new EventDrivenExecution<>();
		target.notifyStarted();
		target.notifyCompleted("already-done");

		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, Duration.ofMillis(50));
		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertEquals("already-done", result.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void non_startable_target_not_started_fails_after_delay() throws Exception {
		// target은 EventDrivenExecution(StartableExecution 미구현)이고 NOT_STARTED 상태.
		// delay 경과 후 target.isNotStarted()가 true이면 IllegalStateException → 본 실행 FAILED.
		EventDrivenExecution<String> target = new EventDrivenExecution<>();
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(target, Duration.ofMillis(50));

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isFailed(), "target이 시작 불가능하면 본 실행은 실패해야 함");
	}

	// ---------- 인자 검증 ----------

	@Test
	public void null_target_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			AsyncExecutions.delay((Execution)null, Duration.ofMillis(10));
			});
	}

	@Test
	public void null_delay_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(10));
			AsyncExecutions.delay(target, null);
			});
	}

	@Test
	public void negative_delay_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			StartableExecution<String> target = AsyncExecutions.idle("v", Duration.ofMillis(10));
			AsyncExecutions.delay(target, Duration.ofMillis(-1));
			});
	}

	// ---------- EventDrivenExecution.delay 체이닝 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void chained_from_event_driven_execution_delay() throws Exception {
		// supplyAsync는 CompletableFutureAsyncExecution(=EventDrivenExecution)을 반환하므로
		// .delay 호출 가능.
		CompletableFutureAsyncExecution<String> upstream = Executions.supplyAsync(() -> "chained");
		upstream.start();
		DelayedAsyncExecution<String> exec = AsyncExecutions.delay(upstream, Duration.ofMillis(100));

		Instant t0 = Instant.now();
		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertEquals("chained", result.get());
		Assertions.assertTrue(Duration.between(t0, Instant.now()).compareTo(Duration.ofMillis(100)) >= 0, "delay 적용 확인");
	}

	// ---------- 헬퍼 ----------

	/**
	 * 외부에서 시작 사실을 관측하기 위한 EventDrivenExecution + StartableExecution.
	 */
	private static class TrackingExecution<T> extends EventDrivenExecution<T>
											implements StartableExecution<T> {
		private final AtomicBoolean m_startedFlag;

		TrackingExecution(AtomicBoolean startedFlag) {
			m_startedFlag = startedFlag;
		}

		@Override
		public void start() {
			m_startedFlag.set(true);
			notifyStarted();
		}
	}
}
