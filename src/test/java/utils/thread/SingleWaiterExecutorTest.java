package utils.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.async.CompletableFutureAsyncExecution;
import utils.async.Executions;
import utils.async.StartableExecution;


/**
 * {@link SingleWaiterExecutor}의 latest-wins 동작과 시작 실패 처리를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class SingleWaiterExecutorTest {
	/** latch가 열릴 때까지 끝나지 않는 작업. */
	private static StartableExecution<Void> blockingJob(CountDownLatch release) {
		return Executions.toExecution(() -> release.await());
	}

	/** {@code start()}가 실행을 끝내지 않은 채 예외를 던지는 작업(종료 콜백이 오지 않는다). */
	private static StartableExecution<Void> failingToStartJob() {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				return CompletableFuture.completedFuture(null);
			}

			@Override
			public void start() {
				throw new IllegalStateException("start failed");
			}
		};
	}

	@Test
	public void latest_pending_job_wins() throws Exception {
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		CountDownLatch release = new CountDownLatch(1);
		StartableExecution<Void> running = blockingJob(release);
		StartableExecution<Void> replaced = blockingJob(new CountDownLatch(0));
		StartableExecution<Void> latest = blockingJob(new CountDownLatch(0));

		executor.submit(running);
		executor.submit(replaced);
		executor.submit(latest);
		Assertions.assertTrue(replaced.isCancelled(), "a replaced pending job is cancelled");
		Assertions.assertFalse(latest.isStarted());

		release.countDown();
		Assertions.assertTrue(latest.waitForFinished(5, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void start_failure_does_not_stall_the_executor() throws Exception {
		// 이전에는 start()가 던지면 실행 슬롯이 남아, 이후 제출한 작업이 영원히 대기했다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();

		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
															() -> executor.submit(failingToStartJob()));
		Assertions.assertEquals("start failed", e.getMessage());

		StartableExecution<Void> next = blockingJob(new CountDownLatch(0));
		executor.submit(next);
		Assertions.assertTrue(next.waitForFinished(5, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void start_failure_of_a_pending_job_moves_on() throws Exception {
		// 종료 콜백에서 이어 시작한 대기 작업의 start()가 던져도 실행기가 멈추지 않는다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		CountDownLatch release = new CountDownLatch(1);
		StartableExecution<Void> running = blockingJob(release);
		executor.submit(running);
		executor.submit(failingToStartJob());

		release.countDown();
		Assertions.assertTrue(running.waitForFinished(5, TimeUnit.SECONDS).isCompleted());

		Thread.sleep(300);			// 종료 콜백(비동기)이 대기 작업의 시작을 시도하고 실패를 정리할 시간

		// 실행 슬롯이 비었으므로 새 작업은 대기 없이 곧바로 시작되어야 한다. 이전에는 시작에 실패한 작업이 실행 슬롯과
		// 대기 슬롯에 남아, 새 작업이 대기 슬롯에 들어갔다(그 작업을 교체·취소하면서 우연히 회복되었다).
		StartableExecution<Void> next = blockingJob(new CountDownLatch(1));
		executor.submit(next);
		Assertions.assertTrue(next.isStarted(), "the executor is still occupied by the job that failed to start");
		next.cancel(true);
	}

	@Test
	public void start_is_called_outside_the_executor_lock() throws Exception {
		// start() 안에서 다른 쓰레드가 실행기를 쓸 수 있어야 한다. 이전에는 내부 lock을 쥔 채 start()를 불러,
		// 그 쓰레드를 기다리면 교착했다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		CompletableFuture<Boolean> otherThreadDone = new CompletableFuture<>();
		StartableExecution<Void> job = new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				Thread other = new Thread(() -> {
					executor.submit(blockingJob(new CountDownLatch(0)));
					otherThreadDone.complete(true);
				});
				other.start();
				try {
					other.join(2000);
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
				}
				return CompletableFuture.completedFuture(null);
			}
		};

		executor.submit(job);

		Assertions.assertTrue(otherThreadDone.getNow(false), "another thread was blocked by the executor lock");
	}

	@Test
	public void shutdownNow_cancels_jobs_and_rejects_new_ones() throws Exception {
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		StartableExecution<Void> running = blockingJob(new CountDownLatch(1));
		StartableExecution<Void> pending = blockingJob(new CountDownLatch(0));
		executor.submit(running);
		executor.submit(pending);

		executor.shutdownNow();

		Assertions.assertTrue(pending.isCancelled());
		Assertions.assertTrue(running.waitForFinished(5, TimeUnit.SECONDS).isCancelled());
		Assertions.assertTrue(executor.isShutdown());
		Assertions.assertThrows(IllegalStateException.class,
								() -> executor.submit(blockingJob(new CountDownLatch(0))));
	}

	// ---- 외부 검토: 취소 예외, 중복 제출, 시작 실패의 종료 콜백 ----

	/** {@code cancel()}이 예외를 던지는 작업. */
	private static StartableExecution<Void> failingToCancelJob() {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				return new CompletableFuture<>();
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				throw new IllegalStateException("cancel failed");
			}
		};
	}

	@Test
	public void shutdownNow_cancels_every_job_even_if_one_cancel_throws() throws Exception {
		// 대기 작업의 취소가 던져도 실행 중인 작업을 취소한다. 예외는 모두 시도한 뒤 던진다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		StartableExecution<Void> running = blockingJob(new CountDownLatch(1));
		executor.submit(running);
		executor.submit(failingToCancelJob());

		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, executor::shutdownNow);
		Assertions.assertEquals("cancel failed", e.getMessage());
		Assertions.assertTrue(running.waitForFinished(5, TimeUnit.SECONDS).isCancelled());
		Assertions.assertTrue(executor.isShutdown());
		Assertions.assertDoesNotThrow(executor::shutdownNow);		// 다시 불러도 아무 일도 하지 않는다
	}

	@Test
	public void failure_to_cancel_a_replaced_job_does_not_fail_submit() throws Exception {
		// 새 작업은 이미 접수되었으므로, 교체된 작업의 취소 실패를 호출자에게 알리지 않는다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		CountDownLatch release = new CountDownLatch(1);
		executor.submit(blockingJob(release));
		executor.submit(failingToCancelJob());

		StartableExecution<Void> latest = blockingJob(new CountDownLatch(0));
		Assertions.assertDoesNotThrow(() -> executor.submit(latest));

		release.countDown();
		Assertions.assertTrue(latest.waitForFinished(5, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void resubmitting_the_same_job_is_rejected() throws Exception {
		// 이전에는 대기 중인 작업을 다시 제출하면 교체 과정에서 그 작업 자체가 취소되었다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();
		CountDownLatch release = new CountDownLatch(1);
		StartableExecution<Void> running = blockingJob(release);
		StartableExecution<Void> pending = blockingJob(new CountDownLatch(0));
		executor.submit(running);
		executor.submit(pending);

		Assertions.assertThrows(IllegalArgumentException.class, () -> executor.submit(pending));
		Assertions.assertThrows(IllegalArgumentException.class, () -> executor.submit(running));
		Assertions.assertFalse(pending.isCancelled());
		Assertions.assertFalse(running.isCancelled());

		release.countDown();
		Assertions.assertTrue(pending.waitForFinished(5, TimeUnit.SECONDS).isCompleted());
	}

	@Test
	public void job_that_failed_to_start_still_finishes() throws Exception {
		// start()가 작업을 끝내지 않고 던져도 종료 콜백이 오도록 실행기가 작업을 취소한다.
		SingleWaiterExecutor executor = new SingleWaiterExecutor();

		StartableExecution<Void> direct = failingToStartJob();
		Assertions.assertThrows(IllegalStateException.class, () -> executor.submit(direct));
		Assertions.assertTrue(direct.waitForFinished(5, TimeUnit.SECONDS).isCancelled());

		// 앞 작업이 끝난 뒤 이어 시작한 경우(예외를 받을 호출자가 없음)도 마찬가지다.
		CountDownLatch release = new CountDownLatch(1);
		executor.submit(blockingJob(release));
		StartableExecution<Void> chained = failingToStartJob();
		executor.submit(chained);
		release.countDown();
		Assertions.assertTrue(chained.waitForFinished(5, TimeUnit.SECONDS).isCancelled());
	}
}
