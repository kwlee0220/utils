package utils.thread;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.async.CompletableFutureAsyncExecution;
import utils.async.Execution;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.FOption;
import utils.func.Result;

/**
 * {@link CompletionFStream}의 완료 순서 수집, 등록·시작 구분, 용량 제한, 종료 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
public class CompletionFStreamTest {
	private final ExecutorService m_executor = Executors.newFixedThreadPool(5);
	private final CompletionFStream<Integer> m_completions = new CompletionFStream<>(5);

	@AfterEach
	public void tearDown() {
		m_completions.close();
		m_executor.shutdownNow();
	}

	@Test
	public void results_arrive_in_completion_order() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(300); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 2; }, m_executor);

		assertEquals(1, (int)m_completions.next().get().get());
		assertEquals(2, (int)m_completions.next().get().get());
		assertEquals(0, (int)m_completions.next().get().get());

		assertThrows(TimeoutException.class, () -> m_completions.next(10, MILLISECONDS));

		m_completions.finishSubmissions();
		assertTrue(m_completions.next(0, MILLISECONDS).isAbsent());
	}

	@Test
	public void failed_execution_carries_cause() throws Exception {
		IllegalArgumentException cause = new IllegalArgumentException("boom");
		m_completions.submit(() -> { throw cause; }, m_executor);

		Result<Integer> result = m_completions.next().get();
		assertTrue(result.isFailed());
		assertSame(cause, result.getCause());
	}

	@Test
	public void submit_after_close_or_finish_is_rejected() throws Exception {
		CompletionFStream<Integer> closed = new CompletionFStream<>(2);
		closed.close();
		assertThrows(IllegalStateException.class, () -> closed.submit(() -> 0, m_executor));

		m_completions.finishSubmissions();
		assertThrows(IllegalStateException.class, () -> m_completions.submit(() -> 0, m_executor));
		assertThrows(IllegalStateException.class,
					() -> m_completions.register(Executions.supplyCheckedAsync(() -> 0, m_executor)));
		assertTrue(m_completions.isSubmissionFinished());
	}

	@Test
	public void null_arguments_are_rejected() {
		assertThrows(IllegalArgumentException.class, () -> m_completions.submit((Callable<Integer>)null, m_executor));
		assertThrows(IllegalArgumentException.class, () -> m_completions.submit((StartableExecution<Integer>)null));
		assertThrows(IllegalArgumentException.class, () -> m_completions.register(null));
		assertThrows(IllegalArgumentException.class, () -> new CompletionFStream<Integer>(0));
	}

	// ---- 등록(register)과 시작(submit)의 구분 ----

	@Test
	public void register_collects_without_starting() throws Exception {
		CompletableFutureAsyncExecution<Integer> exec = Executions.supplyCheckedAsync(() -> 42, m_executor);
		m_completions.register(exec);

		Thread.sleep(100);
		assertTrue(exec.isNotStarted(), "register() must not start the execution");
		assertThrows(TimeoutException.class, () -> m_completions.next(50, MILLISECONDS));

		exec.start();								// 시작은 호출자가 맡는다
		assertEquals(42, (int)m_completions.next().get().get());
	}

	@Test
	public void register_collects_an_execution_cancelled_before_start() throws Exception {
		CompletableFutureAsyncExecution<Integer> exec = Executions.supplyCheckedAsync(() -> 42, m_executor);
		exec.cancel(true);

		m_completions.register(exec);

		Result<Integer> result = m_completions.next().get();
		assertTrue(result.isNone(), "a cancelled result is expected: " + result);
	}

	@Test
	public void submit_requires_a_not_started_execution() throws Exception {
		CompletableFutureAsyncExecution<Integer> started = Executions.supplyCheckedAsync(() -> 42, m_executor);
		started.start();

		assertThrows(IllegalArgumentException.class, () -> m_completions.submit(started));
		// 이미 시작된 작업은 register()로 수집한다.
		m_completions.register(started);
		assertEquals(42, (int)m_completions.next().get().get());
	}

	@Test
	public void execution_that_failed_to_start_is_collected_as_cancelled() throws Exception {
		// start()가 작업을 끝내지 않고 던져도 취소 결과가 수집되어, finishSubmissions() 뒤 스트림이 끝난다.
		StartableExecution<Integer> failing = new CompletableFutureAsyncExecution<Integer>() {
			@Override
			protected CompletableFuture<? extends Integer> startExecution() {
				return CompletableFuture.completedFuture(0);
			}

			@Override
			public void start() {
				throw new IllegalStateException("start failed");
			}
		};

		assertThrows(IllegalStateException.class, () -> m_completions.submit(failing));
		m_completions.finishSubmissions();

		assertTrue(m_completions.next().get().isNone());
		assertTrue(m_completions.next(1, TimeUnit.SECONDS).isAbsent());
	}

	// ---- 용량 제한 ----

	@Test
	public void submit_waits_when_capacity_is_reached() throws Exception {
		// 꺼내지 않은 작업이 용량(2)에 이르면 다음 등록이 기다린다. 결과는 용량 이상 쌓이지 않는다.
		CompletionFStream<Integer> completions = new CompletionFStream<>(2);
		completions.submit(() -> 0, m_executor);
		completions.submit(() -> 1, m_executor);

		CompletableFuture<Execution<Integer>> third = CompletableFuture.supplyAsync(() -> {
			try {
				return completions.submit(() -> 2, m_executor);
			}
			catch ( InterruptedException e ) {
				throw new IllegalStateException(e);
			}
		});
		Thread.sleep(200);
		assertFalse(third.isDone(), "the third submit must wait for a free slot");

		completions.next().get();
		third.get(2, TimeUnit.SECONDS);
		completions.finishSubmissions();
		assertEquals(2, completions.toList().size());
	}

	@Test
	public void consumes_more_tasks_than_capacity_with_a_concurrent_consumer() throws Exception {
		CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
			try {
				for ( int i = 0; i < 20; ++i ) {
					final int v = i;
					m_completions.submit(() -> v, m_executor);
				}
				m_completions.finishSubmissions();
			}
			catch ( InterruptedException e ) {
				throw new IllegalStateException(e);
			}
		});

		int total = 0;
		int count = 0;
		FOption<Result<Integer>> next;
		while ( (next = m_completions.next()).isPresent() ) {
			total += next.get().get();
			++count;
		}
		producer.get(5, TimeUnit.SECONDS);
		assertEquals(20, count);
		assertEquals(190, total);
	}

	@Test
	public void close_releases_a_waiting_submitter() throws Exception {
		CompletionFStream<Integer> completions = new CompletionFStream<>(1);
		completions.submit(() -> 0, m_executor);

		CompletableFuture<Throwable> waiting = CompletableFuture.supplyAsync(() -> {
			try {
				completions.submit(() -> 1, m_executor);
				return null;
			}
			catch ( Throwable e ) {
				return e;
			}
		});
		Thread.sleep(200);
		completions.close();

		assertTrue(waiting.get(2, TimeUnit.SECONDS) instanceof IllegalStateException);
	}

	// ---- 종료 ----

	@Test
	public void finishSubmissions_delivers_results_of_unfinished_executions() throws Exception {
		// 이전의 endOfSupply()와 달리, 아직 끝나지 않은 작업의 결과도 모두 전달한 뒤 끝난다.
		CountDownLatch release = new CountDownLatch(1);
		m_completions.submit(() -> 0, m_executor);
		m_completions.submit(() -> { release.await(); return 1; }, m_executor);

		m_completions.finishSubmissions();
		assertEquals(0, (int)m_completions.next().get().get());
		assertThrows(TimeoutException.class, () -> m_completions.next(100, MILLISECONDS));

		release.countDown();
		assertEquals(1, (int)m_completions.next().get().get());
		assertTrue(m_completions.next().isAbsent());
	}

	@Test
	public void results_after_close_are_dropped() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Execution<Integer> late = m_completions.submit(() -> { release.await(); return 1; }, m_executor);
		m_completions.close();
		release.countDown();

		assertTrue(late.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		assertTrue(m_completions.next().isAbsent(), "a closed stream yields no more results");
	}

	@Test
	public void full_buffer_never_blocks_a_completion_callback() throws Exception {
		// 꺼내지 않은 결과가 용량만큼 쌓여도 작업의 lock은 잡혀 있지 않다(완료 콜백이 기다리지 않는다).
		CompletionFStream<Integer> completions = new CompletionFStream<>(2);
		Execution<Integer> a = completions.submit(() -> 0, m_executor);
		Execution<Integer> b = completions.submit(() -> 1, m_executor);
		a.waitForFinished(2, TimeUnit.SECONDS);
		b.waitForFinished(2, TimeUnit.SECONDS);

		for ( Execution<Integer> exec: List.of(a, b) ) {
			CompletableFuture<Boolean> query = CompletableFuture.supplyAsync(() -> exec.cancel(true) || exec.isDone());
			assertTrue(query.get(2, TimeUnit.SECONDS), "execution lock is held: " + exec);
		}
		completions.close();
	}
}
