package utils.stream;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.StopWatch;
import utils.Tuple;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Unchecked;

/**
 * {@link OrderedMapAsyncStream} 전용 테스트.
 * <p>
 * 입력 순서 보존, 빈 입력/작은 입력 종료, mapper 예외 캡처, close 안전, 인터럽트 플래그
 * 복원, RejectedExecutionException fatal 처리, worker 병렬 실행을 다룬다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MapOrderedAsyncStreamTest {
	private final List<ExecutorService> m_executors = new ArrayList<>();

	@AfterEach
	public void tearDown() {
		for ( ExecutorService exec : m_executors ) {
			exec.shutdownNow();
		}
	}

	private ExecutorService track(ExecutorService exec) {
		m_executors.add(exec);
		return exec;
	}

	// ---------- 순서 보존 ----------

	@Test
	public void orderPreserved_despiteOutOfOrderCompletion() throws Exception {
		// 첫 원소가 가장 느리게 끝나도 결과는 입력 순서 그대로 방출되어야 한다.
		Function<Integer, Integer> mapper = i -> {
			// 0 → 200ms, 1 → 150ms, 2 → 100ms, ...
			Unchecked.runOrRTE(() -> Thread.sleep(Math.max(0, 200 - i * 50)));
			return i * 10;
		};

		List<Try<Integer>> results = FStream.range(0, 5)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(5, results.size());
		for ( int i = 0; i < 5; ++i ) {
			final int idx = i;
			Assertions.assertTrue(results.get(i).isSuccessful(), () -> "idx " + idx + " failed: " + results.get(idx));
			Assertions.assertEquals(Integer.valueOf(i * 10), results.get(i).get());
		}
	}

	// ---------- 종료 조건 ----------

	@Test
	public void emptyInput_terminatesCleanly() throws Exception {
		List<Try<Integer>> results = FStream.<Integer>of()
				.mapAsync(i -> i * 2, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(4))
				.map(Tuple::_2)
				.toList();

		Assertions.assertTrue(results.isEmpty());
	}

	@Test
	public void inputShorterThanWorkerCount_terminatesCleanly() throws Exception {
		// workerCount=4, input=2. counter 로직이 K < N 시 deadlock 없이 endOfSupply 호출해야.
		List<Try<Integer>> results = FStream.of(1, 2)
				.mapAsync(i -> i * 10, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(4))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(2, results.size());
		Assertions.assertEquals(Integer.valueOf(10), results.get(0).get());
		Assertions.assertEquals(Integer.valueOf(20), results.get(1).get());
	}

	@Test
	public void inputEqualsWorkerCount_terminatesCleanly() throws Exception {
		List<Try<Integer>> results = FStream.of(1, 2, 3)
				.mapAsync(i -> i * 10, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(3, results.size());
		Assertions.assertEquals(Integer.valueOf(10), results.get(0).get());
		Assertions.assertEquals(Integer.valueOf(30), results.get(2).get());
	}

	// ---------- mapper 예외 ----------

	@Test
	public void mapperException_yieldsTryFailure_butContinues() throws Exception {
		Function<Integer, Integer> mapper = i -> {
			if ( i == 2 ) {
				throw new IllegalStateException("boom at " + i);
			}
			return i * 10;
		};

		List<Try<Integer>> results = FStream.range(0, 5)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(2))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(5, results.size());
		Assertions.assertTrue(results.get(0).isSuccessful());
		Assertions.assertTrue(results.get(1).isSuccessful());
		Assertions.assertTrue(results.get(2).isFailed());
		Assertions.assertEquals("boom at 2", results.get(2).getCause().getMessage());
		Assertions.assertTrue(results.get(3).isSuccessful());
		Assertions.assertTrue(results.get(4).isSuccessful());
		Assertions.assertEquals(Integer.valueOf(0), results.get(0).get());
		Assertions.assertEquals(Integer.valueOf(30), results.get(3).get());
	}

	// ---------- close 안전 ----------

	@Test
	public void closeMidStream_doesNotHang() throws Exception {
		Function<Integer, Integer> mapper = i -> {
			Unchecked.runOrRTE(() -> Thread.sleep(50));
			return i;
		};

		FStream<Try<Integer>> stream = FStream.range(0, 100)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3))
				.map(Tuple::_2);

		// 일부만 소비하고 close.
		stream.next();
		stream.next();
		stream.close();

		// close 후 next는 빈 결과 또는 IllegalStateException — 둘 다 hang 없이 빠르게 반환되어야 한다.
		StopWatch watch = StopWatch.start();
		try {
			FOption<Try<Integer>> after = stream.next();
			Assertions.assertTrue(after.isAbsent(), "close 후 next는 empty 여야 함");
		}
		catch ( IllegalStateException expected ) {
			// 일부 FStream 구현은 close 후 next() 시 IllegalStateException — 둘 다 허용.
		}
		watch.stop();
		Assertions.assertTrue(watch.getElapsedInMillis() < 1000,
							() -> "close 후 next가 hang 되지 않아야 함 (elapsed=" + watch.getElapsedInMillis() + "ms)");
	}

	// ---------- 인터럽트 플래그 복원 ----------

	@Test
	public void interruptedDuringWait_returnsTryFailure_andRestoresFlag() throws Exception {
		// mapper가 충분히 길게 sleep하는 동안 consumer thread를 interrupt.
		// awaitResult가 InterruptedException을 잡아 Try.failure로 반환하고 인터럽트 플래그를 복원해야.
		Function<Integer, Integer> slowMapper = i -> {
			Unchecked.runOrRTE(() -> Thread.sleep(5000));
			return i;
		};

		FStream<Try<Integer>> stream = FStream.range(0, 5)
				.mapAsync(slowMapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(2))
				.map(Tuple::_2);

		AtomicReference<Try<Integer>> result = new AtomicReference<>();
		AtomicReference<Boolean> flagAfterReturn = new AtomicReference<>(false);
		CountDownLatch done = new CountDownLatch(1);

		Thread consumer = new Thread(() -> {
			try {
				FOption<Try<Integer>> r = stream.next();
				if ( r.isPresent() ) {
					result.set(r.get());
				}
				flagAfterReturn.set(Thread.currentThread().isInterrupted());
			}
			catch ( Throwable t ) {
				// SuppliableFStream.next() 가 RuntimeInterruptedException을 던지는 경로일 수도 있음.
				flagAfterReturn.set(Thread.currentThread().isInterrupted());
			}
			finally {
				done.countDown();
			}
		});
		consumer.start();

		// consumer가 awaitResult에 진입할 시간 확보.
		Thread.sleep(100);
		consumer.interrupt();

		Assertions.assertTrue(done.await(3, TimeUnit.SECONDS),
							"consumer가 인터럽트 후 즉시 깨어나야 함");
		Assertions.assertTrue(flagAfterReturn.get(), "인터럽트 플래그가 복원되어야 함");

		// result가 채워진 경로 (awaitResult catch) 라면 Try.failure(InterruptedException) 이어야.
		Try<Integer> trial = result.get();
		if ( trial != null ) {
			Assertions.assertTrue(trial.isFailed());
			Assertions.assertTrue(trial.getCause() instanceof InterruptedException,
								() -> "cause = " + trial.getCause());
		}

		stream.close();
	}

	// ---------- RejectedExecutionException → per-element failure ----------

	@Test
	public void rejectedExecution_yieldsFailureForEachAffectedItem() throws Exception {
		// 첫 작업의 mapper 안에서 executor를 shutdown.
		// CompletableFutureAsyncExecution.start() 가 startExecution() 의 RejectedExecutionException
		// 을 내부에서 catch → exec를 FAILED 상태로 전이시키므로, 후속 작업들은 element-wise로
		// Try.failure(RejectedExecutionException) 으로 노출된다 (스트림 전체 fatal이 아님).
		ExecutorService exec = track(Executors.newFixedThreadPool(1));
		AtomicInteger callIndex = new AtomicInteger();

		Function<Integer, Integer> mapper = i -> {
			int idx = callIndex.getAndIncrement();
			if ( idx == 0 ) {
				exec.shutdown();
			}
			return i * 10;
		};

		List<Try<Integer>> results = FStream.range(0, 5)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER()
													.setWorkerCount(1)
													.setExecutor(exec))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(5, results.size());

		// 첫 번째 작업은 (mapper 안에서 shutdown 호출 후) 정상 반환.
		Assertions.assertTrue(results.get(0).isSuccessful(), "idx 0 should succeed");
		Assertions.assertEquals(Integer.valueOf(0), results.get(0).get());

		// 이후 작업들은 executor 가 reject → 모두 Try.failure(RejectedExecutionException).
		for ( int i = 1; i < 5; ++i ) {
			final int idx = i;
			Assertions.assertTrue(results.get(i).isFailed(), () -> "idx " + idx + " should fail");
			Throwable root = results.get(i).getCause();
			while ( root != null && !(root instanceof RejectedExecutionException) ) {
				root = root.getCause();
			}
			Assertions.assertNotNull(root,
									() -> "idx " + idx + " cause chain에 RejectedExecutionException 포함되어야 함");
		}
	}

	// ---------- 워커 병렬 실행 ----------

	@Test
	public void workersRunInParallel() throws Exception {
		// 워커 4개로 4개 작업 (각 200ms sleep) → 직렬이면 800ms+, 병렬이면 ~200ms.
		final int n = 4;
		final int sleepMs = 200;

		Function<Integer, Integer> mapper = i -> {
			Unchecked.runOrRTE(() -> Thread.sleep(sleepMs));
			return i;
		};

		StopWatch watch = StopWatch.start();
		List<Try<Integer>> results = FStream.range(0, n)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(n))
				.map(Tuple::_2)
				.toList();
		watch.stop();

		Assertions.assertEquals(n, results.size());
		// 병렬이면 ~sleepMs (+ 약간의 오버헤드). 직렬이면 n * sleepMs.
		long elapsed = watch.getElapsedInMillis();
		Assertions.assertTrue(elapsed < (long)n * sleepMs / 2,
							() -> "워커가 병렬 실행되어야 함 (elapsed=" + elapsed + "ms, threshold=" + (n * sleepMs / 2) + ")");
	}

	// ---------- 워커 수 한도 ----------

	@Test
	public void concurrentMapperCount_doesNotExceedWorkerCount() throws Exception {
		// 워커 2개로 10개 작업을 처리할 때 동시 실행되는 mapper 수가 2를 초과하지 않아야.
		final int workers = 2;
		final int total = 10;
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger peak = new AtomicInteger();

		Function<Integer, Integer> mapper = i -> {
			int cur = inFlight.incrementAndGet();
			peak.accumulateAndGet(cur, Math::max);
			Unchecked.runOrRTE(() -> Thread.sleep(50));
			inFlight.decrementAndGet();
			return i;
		};

		List<Try<Integer>> results = FStream.range(0, total)
				.mapAsync(mapper, AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(workers))
				.map(Tuple::_2)
				.toList();

		Assertions.assertEquals(total, results.size());
		Assertions.assertTrue(peak.get() <= workers,
							() -> "동시 실행 mapper 수가 workerCount 이하여야 함 (peak=" + peak.get() + ")");
	}
}
