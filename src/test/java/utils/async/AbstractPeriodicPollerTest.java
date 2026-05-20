package utils.async;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractPeriodicPollerTest {
	private static final Duration INTERVAL_50MS = Duration.ofMillis(50);
	private static final Duration INTERVAL_100MS = Duration.ofMillis(100);

	/**
	 * 지정된 iteration 수만큼 FOption.empty()를 반환한 뒤 목표 상태(value)를 반환하는 폴러.
	 */
	static class CountingPoller extends AbstractPeriodicPoller<Integer> {
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
		protected FOption<Integer> tryPoll() {
			int n = m_pollCount.incrementAndGet();
			if ( n >= m_targetIteration ) {
				return FOption.of(m_resultValue);
			}
			return FOption.empty();
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

		Assertions.assertEquals(42, result);
		Assertions.assertEquals(3, poller.m_pollCount.get());
		// 첫 polling 즉시 + 2번 더 = 약 2 * 50ms ~ 100ms.
		Assertions.assertTrue(elapsed < 250, "elapsed=" + elapsed);
	}

	@Test
	public void testFirstPollReachesTargetImmediately() throws Exception {
		// 첫 polling에서 즉시 목표 상태에 도달한다.
		CountingPoller poller = new CountingPoller(INTERVAL_100MS, 1, 7);

		long started = System.currentTimeMillis();
		poller.start();
		int result = poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals(7, result);
		Assertions.assertEquals(1, poller.m_pollCount.get());
		// interval 만큼 sleep하지 않고 곧바로 종료되어야 한다.
		Assertions.assertTrue(elapsed < 100, "elapsed=" + elapsed);
	}

	// ----- 라이프사이클 훅 -----

	@Test
	public void testLifecycleHooksOnSuccess() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 2, 99);

		poller.start();
		poller.waitForFinished().get();

		Assertions.assertEquals(1, poller.m_initCount.get());
		Assertions.assertEquals(2, poller.m_pollCount.get());
		Assertions.assertEquals(1, poller.m_finalizeCount.get());
		// 정상 종료 시 finalizePoller에 결과값이 전달되어야 한다.
		Assertions.assertEquals(Integer.valueOf(99), poller.m_finalizedState.get());
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

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(1, poller.m_finalizeCount.get());
		Assertions.assertNull(poller.m_finalizedState.get());
	}

	@Test
	public void testFinalizePollerStateNullOnTimeout() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, Integer.MAX_VALUE, 0);
		poller.setTimeout(Duration.ofMillis(150));

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assertions.assertEquals(1, poller.m_finalizeCount.get());
		Assertions.assertNull(poller.m_finalizedState.get());
	}

	@Test
	public void testFinalizePollerStateNullOnException() throws Exception {
		AtomicReference<Integer> finalized = new AtomicReference<>();
		AtomicInteger finalizeCount = new AtomicInteger(0);

		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
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

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, finalizeCount.get());
		Assertions.assertNull(finalized.get());
	}

	@Test
	public void testFinalizePollerNotCalledWhenInitializePollerFails() throws Exception {
		AtomicInteger initCount = new AtomicInteger(0);
		AtomicInteger pollCount = new AtomicInteger(0);
		AtomicInteger finalizeCount = new AtomicInteger(0);

		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected void initializePoller() throws Exception {
				initCount.incrementAndGet();
				throw new RuntimeException("init failed");
			}
			@Override
			protected FOption<Integer> tryPoll() {
				pollCount.incrementAndGet();
				return FOption.empty();
			}
			@Override
			protected void finalizePoller(@Nullable Integer state) {
				finalizeCount.incrementAndGet();
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, initCount.get());
		// tryPoll도 finalizePoller도 호출되지 않아야 한다.
		Assertions.assertEquals(0, pollCount.get());
		Assertions.assertEquals(0, finalizeCount.get());
	}

	@Test
	public void testFinalizePollerExceptionIsSwallowed() throws Exception {
		// finalizePoller에서 던진 예외는 Unchecked.runOrIgnore에 의해 swallow되어야 하며,
		// poller 결과는 정상 종료여야 한다.
		AtomicInteger finalizeCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(7);
			}
			@Override
			protected void finalizePoller(@Nullable Integer state) {
				finalizeCount.incrementAndGet();
				throw new RuntimeException("boom in finalize");
			}
		};

		poller.start();
		int result = poller.waitForFinished(2, TimeUnit.SECONDS).get();

		Assertions.assertEquals(7, result);
		Assertions.assertEquals(1, finalizeCount.get());
	}

	@Test
	public void testInitializePollerCanReadParentDue() throws Exception {
		// initializePoller 호출 시점에 부모의 setTimeout으로부터 파생된 m_due가 설정되어 있어야 한다.
		AtomicReference<Instant> dueInsideInit = new AtomicReference<>();

		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected void initializePoller() throws Exception {
				dueInsideInit.set(getDue());
			}
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(1);
			}
		};
		poller.setTimeout(Duration.ofSeconds(5));

		poller.start();
		poller.waitForFinished().get();

		// initializePoller에서 본 getDue()가 non-null이어야 한다 (super.initializeLoop이 먼저 실행됐음).
		Assertions.assertNotNull(dueInsideInit.get());
		Assertions.assertTrue(dueInsideInit.get().isAfter(Instant.now().minusSeconds(10)));
	}

	// ----- 예외 / 취소 / null 반환 -----

	@Test
	public void testNullReturnFromTryPollFails() throws Exception {
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return null;
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof NullPointerException);
	}

	@Test
	public void testTryPollThrowsCancellationCancelsPoller() throws Exception {
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() throws CancellationException {
				throw new CancellationException("stop");
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isCancelled());
	}

	@Test
	public void testTryPollThrowsExceptionFails() throws Exception {
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				throw new IllegalArgumentException("bad");
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof IllegalArgumentException);
		Assertions.assertEquals("bad", result.getFailureCause().getMessage());
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

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assertions.assertTrue(elapsed >= 150 && elapsed < 400, "elapsed=" + elapsed);
	}

	@Test
	public void testDueInPastImmediateTimeout() throws Exception {
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				pollCount.incrementAndGet();
				return FOption.empty();
			}
		};
		poller.setDue(Instant.now().minusSeconds(1));

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(1, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// tryPoll이 한 번도 호출되지 않아야 한다 (부모 클래스의 iteration 진입 시점 검사로 인해).
		Assertions.assertEquals(0, pollCount.get());
	}

	// ----- cancel -----

	@Test
	public void testCancelDuringPolling() throws Exception {
		AtomicInteger pollCount = new AtomicInteger(0);
		AtomicBoolean cancelled = new AtomicBoolean(false);

		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				pollCount.incrementAndGet();
				return FOption.empty();
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

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertTrue(pollCount.get() > 0, "적어도 한 번은 polling되었어야 한다");
	}

	// ----- cumulative 모드 -----

	@Test
	public void testCumulativeModeCatchUp() throws Exception {
		// tryPoll이 interval보다 오래 걸리면 누적 모드에서는 catch-up.
		// interval=50ms, action=80ms, target=4 → 약 4 * 80ms = 320ms.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS, true) {
			@Override
			protected FOption<Integer> tryPoll() throws InterruptedException {
				int n = pollCount.incrementAndGet();
				Thread.sleep(80);
				return n >= 4 ? FOption.of(n) : FOption.empty();
			}
		};

		long started = System.currentTimeMillis();
		poller.start();
		int result = poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals(4, result);
		// catch-up이라 interval 대기가 거의 없음. 너그럽게 검사.
		Assertions.assertTrue(elapsed >= 280 && elapsed < 600, "elapsed=" + elapsed);
	}

	@Test
	public void testCumulativeIsDefaultOneArgConstructor() throws Exception {
		// 1-arg 생성자는 cumulative=true를 기본으로 한다.
		// interval=50ms, action=80ms (interval보다 큼), target=3.
		// cumulative이면 catch-up되어 약 3 * 80ms = 240ms 정도.
		// non-cumulative이었다면 3 * (80+50) = 약 390ms 였을 것.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() throws InterruptedException {
				int n = pollCount.incrementAndGet();
				Thread.sleep(80);
				return n >= 3 ? FOption.of(n) : FOption.empty();
			}
		};

		long started = System.currentTimeMillis();
		poller.start();
		poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertEquals(3, pollCount.get());
		// catch-up 동작이 발생하므로 non-cumulative 시나리오(~390ms)보다 빨라야 한다.
		Assertions.assertTrue(elapsed < 350, "elapsed=" + elapsed);
	}

	@Test
	public void testNonCumulativeWithTwoArgConstructor() throws Exception {
		// 2-arg로 cumulative=false를 명시한 경우 매 iteration 종료 후 interval 만큼 대기.
		// interval=50ms, action=10ms, target=4 → 약 3 * 50 + action ≈ 160ms.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS, false) {
			@Override
			protected FOption<Integer> tryPoll() throws InterruptedException {
				int n = pollCount.incrementAndGet();
				Thread.sleep(10);
				return n >= 4 ? FOption.of(n) : FOption.empty();
			}
		};

		long started = System.currentTimeMillis();
		poller.start();
		poller.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertTrue(elapsed >= 130 && elapsed < 350, "elapsed=" + elapsed);
	}

	// ----- 인덱스 / 호출 횟수 -----

	@Test
	public void testTryPollInvokedRepeatedlyUntilTargetReached() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, 5, 123);

		poller.start();
		Assertions.assertEquals(123, (int)poller.waitForFinished().get());
		Assertions.assertEquals(5, poller.m_pollCount.get());
	}

	// ----- setter 호출 후 시작 시 부모 검증 동작 -----

	@Test
	public void testSetTimeoutAfterStart() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			CountingPoller poller = new CountingPoller(INTERVAL_50MS, 100, 0);
			poller.start();
			try {
				poller.setTimeout(Duration.ofSeconds(1));
			}
			finally {
				poller.cancel(true);
				poller.waitForFinished();
			}
			});
	}
}
