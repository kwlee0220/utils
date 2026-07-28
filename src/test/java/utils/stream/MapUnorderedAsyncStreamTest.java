package utils.stream;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.StopWatch;
import utils.Tuple;
import utils.func.Try;
import utils.func.Unchecked;

/**
 * {@link UnorderedMapAsyncStream} 전용 테스트.
 * <p>
 * 결과 출력 순서가 입력 순서가 아닌 완료 순서임을 확인하고, 빈 입력/작은 입력 종료,
 * mapper 예외 캡처, close 시 진행 중 작업 cancel 전파, 워커 병렬 실행 및 동시성 한도,
 * RejectedExecutionException 처리를 다룬다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MapUnorderedAsyncStreamTest {
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

	private static AsyncExecutionOptions unordered(int workerCount) {
		return AsyncExecutionOptions.KEEP_ORDER(false).setWorkerCount(workerCount);
	}

	// ---------- 완료 순서 출력 ----------

	@Test
	public void resultsEmittedInCompletionOrder_notInputOrder() throws Exception {
		// 입력은 [0,1,2,3,4]. 마지막 입력(4)만 즉시 완료되고, 나머지는 latch로 막아둔다.
		// → 첫 방출은 반드시 4가 되어야 하며, 이후 latch를 풀어 나머지를 완료시킨다.
		// sleep 시간 차이에 의존하지 않으므로 시스템 부하와 무관하게 결정적으로 동작한다.
		final int n = 5;
		CountDownLatch release = new CountDownLatch(1);

		Function<Integer, Integer> mapper = i -> {
			if ( i < n - 1 ) {
				Unchecked.runOrRTE(() -> release.await(5, TimeUnit.SECONDS));
			}
			return i;
		};

		try ( FStream<Tuple<Integer, Try<Integer>>> stream = FStream.range(0, n)
																	.mapAsync(mapper, unordered(n)) ) {
			// 유일하게 완료 가능한 4가 입력 순서(0)를 제치고 가장 먼저 방출되어야 한다.
			Tuple<Integer, Try<Integer>> first = stream.next().get();
			Assertions.assertEquals(Integer.valueOf(n - 1), first._1());
			Assertions.assertTrue(first._2().isSuccessful());

			// 나머지 작업들을 풀어주고 모두 방출되는지 확인한다 (순서 무관).
			release.countDown();

			Set<Integer> remains = new HashSet<>();
			for ( Tuple<Integer, Try<Integer>> r : stream ) {
				Assertions.assertTrue(r._2().isSuccessful(), () -> "Try should succeed: " + r);
				Assertions.assertEquals(r._1(), r._2().get());
				remains.add(r._1());
			}
			Assertions.assertEquals(new HashSet<>(FStream.range(0, n - 1).toList()), remains);
		}
	}

	// ---------- 종료 조건 ----------

	@Test
	public void emptyInput_terminatesCleanly() throws Exception {
		List<Tuple<Integer, Try<Integer>>> results = FStream.<Integer>of()
				.mapAsync(i -> i * 2, unordered(4))
				.toList();

		Assertions.assertTrue(results.isEmpty());
	}

	@Test
	public void inputShorterThanWorkerCount_terminatesCleanly() throws Exception {
		// workerCount=4, input=2. 남는 워커가 endOfSupply 도달까지 카운트를 제대로 줄여야.
		List<Tuple<Integer, Try<Integer>>> results = FStream.of(1, 2)
				.mapAsync(i -> i * 10, unordered(4))
				.toList();

		Assertions.assertEquals(2, results.size());
		Set<Integer> values = new HashSet<>();
		for ( Tuple<Integer, Try<Integer>> r : results ) {
			values.add(r._2().get());
		}
		Assertions.assertEquals(new HashSet<>(FStream.of(10, 20).toList()), values);
	}

	@Test
	public void inputEqualsWorkerCount_terminatesCleanly() throws Exception {
		List<Tuple<Integer, Try<Integer>>> results = FStream.of(1, 2, 3)
				.mapAsync(i -> i * 10, unordered(3))
				.toList();

		Assertions.assertEquals(3, results.size());
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

		List<Tuple<Integer, Try<Integer>>> results = FStream.range(0, 5)
				.mapAsync(mapper, unordered(2))
				.toList();

		Assertions.assertEquals(5, results.size());

		Tuple<Integer, Try<Integer>> failed = null;
		int successCount = 0;
		for ( Tuple<Integer, Try<Integer>> r : results ) {
			if ( r._2().isFailed() ) {
				failed = r;
			}
			else {
				++successCount;
			}
		}
		Assertions.assertNotNull(failed, "exactly one failure expected");
		Assertions.assertEquals(Integer.valueOf(2), failed._1());
		Assertions.assertEquals("boom at 2", failed._2().getCause().getMessage());
		Assertions.assertEquals(4, successCount);
	}

	// ---------- close 시 cancel 전파 ----------

	@Test
	public void closeMidStream_cancelsActiveJobs_andDoesNotHang() throws Exception {
		// mapper가 길게 sleep하는 동안 close → active job들에 cancel(true) 전파되어야.
		Function<Integer, Integer> mapper = i -> {
			Unchecked.runOrRTE(() -> Thread.sleep(5000));
			return i;
		};

		FStream<Tuple<Integer, Try<Integer>>> stream = FStream.range(0, 100)
				.mapAsync(mapper, unordered(3));

		// 워커들이 mapper sleep에 진입할 시간 확보.
		Thread.sleep(100);

		// close는 5초 mapper sleep을 끝까지 기다리지 않고 빠르게 반환되어야 한다.
		StopWatch watch = StopWatch.start();
		stream.close();
		watch.stop();
		Assertions.assertTrue(watch.getElapsedInMillis() < 1000,
							() -> "close가 mapper sleep을 끝까지 기다리지 않아야 함 (elapsed=" + watch.getElapsedInMillis() + "ms)");

		// close 후 next는 hang 없이 빈 결과 또는 IllegalStateException.
		StopWatch nextWatch = StopWatch.start();
		try {
			stream.next();
		}
		catch ( IllegalStateException expected ) {
			// 허용.
		}
		nextWatch.stop();
		Assertions.assertTrue(nextWatch.getElapsedInMillis() < 1000,
							() -> "close 후 next가 hang 되지 않아야 함 (elapsed=" + nextWatch.getElapsedInMillis() + "ms)");
	}

	// ---------- RejectedExecutionException ----------

	@Test
	public void rejectedExecution_yieldsFailureForEachAffectedItem() throws Exception {
		// 첫 작업의 mapper 안에서 executor를 shutdown.
		// CompletableFutureAsyncExecution.start() 가 RejectedExecutionException을 내부에서
		// catch → exec를 FAILED 상태로 전이시키므로, 후속 작업들은 element-wise로
		// Try.failure(RejectedExecutionException) 으로 노출된다.
		ExecutorService exec = track(Executors.newFixedThreadPool(1));
		AtomicInteger callIndex = new AtomicInteger();

		Function<Integer, Integer> mapper = i -> {
			int idx = callIndex.getAndIncrement();
			if ( idx == 0 ) {
				exec.shutdown();
			}
			return i * 10;
		};

		List<Tuple<Integer, Try<Integer>>> results = FStream.range(0, 5)
				.mapAsync(mapper, unordered(1).setExecutor(exec))
				.toList();

		Assertions.assertEquals(5, results.size());

		int successCount = 0;
		int failCount = 0;
		for ( Tuple<Integer, Try<Integer>> r : results ) {
			if ( r._2().isSuccessful() ) {
				++successCount;
			}
			else {
				++failCount;
				Throwable root = r._2().getCause();
				while ( root != null && !(root instanceof RejectedExecutionException) ) {
					root = root.getCause();
				}
				Assertions.assertNotNull(root, "cause chain에 RejectedExecutionException 포함되어야 함");
			}
		}
		// 첫 작업은 정상 (mapper 안에서 shutdown 호출 후 정상 반환).
		Assertions.assertEquals(1, successCount);
		Assertions.assertEquals(4, failCount);
	}

	// ---------- 워커 병렬 실행 ----------

	@Test
	public void workersRunInParallel() throws Exception {
		// 워커 n개가 모두 동시에 mapper 안에 들어와야만 barrier가 풀린다.
		// 직렬 실행이면 barrier timeout으로 mapper가 실패하므로, 경과 시간 측정 없이
		// 시스템 부하와 무관하게 병렬성을 검증할 수 있다.
		final int n = 4;
		CyclicBarrier barrier = new CyclicBarrier(n);

		Function<Integer, Integer> mapper = i -> {
			Unchecked.runOrRTE(() -> barrier.await(5, TimeUnit.SECONDS));
			return i;
		};

		List<Tuple<Integer, Try<Integer>>> results = FStream.range(0, n)
				.mapAsync(mapper, unordered(n))
				.toList();

		Assertions.assertEquals(n, results.size());
		for ( Tuple<Integer, Try<Integer>> r : results ) {
			Assertions.assertTrue(r._2().isSuccessful(),
								() -> "워커 " + n + "개가 동시에 실행되어 barrier에 도달해야 함: " + r);
		}
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

		List<Tuple<Integer, Try<Integer>>> results = FStream.range(0, total)
				.mapAsync(mapper, unordered(workers))
				.toList();

		Assertions.assertEquals(total, results.size());
		Assertions.assertTrue(peak.get() <= workers,
							() -> "동시 실행 mapper 수가 workerCount 이하여야 함 (peak=" + peak.get() + ")");
	}
}
