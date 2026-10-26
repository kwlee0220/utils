package utils.async;


import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopExecutionTest {
	private static final Duration INTERVAL_100MS = Duration.ofMillis(100);
	private static final Duration INTERVAL_50MS = Duration.ofMillis(50);

	static class TestPeriodicLoop extends PeriodicLoopExecution<Integer> {
		private final int m_lastIteration;
		private int m_count = 0;

		TestPeriodicLoop(Duration interval, int lastIteration) {
			super(interval);

			m_lastIteration = lastIteration;
		}

		TestPeriodicLoop(Duration interval, IntervalMode mode, int lastIteration) {
			super(interval, mode);

			m_lastIteration = lastIteration;
		}

		@Override
		protected FOption<Integer> performPeriodicAction(long loopIndex) {
			if ( loopIndex >= m_lastIteration ) {
				return FOption.of(m_count);
			}
			++m_count;
			return FOption.empty();
		}
	};

	@Test
	public void testBasic() throws Exception {
		// 100ms 주기로 3번 iteration을 수행하는 loop
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 3);

		long started = System.currentTimeMillis();
		loop.start();
		int count = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals(3, count);
		// 대충 300ms 정도 소요된다.
		Assertions.assertTrue(elapsed > 250 && elapsed <= 400);
	}

	@Test
	public void testCancel() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 5);
		loop.start();

		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(250);
				loop.cancel(true);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		Assertions.assertTrue(aresult.isCancelled());
		Assertions.assertEquals(3, loop.m_count);
	}

	@Test
	public void testCancelImmediately() throws Exception {
		// 100ms 주기로 5번 iteration을 수행하는 loop
		// 대충 500ms 정도 소요된다
		// 200ms 후에 cancel 시킨다.

		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 5);

		long started = System.currentTimeMillis();
		loop.start();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(230);
				// cancel을 성공해야 함.
				boolean ret = loop.cancel(true);
				Assertions.assertTrue(ret);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		long elapsed = System.currentTimeMillis() - started;

		// 200ms 후에 cancel 시키므로 중단되어야 함.
		Assertions.assertTrue(aresult.isCancelled());
		// 2번 iteration 수행되어야 함.
		Assertions.assertEquals(3, loop.m_count);
		Assertions.assertTrue(elapsed < 300);
	}

	// ----- 종료 / 결과 -----

	@Test
	public void testEarlyTermination() throws Exception {
		// 첫 iteration에서 즉시 결과를 반환하는 경우
		PeriodicLoopExecution<String> loop = new PeriodicLoopExecution<String>(INTERVAL_100MS) {
			@Override
			protected FOption<String> performPeriodicAction(long loopIndex) {
				return FOption.of("done@" + loopIndex);
			}
		};

		long started = System.currentTimeMillis();
		loop.start();
		String result = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals("done@0", result);
		// interval 만큼 대기하지 않고 곧바로 종료되어야 한다.
		Assertions.assertTrue(elapsed < 100, "elapsed=" + elapsed);
	}

	@Test
	public void testNullReturnFailsWithNPE() throws Exception {
		// performPeriodicAction이 null을 반환하면 NullPointerException으로 FAILED 상태가 되어야 한다.
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				return null;
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof NullPointerException);
	}

	// ----- timeout / due -----

	@Test
	public void testTimeoutCausesFailure() throws Exception {
		// 200ms timeout을 설정하면 TimeoutException으로 FAILED 상태가 된다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 100);
		loop.setTimeout(Duration.ofMillis(200));

		long started = System.currentTimeMillis();
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertTrue(result.isFailed(), "state=" + result.getState());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assertions.assertEquals("timeout=PT0.2S", result.getFailureCause().getMessage());
		// 대충 200ms 근처에서 종료되어야 한다.
		Assertions.assertTrue(elapsed >= 180 && elapsed < 350, "elapsed=" + elapsed);
	}

	@Test
	public void testDueCausesFailure() throws Exception {
		// 명시적인 due 시각을 설정한다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 100);
		loop.setDue(Instant.now().plusMillis(200));

		long started = System.currentTimeMillis();
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assertions.assertTrue(elapsed >= 180 && elapsed < 350, "elapsed=" + elapsed);
	}

	@Test
	public void testDueInPastImmediateTimeout() throws Exception {
		// 이미 과거인 due를 설정하면 첫 iteration 직전에 즉시 timeout이 발생해야 한다.
		AtomicInteger callCount = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				callCount.incrementAndGet();
				return FOption.empty();
			}
		};
		loop.setDue(Instant.now().minusSeconds(1));

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(1, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// performPeriodicAction이 한 번도 호출되지 않아야 한다.
		Assertions.assertEquals(0, callCount.get());
	}

	@Test
	public void testDueOverridesTimeout() throws Exception {
		// setDue가 먼저 설정되면 setTimeout은 무시된다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 100);
		loop.setDue(Instant.now().plusMillis(200));
		loop.setTimeout(Duration.ofSeconds(10));   // 무시되어야 함

		long started = System.currentTimeMillis();
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertTrue(result.isFailed());
		// timeout(10초)가 아니라 due(200ms)에 의해 종료되어야 한다.
		Assertions.assertTrue(elapsed < 500, "elapsed=" + elapsed);
		// 예외 메시지도 실제로 적용된 due를 가리켜야 한다.
		Throwable cause = result.getFailureCause();
		Assertions.assertInstanceOf(TimeoutException.class, cause);
		Assertions.assertTrue(cause.getMessage().startsWith("due="), cause.getMessage());
	}

	@Test
	public void testGetDueDerivedFromTimeoutAfterStart() throws Exception {
		// setTimeout만 호출하고 시작한 뒤 getDue()를 호출하면 파생값이 반환되어야 한다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
		loop.setTimeout(Duration.ofMillis(150));

		Assertions.assertNull(loop.getDue());   // 시작 전에는 null

		Instant before = Instant.now();
		loop.start();
		loop.waitForFinished(2, TimeUnit.SECONDS);
		Instant after = Instant.now();

		Instant due = loop.getDue();
		Assertions.assertNotNull(due);
		// 파생된 due는 시작 시각 + timeout 근처여야 한다.
		Assertions.assertTrue(due.isAfter(before));
		Assertions.assertTrue(due.isBefore(after.plusSeconds(1)));
	}

	// ----- cumulative interval -----

	@Test
	public void testCumulativeCatchUp() throws Exception {
		// cumulativeInterval=true: action이 interval보다 오래 걸려도 다음 iteration이 곧바로 시작된다.
		// interval=50ms, action=120ms, lastIteration=4
		// 누적 모드면 약 4 * 120ms = 480ms (action 시간이 지배적)
		AtomicInteger count = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS, IntervalMode.FIXED_RATE) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) throws InterruptedException {
				int c = count.incrementAndGet();
				Thread.sleep(120);
				return c >= 4 ? FOption.of(c) : FOption.empty();
			}
		};

		long started = System.currentTimeMillis();
		loop.start();
		int result = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals(4, result);
		// 누적 catch-up이라 약 4 * 120ms = 480ms 정도. 너그럽게 검사.
		Assertions.assertTrue(elapsed >= 400 && elapsed < 700, "elapsed=" + elapsed);
	}

	@Test
	public void testNonCumulativeDelayFromIterEnd() throws Exception {
		// cumulativeInterval=false (기본값): 다음 iteration은 이번 iteration 종료 시각 + interval (fixed-delay)
		// interval=100ms, action=60ms, 5번 iteration.
		// 마지막 iteration이 결과를 반환하면 마지막 대기가 생략되므로
		// 예상 elapsed ~= 5 * 60ms + 4 * 100ms ≈ 700ms. (시작 시각 기준이었다면 ≈ 460ms)
		long[] gaps = measureNonCumulativeGaps(INTERVAL_100MS, 60, 5);
		for ( long gap: gaps ) {
			Assertions.assertTrue(gap >= 90, "gap after iteration end=" + gap);
		}
	}

	@Test
	public void testNonCumulativeDelayKeptWhenActionOverrunsInterval() throws Exception {
		// action(150ms)이 interval(100ms)보다 오래 걸려도 다음 iteration 전에 interval만큼 대기해야 한다.
		// (시작 시각 기준이었다면 대기 없이 곧바로 다음 iteration이 시작된다.)
		long[] gaps = measureNonCumulativeGaps(INTERVAL_100MS, 150, 3);
		for ( long gap: gaps ) {
			Assertions.assertTrue(gap >= 90, "gap after iteration end=" + gap);
		}
	}

	/**
	 * non-cumulative loop를 {@code iterations}번 수행하고, 각 iteration 종료부터 다음 iteration 시작까지의
	 * 간격(ms)을 반환한다.
	 */
	private static long[] measureNonCumulativeGaps(Duration interval, long actionMillis, int iterations)
		throws Exception {
		long[] starts = new long[iterations];
		long[] ends = new long[iterations];
		AtomicInteger count = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(interval) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) throws InterruptedException {
				starts[(int)loopIndex] = System.nanoTime();
				int c = count.incrementAndGet();
				Thread.sleep(actionMillis);
				ends[(int)loopIndex] = System.nanoTime();
				return c >= iterations ? FOption.of(c) : FOption.empty();
			}
		};

		loop.start();
		Assertions.assertEquals(iterations, (int)loop.waitForFinished(5, TimeUnit.SECONDS).get());

		long[] gaps = new long[iterations-1];
		for ( int i = 0; i < gaps.length; ++i ) {
			gaps[i] = TimeUnit.NANOSECONDS.toMillis(starts[i+1] - ends[i]);
		}
		return gaps;
	}

	// ----- 예외 전파 -----

	@Test
	public void testActionThrowsExceptionFails() throws Exception {
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				if ( loopIndex == 1 ) {
					throw new IllegalStateException("boom");
				}
				return FOption.empty();
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof IllegalStateException);
		Assertions.assertEquals("boom", result.getFailureCause().getMessage());
	}

	@Test
	public void testActionThrowsCancellationCancelsLoop() throws Exception {
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) throws CancellationException {
				if ( loopIndex == 1 ) {
					throw new CancellationException("cancelled by action");
				}
				return FOption.empty();
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isCancelled());
	}

	// ----- 생성자 / setter 검증 -----

	@Test
	public void testInvalidIntervalNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TestPeriodicLoop(null, 1);
			});
	}

	@Test
	public void testInvalidIntervalZero() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TestPeriodicLoop(Duration.ZERO, 1);
			});
	}

	@Test
	public void testInvalidIntervalNegative() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new TestPeriodicLoop(Duration.ofMillis(-10), 1);
			});
	}

	@Test
	public void testInvalidTimeoutZero() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
			loop.setTimeout(Duration.ZERO);
			});
	}

	@Test
	public void testInvalidTimeoutNegative() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
			loop.setTimeout(Duration.ofMillis(-10));
			});
	}

	@Test
	public void testNullTimeoutAllowed() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setTimeout(null);   // null은 허용 (제한 없음을 의미)
		Assertions.assertNull(loop.getTimeout());
	}

	@Test
	public void testNullDueAllowed() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setDue(null);
		Assertions.assertNull(loop.getDue());
	}

	@Test
	public void testSetTimeoutAfterStart() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
			loop.start();
			try {
				loop.setTimeout(Duration.ofSeconds(1));
			}
			finally {
				loop.cancel(true);
				loop.waitForFinished();
			}
			});
	}

	@Test
	public void testSetDueAfterStart() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
			loop.start();
			try {
				loop.setDue(Instant.now().plusSeconds(1));
			}
			finally {
				loop.cancel(true);
				loop.waitForFinished();
			}
			});
	}

	// ----- initialize / finalize 훅 -----

	@Test
	public void testInitializeAndFinalizeHooksOnSuccess() throws Exception {
		AtomicInteger initCount = new AtomicInteger(0);
		AtomicInteger finalizeCount = new AtomicInteger(0);

		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected void initializeLoop() throws Exception {
				super.initializeLoop();
				initCount.incrementAndGet();
			}
			@Override
			protected void finalizeLoop() {
				super.finalizeLoop();
				finalizeCount.incrementAndGet();
			}
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				return FOption.of(42);
			}
		};

		loop.start();
		Assertions.assertEquals(42, (int)loop.waitForFinished().get());
		Assertions.assertEquals(1, initCount.get());
		Assertions.assertEquals(1, finalizeCount.get());
	}

	@Test
	public void testFinalizeCalledOnFailure() throws Exception {
		AtomicInteger finalizeCount = new AtomicInteger(0);

		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected void finalizeLoop() {
				super.finalizeLoop();
				finalizeCount.incrementAndGet();
			}
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				throw new RuntimeException("fail");
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed());
		// initializeLoop 성공 후의 종료 사유와 무관하게 finalizeLoop은 호출되어야 한다.
		Assertions.assertEquals(1, finalizeCount.get());
	}

	@Test
	public void testExternalFailureWakesIntervalWait() throws Exception {
		// 주기 대기 중 외부에서 FAILED로 확정되면 interval이 끝나기를 기다리지 않고 곧바로 loop를 끝내야 한다.
		AtomicInteger actionCount = new AtomicInteger(0);
		AtomicInteger finalizeCount = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(Duration.ofSeconds(10)) {
			@Override
			protected void finalizeLoop() {
				super.finalizeLoop();
				finalizeCount.incrementAndGet();
			}
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				actionCount.incrementAndGet();
				return FOption.empty();
			}
		};

		loop.start();
		loop.waitForStarted();
		Thread.sleep(100);		// 첫 iteration 이후 주기 대기에 들어가도록 한다.

		IllegalStateException external = new IllegalStateException("external");
		Assertions.assertTrue(loop.notifyFailed(external));

		// FAILED 전이는 즉시 확정되므로 finalizeLoop 호출 여부를 짧은 시간 안에 확인한다.
		long started = System.currentTimeMillis();
		while ( finalizeCount.get() == 0 && System.currentTimeMillis() - started < 2_000 ) {
			Thread.sleep(10);
		}
		Assertions.assertEquals(1, finalizeCount.get(), "loop did not finish while waiting for next iteration");
		Assertions.assertEquals(1, actionCount.get());
		Assertions.assertSame(external, loop.poll().getFailureCause());
	}

	@Test
	public void testExternalFailureDuringActionPrevailsOverTimeout() throws Exception {
		// 작업 수행 중 외부에서 FAILED로 확정되고 작업이 제한 시간을 넘긴 뒤 empty를 반환하는 경우,
		// 새 TimeoutException이 아니라 확정된 실패 원인이 run()에 전달되어야 한다.
		IllegalStateException external = new IllegalStateException("original external failure");
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) throws InterruptedException {
				notifyFailed(external);
				Thread.sleep(150);		// 제한 시간(50ms)을 넘긴다.
				return FOption.empty();
			}
		};
		loop.setTimeout(Duration.ofMillis(50));

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, loop::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertSame(external, loop.poll().getFailureCause());
	}

	@Test
	public void testHugeTimeoutTreatedAsUnlimited() throws Exception {
		// ChronoUnit.FOREVER처럼 표현 범위를 넘는 timeout도 초기화를 실패시키지 않아야 한다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 2);
		loop.setTimeout(ChronoUnit.FOREVER.getDuration());

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isCompleted(), result.toString());
		Assertions.assertEquals(2, result.get());
		Assertions.assertEquals(Instant.MAX, loop.getDue());
	}

	@Test
	public void testGetRemainingTime() throws Exception {
		// 시작 전이나 제한 시간이 없으면 empty, 수행 중에는 남은 시간(0 < t <= timeout)을 반환해야 한다.
		java.util.List<FOption<Duration>> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> performPeriodicAction(long loopIndex) {
				seen.add(getRemainingTime());
				return loopIndex >= 1 ? FOption.of(1) : FOption.empty();
			}
		};
		loop.setTimeout(Duration.ofSeconds(10));
		Assertions.assertTrue(loop.getRemainingTime().isAbsent());

		loop.start();
		Assertions.assertTrue(loop.waitForFinished(2, TimeUnit.SECONDS).isCompleted());
		Assertions.assertEquals(2, seen.size());
		for ( FOption<Duration> remaining: seen ) {
			Assertions.assertTrue(remaining.isPresent());
			Assertions.assertTrue(remaining.get().compareTo(Duration.ZERO) > 0
									&& remaining.get().compareTo(Duration.ofSeconds(10)) <= 0, remaining.get().toString());
		}
		// 두 번째 iteration의 남은 시간은 첫 번째보다 작아야 한다(적어도 interval만큼 경과).
		Assertions.assertTrue(seen.get(1).get().compareTo(seen.get(0).get()) < 0);

		TestPeriodicLoop unlimited = new TestPeriodicLoop(INTERVAL_50MS, 1);
		unlimited.start();
		unlimited.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(unlimited.getRemainingTime().isAbsent());
	}

	@Test
	public void testLoopIndexStartsFromZeroAndIncrements() throws Exception {
		AtomicInteger expected = new AtomicInteger(0);
		PeriodicLoopExecution<Long> loop = new PeriodicLoopExecution<Long>(INTERVAL_50MS) {
			@Override
			protected FOption<Long> performPeriodicAction(long loopIndex) {
				Assertions.assertEquals(expected.getAndIncrement(), loopIndex);
				if ( loopIndex == 3 ) {
					return FOption.of(loopIndex);
				}
				return FOption.empty();
			}
		};

		loop.start();
		Assertions.assertEquals(3L, (long)loop.waitForFinished().get());
		Assertions.assertEquals(4, expected.get());
	}
}
