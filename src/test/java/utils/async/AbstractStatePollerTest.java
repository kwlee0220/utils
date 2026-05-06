package utils.async;


import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractStatePollerTest {
	private static final Duration INTERVAL_50MS = Duration.ofMillis(50);
	private static final Duration INTERVAL_100MS = Duration.ofMillis(100);

	/**
	 * 지정된 iteration 수만큼 Optional.empty()를 반환한 뒤 목표 상태(value)를 반환하는 폴러.
	 */
	static class CountingPoller extends AbstractStatePoller<Integer> {
		private final int m_targetIteration;
		private final int m_resultValue;
		final AtomicInteger m_initCount = new AtomicInteger(0);
		final AtomicInteger m_pollCount = new AtomicInteger(0);
		final AtomicInteger m_finalizeCount = new AtomicInteger(0);
		final AtomicReference<Integer> m_finalizedState = new AtomicReference<>();

		CountingPoller(Duration interval, int targetIteration, int resultValue) {
			super(interval);
			m_targetIteration = targetIteration;
			m_resultValue = resultValue;
		}

		CountingPoller(Duration interval, boolean cumulative, int targetIteration, int resultValue) {
			super(interval, cumulative);
			m_targetIteration = targetIteration;
			m_resultValue = resultValue;
		}

		@Override
		protected void initializePoller() throws Exception {
			m_initCount.incrementAndGet();
		}

		@Override
		protected Optional<Integer> pollState() throws Exception {
			int n = m_pollCount.incrementAndGet();
			if ( n >= m_targetIteration ) {
				return Optional.of(m_resultValue);
			}
			return Optional.empty();
		}

		@Override
		protected void finalizePoller(@Nullable Integer state) {
			m_finalizeCount.incrementAndGet();
			m_finalizedState.set(state);
		}
	}

	// ----- 기본 동작 -----

	@Test
	public void testReachesTargetState() throws Exception {
		// 3번째 polling에서 목표 상태에 도달한다.
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 3, 42);

		long started = System.currentTimeMillis();
		poller.start();
		int result = poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(42, result);
		Assert.assertEquals(3, poller.m_pollCount.get());
		// 첫 polling 즉시 + 2번 더 = 약 2 * 50ms ~ 100ms (non-cumulative).
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 250);
	}

	@Test
	public void testFirstPollReachesTargetImmediately() throws Exception {
		// 첫 polling에서 즉시 목표 상태에 도달한다.
		CountingPoller poller = new CountingPoller(INTERVAL_100MS, 1, 7);

		long started = System.currentTimeMillis();
		poller.start();
		int result = poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(7, result);
		Assert.assertEquals(1, poller.m_pollCount.get());
		// interval 만큼 sleep하지 않고 곧바로 종료되어야 한다.
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 100);
	}

	// ----- 라이프사이클 훅 -----

	@Test
	public void testLifecycleHooksOnSuccess() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 2, 99);

		poller.start();
		poller.waitForFinished().get();

		Assert.assertEquals(1, poller.m_initCount.get());
		Assert.assertEquals(2, poller.m_pollCount.get());
		Assert.assertEquals(1, poller.m_finalizeCount.get());
		// 정상 종료 시 finalizePoller에 결과값이 전달되어야 한다.
		Assert.assertEquals(Integer.valueOf(99), poller.m_finalizedState.get());
	}

	@Test
	public void testFinalizePollerStateNullOnCancel() throws Exception {
		// 절대 도달하지 않는 목표를 설정한 뒤 취소하면, finalizePoller(null)이 호출되어야 한다.
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, Integer.MAX_VALUE, 0);

		poller.start();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(150);
				poller.cancel(true);
			}
			catch ( InterruptedException ignored ) { }
		});
		AsyncResult<Integer> result = poller.waitForFinished();

		Assert.assertTrue(result.isCancelled());
		Assert.assertEquals(1, poller.m_finalizeCount.get());
		Assert.assertNull(poller.m_finalizedState.get());
	}

	@Test
	public void testFinalizePollerStateNullOnTimeout() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, Integer.MAX_VALUE, 0);
		poller.setTimeout(Duration.ofMillis(150));

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assert.assertEquals(1, poller.m_finalizeCount.get());
		Assert.assertNull(poller.m_finalizedState.get());
	}

	@Test
	public void testFinalizePollerStateNullOnException() throws Exception {
		AtomicReference<Integer> finalized = new AtomicReference<>();
		AtomicInteger finalizeCount = new AtomicInteger(0);

		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				throw new IllegalStateException("boom");
			}
			@Override
			protected void finalizePoller(@Nullable Integer state) {
				finalizeCount.incrementAndGet();
				finalized.set(state);
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertEquals(1, finalizeCount.get());
		Assert.assertNull(finalized.get());
	}

	@Test
	public void testFinalizePollerNotCalledWhenInitializePollerFails() throws Exception {
		AtomicInteger initCount = new AtomicInteger(0);
		AtomicInteger pollCount = new AtomicInteger(0);
		AtomicInteger finalizeCount = new AtomicInteger(0);

		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected void initializePoller() throws Exception {
				initCount.incrementAndGet();
				throw new RuntimeException("init failed");
			}
			@Override
			protected Optional<Integer> pollState() throws Exception {
				pollCount.incrementAndGet();
				return Optional.empty();
			}
			@Override
			protected void finalizePoller(@Nullable Integer state) {
				finalizeCount.incrementAndGet();
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertEquals(1, initCount.get());
		// pollState도 finalizePoller도 호출되지 않아야 한다.
		Assert.assertEquals(0, pollCount.get());
		Assert.assertEquals(0, finalizeCount.get());
	}

	@Test
	public void testInitializePollerCanReadParentDue() throws Exception {
		// initializePoller 호출 시점에 부모의 setTimeout으로부터 파생된 m_due가 설정되어 있어야 한다.
		AtomicReference<Instant> dueInsideInit = new AtomicReference<>();

		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected void initializePoller() throws Exception {
				dueInsideInit.set(getDue());
			}
			@Override
			protected Optional<Integer> pollState() throws Exception {
				return Optional.of(1);
			}
		};
		poller.setTimeout(Duration.ofSeconds(5));

		poller.start();
		poller.waitForFinished().get();

		// initializePoller에서 본 getDue()가 non-null이어야 한다 (super.initializeLoop이 먼저 실행됐음).
		Assert.assertNotNull(dueInsideInit.get());
		Assert.assertTrue(dueInsideInit.get().isAfter(Instant.now().minusSeconds(10)));
	}

	// ----- 예외 / 취소 / null 반환 -----

	@Test
	public void testNullReturnFromPollStateFails() throws Exception {
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				return null;
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof NullPointerException);
	}

	@Test
	public void testPollStateThrowsCancellationCancelsPoller() throws Exception {
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				throw new CancellationException("stop");
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isCancelled());
	}

	@Test
	public void testPollStateThrowsExceptionFails() throws Exception {
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				throw new IllegalArgumentException("bad");
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof IllegalArgumentException);
		Assert.assertEquals("bad", result.getFailureCause().getMessage());
	}

	// ----- timeout / due -----

	@Test
	public void testTimeoutOnLongPolling() throws Exception {
		// 100번째 iteration까지 가도록 설정 후 timeout을 짧게 잡으면 timeout으로 실패.
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 100, 0);
		poller.setTimeout(Duration.ofMillis(180));

		long started = System.currentTimeMillis();
		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 150 && elapsed < 400);
	}

	@Test
	public void testDueInPastImmediateTimeout() throws Exception {
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				pollCount.incrementAndGet();
				return Optional.empty();
			}
		};
		poller.setDue(Instant.now().minusSeconds(1));

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(1, TimeUnit.SECONDS);

		Assert.assertTrue(result.isFailed());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// pollState가 한 번도 호출되지 않아야 한다 (부모 클래스의 iteration 진입 시점 검사로 인해).
		Assert.assertEquals(0, pollCount.get());
	}

	// ----- cancel -----

	@Test
	public void testCancelDuringPolling() throws Exception {
		AtomicBoolean polledAfterCancel = new AtomicBoolean(false);
		AtomicInteger pollCount = new AtomicInteger(0);
		AtomicBoolean cancelled = new AtomicBoolean(false);

		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				if ( cancelled.get() ) {
					polledAfterCancel.set(true);
				}
				pollCount.incrementAndGet();
				return Optional.empty();
			}
		};

		poller.start();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(120);
				cancelled.set(true);
				poller.cancel(true);
			}
			catch ( InterruptedException ignored ) { }
		});
		AsyncResult<Integer> result = poller.waitForFinished();

		Assert.assertTrue(result.isCancelled());
		Assert.assertTrue("적어도 한 번은 polling되었어야 한다", pollCount.get() > 0);
	}

	// ----- cumulative 모드 -----

	@Test
	public void testCumulativeModeCatchUp() throws Exception {
		// pollState가 interval보다 오래 걸리면 누적 모드에서는 catch-up.
		// interval=50ms, action=80ms, target=4 → 약 4 * 80ms = 320ms.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS, true) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				int n = pollCount.incrementAndGet();
				Thread.sleep(80);
				return n >= 4 ? Optional.of(n) : Optional.empty();
			}
		};

		long started = System.currentTimeMillis();
		poller.start();
		int result = poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(4, result);
		// catch-up이라 interval 대기가 거의 없음. 너그럽게 검사.
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 280 && elapsed < 600);
	}

	@Test
	public void testNonCumulativeIsDefaultOneArgConstructor() throws Exception {
		// 1-arg 생성자는 부모의 기본값(false)을 따른다.
		// interval=50ms, action=10ms, target=4: non-cumulative이면 약 3 * 50 + action ≈ 160ms.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractStatePoller<Integer> poller = new AbstractStatePoller<Integer>(INTERVAL_50MS) {
			@Override
			protected Optional<Integer> pollState() throws Exception {
				int n = pollCount.incrementAndGet();
				Thread.sleep(10);
				return n >= 4 ? Optional.of(n) : Optional.empty();
			}
		};

		long started = System.currentTimeMillis();
		poller.start();
		poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		// non-cumulative이라 마지막 sleep 이후 결과를 받아 종료. ~150-180ms 예상.
		Assert.assertTrue("elapsed=" + elapsed, elapsed >= 130 && elapsed < 350);
	}

	// ----- 인덱스 / 호출 횟수 -----

	@Test
	public void testPollStateInvokedRepeatedlyUntilTargetReached() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 5, 123);

		poller.start();
		Assert.assertEquals(123, (int)poller.waitForFinished().get());
		Assert.assertEquals(5, poller.m_pollCount.get());
	}

	// ----- setter 호출 후 시작 시 부모 검증 동작 -----

	@Test(expected = IllegalStateException.class)
	public void testSetTimeoutAfterStart() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 100, 0);
		poller.start();
		try {
			poller.setTimeout(Duration.ofSeconds(1));
		}
		finally {
			poller.cancel(true);
			poller.waitForFinished();
		}
	}
}
