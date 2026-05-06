package utils.async;


import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;


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

		TestPeriodicLoop(Duration interval, boolean cumulative, int lastIteration) {
			super(interval, cumulative);

			m_lastIteration = lastIteration;
		}

		@Override
		protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
			if ( loopIndex >= m_lastIteration ) {
				return Optional.of(m_count);
			}
			++m_count;
			return Optional.empty();
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

		Assert.assertEquals(3, count);
		// 대충 300ms 정도 소요된다.
		Assert.assertTrue(elapsed > 250 && elapsed <= 400);
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
		Assert.assertTrue(aresult.isCancelled());
		Assert.assertEquals(3, loop.m_count);
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
				Assert.assertTrue(ret);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		long elapsed = System.currentTimeMillis() - started;

		// 200ms 후에 cancel 시키므로 중단되어야 함.
		Assert.assertTrue(aresult.isCancelled());
		// 2번 iteration 수행되어야 함.
		Assert.assertEquals(3, loop.m_count);
		Assert.assertTrue(elapsed < 300);
	}

	// ----- 종료 / 결과 -----

	@Test
	public void testEarlyTermination() throws Exception {
		// 첫 iteration에서 즉시 결과를 반환하는 경우
		PeriodicLoopExecution<String> loop = new PeriodicLoopExecution<String>(INTERVAL_100MS) {
			@Override
			protected Optional<String> performPeriodicAction(long loopIndex) throws Exception {
				return Optional.of("done@" + loopIndex);
			}
		};

		long started = System.currentTimeMillis();
		loop.start();
		String result = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals("done@0", result);
		// interval 만큼 대기하지 않고 곧바로 종료되어야 한다.
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 100);
	}

	@Test
	public void testNullReturnFailsWithNPE() throws Exception {
		// performPeriodicAction이 null을 반환하면 NullPointerException으로 FAILED 상태가 되어야 한다.
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				return null;
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof NullPointerException);
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

		Assert.assertTrue("state=" + result.getState(), result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// 대충 200ms 근처에서 종료되어야 한다.
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 180 && elapsed < 350);
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

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 180 && elapsed < 350);
	}

	@Test
	public void testDueInPastImmediateTimeout() throws Exception {
		// 이미 과거인 due를 설정하면 첫 iteration 직전에 즉시 timeout이 발생해야 한다.
		AtomicInteger callCount = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				callCount.incrementAndGet();
				return Optional.empty();
			}
		};
		loop.setDue(Instant.now().minusSeconds(1));

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(1, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// performPeriodicAction이 한 번도 호출되지 않아야 한다.
		Assert.assertEquals(0, callCount.get());
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

		Assert.assertTrue(result.isFailed());
		// timeout(10초)가 아니라 due(200ms)에 의해 종료되어야 한다.
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 500);
	}

	@Test
	public void testGetDueDerivedFromTimeoutAfterStart() throws Exception {
		// setTimeout만 호출하고 시작한 뒤 getDue()를 호출하면 파생값이 반환되어야 한다.
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
		loop.setTimeout(Duration.ofMillis(150));

		Assert.assertNull(loop.getDue());   // 시작 전에는 null

		Instant before = Instant.now();
		loop.start();
		loop.waitForFinished(2, TimeUnit.SECONDS);
		Instant after = Instant.now();

		Instant due = loop.getDue();
		Assert.assertNotNull(due);
		// 파생된 due는 시작 시각 + timeout 근처여야 한다.
		Assert.assertTrue(due.isAfter(before));
		Assert.assertTrue(due.isBefore(after.plusSeconds(1)));
	}

	// ----- cumulative interval -----

	@Test
	public void testCumulativeCatchUp() throws Exception {
		// cumulativeInterval=true: action이 interval보다 오래 걸려도 다음 iteration이 곧바로 시작된다.
		// interval=50ms, action=120ms, lastIteration=4
		// 누적 모드면 약 4 * 120ms = 480ms (action 시간이 지배적)
		AtomicInteger count = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS, true) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				int c = count.incrementAndGet();
				Thread.sleep(120);
				return c >= 4 ? Optional.of(c) : Optional.empty();
			}
		};

		long started = System.currentTimeMillis();
		loop.start();
		int result = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(4, result);
		// 누적 catch-up이라 약 4 * 120ms = 480ms 정도. 너그럽게 검사.
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 400 && elapsed < 700);
	}

	@Test
	public void testNonCumulativeTimingFromIterStart() throws Exception {
		// cumulativeInterval=false (기본값): 다음 iteration은 이번 iteration 시작 시각 + interval
		// action이 interval보다 짧으면 interval만큼 간격이 유지된다.
		// interval=100ms, action=20ms, 5번 iteration.
		// 마지막 iteration이 결과를 반환하면 마지막 sleep이 생략되므로
		// 예상 elapsed ~= 4 * 100ms + action ≈ 420ms.
		AtomicInteger count = new AtomicInteger(0);
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_100MS) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				int c = count.incrementAndGet();
				Thread.sleep(20);
				return c >= 5 ? Optional.of(c) : Optional.empty();
			}
		};

		long started = System.currentTimeMillis();
		loop.start();
		int result = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(5, result);
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 380 && elapsed < 700);
	}

	// ----- 예외 전파 -----

	@Test
	public void testActionThrowsExceptionFails() throws Exception {
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				if ( loopIndex == 1 ) {
					throw new IllegalStateException("boom");
				}
				return Optional.empty();
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof IllegalStateException);
		Assert.assertEquals("boom", result.getFailureCause().getMessage());
	}

	@Test
	public void testActionThrowsCancellationCancelsLoop() throws Exception {
		PeriodicLoopExecution<Integer> loop = new PeriodicLoopExecution<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				if ( loopIndex == 1 ) {
					throw new CancellationException("cancelled by action");
				}
				return Optional.empty();
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assert.assertTrue(result.isCancelled());
	}

	// ----- 생성자 / setter 검증 -----

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidIntervalNull() {
		new TestPeriodicLoop(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidIntervalZero() {
		new TestPeriodicLoop(Duration.ZERO, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidIntervalNegative() {
		new TestPeriodicLoop(Duration.ofMillis(-10), 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidTimeoutZero() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setTimeout(Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidTimeoutNegative() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setTimeout(Duration.ofMillis(-10));
	}

	@Test
	public void testNullTimeoutAllowed() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setTimeout(null);   // null은 허용 (제한 없음을 의미)
		Assert.assertNull(loop.getTimeout());
	}

	@Test
	public void testNullDueAllowed() {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 1);
		loop.setDue(null);
		Assert.assertNull(loop.getDue());
	}

	@Test(expected = IllegalStateException.class)
	public void testSetLoopIntervalAfterStart() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
		loop.start();
		try {
			loop.setLoopInterval(INTERVAL_100MS);
		}
		finally {
			loop.cancel(true);
			loop.waitForFinished();
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testSetTimeoutAfterStart() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
		loop.start();
		try {
			loop.setTimeout(Duration.ofSeconds(1));
		}
		finally {
			loop.cancel(true);
			loop.waitForFinished();
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testSetDueAfterStart() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_50MS, 100);
		loop.start();
		try {
			loop.setDue(Instant.now().plusSeconds(1));
		}
		finally {
			loop.cancel(true);
			loop.waitForFinished();
		}
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
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				return Optional.of(42);
			}
		};

		loop.start();
		Assert.assertEquals(42, (int)loop.waitForFinished().get());
		Assert.assertEquals(1, initCount.get());
		Assert.assertEquals(1, finalizeCount.get());
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
			protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
				throw new RuntimeException("fail");
			}
		};

		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished(2, TimeUnit.SECONDS);
		Assert.assertTrue(result.isFailed());
		// initializeLoop 성공 후의 종료 사유와 무관하게 finalizeLoop은 호출되어야 한다.
		Assert.assertEquals(1, finalizeCount.get());
	}

	@Test
	public void testLoopIndexStartsFromZeroAndIncrements() throws Exception {
		AtomicInteger expected = new AtomicInteger(0);
		PeriodicLoopExecution<Long> loop = new PeriodicLoopExecution<Long>(INTERVAL_50MS) {
			@Override
			protected Optional<Long> performPeriodicAction(long loopIndex) throws Exception {
				Assert.assertEquals(expected.getAndIncrement(), loopIndex);
				if ( loopIndex == 3 ) {
					return Optional.of(loopIndex);
				}
				return Optional.empty();
			}
		};

		loop.start();
		Assert.assertEquals(3L, (long)loop.waitForFinished().get());
		Assert.assertEquals(4, expected.get());
	}
}
