package utils.async;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
		final AtomicReference<AsyncResult<Integer>> m_finalizedState = new AtomicReference<>();

		CountingPoller(Duration interval, int targetIteration, int resultValue) {
			super(interval);
			m_targetIteration = targetIteration;
			m_resultValue = resultValue;
		}

		CountingPoller(Duration interval, IntervalMode mode, int targetIteration, int resultValue) {
			super(interval, mode);
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
		protected void cleanupPoller(AsyncResult<Integer> outcome) {
			m_finalizeCount.incrementAndGet();
			m_finalizedState.set(outcome);
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
		// 정상 종료 시 cleanupPoller에 완료 결과와 결과값이 전달되어야 한다.
		Assertions.assertTrue(poller.m_finalizedState.get().isCompleted());
		Assertions.assertEquals(Integer.valueOf(99), poller.m_finalizedState.get().getUnchecked());
	}

	@Test
	public void testCleanupPollerOutcomeOnCancel() throws Exception {
		// 절대 도달하지 않는 목표를 설정한 뒤 취소하면, cleanupPoller에 취소 결과가 전달되어야 한다.
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
		Assertions.assertTrue(poller.m_finalizedState.get().isCancelled());
	}

	@Test
	public void testCleanupPollerOutcomeOnTimeout() throws Exception {
		CountingPoller poller = new CountingPoller(INTERVAL_50MS, Integer.MAX_VALUE, 0);
		poller.setTimeout(Duration.ofMillis(150));

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		Assertions.assertEquals(1, poller.m_finalizeCount.get());
		Assertions.assertTrue(poller.m_finalizedState.get().isFailed());
		Assertions.assertInstanceOf(TimeoutException.class, poller.m_finalizedState.get().getFailureCause());
	}

	@Test
	public void testCleanupPollerOutcomeOnException() throws Exception {
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AtomicInteger finalizeCount = new AtomicInteger(0);

		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				throw new IllegalStateException("boom");
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalizeCount.incrementAndGet();
				finalized.set(outcome);
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, finalizeCount.get());
		Assertions.assertTrue(finalized.get().isFailed());
		Assertions.assertEquals("boom", finalized.get().getFailureCause().getMessage());
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
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalizeCount.incrementAndGet();
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, initCount.get());
		// tryPoll도 cleanupPoller도 호출되지 않아야 한다.
		Assertions.assertEquals(0, pollCount.get());
		Assertions.assertEquals(0, finalizeCount.get());
	}

	@Test
	public void testFinalizePollerExceptionIsSwallowed() throws Exception {
		// cleanupPoller에서 던진 예외는 poller 결과에 영향을 주지 않아야 하며(정상 종료),
		// 조용히 사라지지 않고 경고 로그로 남아야 한다.
		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
				LoggerFactory.getLogger(AbstractPeriodicPollerTest.class.getName() + ".finalizeException");
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		AtomicInteger finalizeCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(7);
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalizeCount.incrementAndGet();
				throw new RuntimeException("boom in finalize");
			}
		};
		poller.setLogger(logger);

		try {
			poller.start();
			int result = poller.waitForFinished(2, TimeUnit.SECONDS).get();

			Assertions.assertEquals(7, result);
			Assertions.assertEquals(1, finalizeCount.get());
			Assertions.assertTrue(appender.list.stream().anyMatch(ev -> ev.getLevel() == Level.WARN
										&& ev.getThrowableProxy() != null
										&& "boom in finalize".equals(ev.getThrowableProxy().getMessage())),
								"cleanupPoller exception must be logged: " + appender.list);
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	// ----- 기본 주기 모드 -----

	@Test
	public void testDefaultConstructorUsesFixedDelay() throws Exception {
		// 단일 인자 생성자는 fixed-delay 모드: polling(150ms)이 주기(100ms)보다 오래 걸려도
		// 다음 polling 전에 주기만큼 대기해야 한다(누적 모드였다면 대기 없이 곧바로 시작된다).
		long[] starts = new long[3];
		long[] ends = new long[3];
		AtomicInteger count = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_100MS) {
			@Override
			protected FOption<Integer> tryPoll() throws InterruptedException {
				int idx = count.getAndIncrement();
				starts[idx] = System.nanoTime();
				Thread.sleep(150);
				ends[idx] = System.nanoTime();
				return idx == 2 ? FOption.of(idx) : FOption.empty();
			}
		};

		poller.start();
		Assertions.assertEquals(2, (int)poller.waitForFinished(5, TimeUnit.SECONDS).get());
		for ( int i = 0; i < 2; ++i ) {
			long gap = TimeUnit.NANOSECONDS.toMillis(starts[i+1] - ends[i]);
			Assertions.assertTrue(gap >= 90, "gap after poll end=" + gap);
		}
	}

	@Test
	public void testPeriodicPollerBuilderDefaultsToFixedDelay() throws Exception {
		// interval()/delay()를 지정하지 않은 PeriodicPoller는 delay(1초)로 동작해야 한다.
		PeriodicPoller<Boolean> poller = PeriodicPoller.pollUntil(() -> true).build();
		Assertions.assertEquals(Duration.ofSeconds(1), poller.getLoopInterval());
		Assertions.assertEquals(IntervalMode.FIXED_DELAY, poller.getIntervalMode());
		Assertions.assertTrue(poller.toString().contains("fixedDelay="), poller.toString());

		// fixedRate()/fixedDelay()는 각각 FIXED_RATE/FIXED_DELAY로 설정해야 한다.
		PeriodicPoller<Boolean> rate = PeriodicPoller.pollUntil(() -> true).fixedRate(INTERVAL_50MS).build();
		Assertions.assertEquals(IntervalMode.FIXED_RATE, rate.getIntervalMode());
		Assertions.assertEquals(INTERVAL_50MS, rate.getLoopInterval());
		Assertions.assertTrue(rate.toString().contains("fixedRate="), rate.toString());
		Assertions.assertEquals(IntervalMode.FIXED_DELAY,
								PeriodicPoller.pollUntil(() -> true).fixedDelay(INTERVAL_50MS).build().getIntervalMode());
	}

	@Test
	public void testIntervalModeOfConstructors() {
		// 단일 인자 생성자는 FIXED_DELAY, IntervalMode 생성자는 지정한 모드를 따라야 한다.
		class ModePoller extends AbstractPeriodicPoller<Integer> {
			ModePoller() { super(INTERVAL_50MS); }
			ModePoller(IntervalMode mode) { super(INTERVAL_50MS, mode); }
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(0);
			}
		}
		Assertions.assertEquals(IntervalMode.FIXED_DELAY, new ModePoller().getIntervalMode());
		Assertions.assertEquals(IntervalMode.FIXED_RATE, new ModePoller(IntervalMode.FIXED_RATE).getIntervalMode());
		Assertions.assertThrows(IllegalArgumentException.class, () -> new ModePoller((IntervalMode)null));
	}

	// ----- 결과 반영과 외부 종료의 경합 -----

	@Test
	public void testTargetReachedAfterExternalFailureIsNotApplied() throws Exception {
		// tryPoll 수행 중 외부에서 FAILED로 확정된 뒤 tryPoll이 값을 반환하면,
		// onTargetReached는 호출되지 않고 cleanupPoller에는 외부에서 확정된 실패가 전달되어야 한다.
		IllegalStateException external = new IllegalStateException("external");
		AtomicInteger targetCalls = new AtomicInteger();
		AtomicReference<Integer> discarded = new AtomicReference<>();
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				notifyFailed(external);
				return FOption.of(7);
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				targetCalls.incrementAndGet();
				return result;
			}
			@Override
			protected void onTargetDiscarded(Integer value) {
				discarded.set(value);
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed(), result.toString());
		Assertions.assertSame(external, result.getFailureCause());
		Assertions.assertTrue(waitUntil(() -> finalized.get() != null), "cleanupPoller not called");
		Assertions.assertEquals(0, targetCalls.get());
		Assertions.assertTrue(finalized.get().isFailed());
		Assertions.assertSame(external, finalized.get().getFailureCause());
		// 채택되지 못한 값은 자원 정리를 위해 onTargetDiscarded로 전달되어야 한다.
		Assertions.assertEquals(Integer.valueOf(7), discarded.get());
	}

	@Test
	public void testExternalFailureDuringOnTargetReachedIsNotAdoptedAsResult() throws Exception {
		// onTargetReached 수행 도중 외부에서 FAILED로 확정되면, 그 결과는 최종 결과로 채택되지 않고
		// cleanupPoller에는 외부에서 확정된 실패가 전달되어야 한다.
		IllegalStateException external = new IllegalStateException("external");
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AtomicReference<Integer> discarded = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(7);
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				notifyFailed(external);
				return result * 10;
			}
			@Override
			protected void onTargetDiscarded(Integer value) {
				discarded.set(value);
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertTrue(finalized.get().isFailed());
		Assertions.assertSame(external, finalized.get().getFailureCause());
		// onTargetReached가 반환했지만 채택되지 못한 값이 onTargetDiscarded로 전달되어야 한다.
		Assertions.assertEquals(Integer.valueOf(70), discarded.get());
	}

	@Test
	public void testTargetReachedWhileCancellingCompletes() throws Exception {
		// 취소 요청(CANCELLING) 중에 목표 상태에 도달한 경우는 기존 정책대로 완료가 허용되어야 하며,
		// 이때는 onTargetReached가 호출되고 cleanupPoller에 완료 결과가 전달되어야 한다.
		AtomicInteger targetCalls = new AtomicInteger();
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				Assertions.assertTrue(cancel(true));		// RUNNING → CANCELLING
				return FOption.of(7);
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				targetCalls.incrementAndGet();
				return result;
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		Assertions.assertEquals(7, (int)poller.run());
		Assertions.assertTrue(poller.isCompleted());
		Assertions.assertEquals(1, targetCalls.get());
		Assertions.assertEquals(Integer.valueOf(7), finalized.get().getUnchecked());
	}

	private static boolean waitUntil(java.util.function.BooleanSupplier cond) throws InterruptedException {
		long due = System.currentTimeMillis() + 2_000;
		while ( !cond.getAsBoolean() ) {
			if ( System.currentTimeMillis() > due ) {
				return false;
			}
			Thread.sleep(10);
		}
		return true;
	}

	// ----- polling 시도 번호 -----

	@Test
	public void testPollAttempt() throws Exception {
		// getPollAttempt()는 첫 polling 전 -1, tryPoll/onTargetReached 안에서는 현재 시도 번호(0부터),
		// cleanupPoller 안에서는 마지막 시도 번호여야 한다.
		java.util.List<Long> seenInPoll = new java.util.concurrent.CopyOnWriteArrayList<>();
		AtomicReference<Long> seenInTarget = new AtomicReference<>();
		AtomicReference<Long> seenInFinalize = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(Duration.ofMillis(10)) {
			@Override
			protected FOption<Integer> tryPoll() {
				seenInPoll.add(getPollAttempt());
				return getPollAttempt() == 2 ? FOption.of(7) : FOption.empty();
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				seenInTarget.set(getPollAttempt());
				return result;
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				seenInFinalize.set(getPollAttempt());
			}
		};
		Assertions.assertEquals(-1L, poller.getPollAttempt());

		poller.start();
		Assertions.assertEquals(7, (int)poller.waitForFinished(2, TimeUnit.SECONDS).get());
		Assertions.assertEquals(java.util.List.of(0L, 1L, 2L), seenInPoll);
		Assertions.assertEquals(2L, seenInTarget.get());
		Assertions.assertEquals(2L, seenInFinalize.get());
	}

	// ----- onTargetReached / onTargetDiscarded / cleanupPoller -----

	@Test
	public void testOnTargetReachedTransformsResult() throws Exception {
		// onTargetReached가 반환한 값이 최종 결과가 되고, cleanupPoller에도 그 값이 전달되어야 한다.
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(21);
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				return result * 2;
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		poller.start();
		Assertions.assertEquals(42, (int)poller.waitForFinished(2, TimeUnit.SECONDS).get());
		Assertions.assertEquals(Integer.valueOf(42), finalized.get().getUnchecked());
	}

	@Test
	public void testOnTargetReachedFailureFailsPoller() throws Exception {
		// onTargetReached의 예외는 cleanupPoller와 달리 polling 결과(FAILED)에 반영되어야 하며,
		// cleanupPoller에도 그 실패가 전달되어야 한다.
		IllegalStateException boom = new IllegalStateException("apply failed");
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AtomicReference<Integer> discarded = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(7);
			}
			@Override
			protected Integer onTargetReached(Integer result) {
				throw boom;
			}
			@Override
			protected void onTargetDiscarded(Integer value) {
				discarded.set(value);
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed(), result.toString());
		Assertions.assertSame(boom, result.getFailureCause());
		Assertions.assertSame(boom, finalized.get().getFailureCause());
		Assertions.assertEquals(Integer.valueOf(7), discarded.get());
	}

	@Test
	public void testCleanupPollerDistinguishesNullResult() throws Exception {
		// 목표 상태 값이 null인 정상 종료도 완료(COMPLETED)로 전달되어 비정상 종료와 구분되어야 한다.
		AtomicReference<AsyncResult<Integer>> finalized = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(null);
			}
			@Override
			protected void cleanupPoller(AsyncResult<Integer> outcome) {
				finalized.set(outcome);
			}
		};

		poller.start();
		AsyncResult<Integer> result = poller.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isCompleted(), result.toString());
		Assertions.assertTrue(finalized.get().isCompleted());
		Assertions.assertNull(finalized.get().getUnchecked());
	}

	@Test
	public void testHooksMayThrowCheckedExceptions() throws Exception {
		// tryPoll/onTargetReached는 IOException 등을 감싸지 않고 던질 수 있으며, 그 예외가 그대로 실패 원인이 되어야 한다.
		java.io.IOException ioe = new java.io.IOException("io failure");
		AbstractPeriodicPoller<Integer> polling = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() throws java.io.IOException {
				throw ioe;
			}
		};
		polling.start();
		AsyncResult<Integer> result = polling.waitForFinished(2, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isFailed(), result.toString());
		Assertions.assertSame(ioe, result.getFailureCause());

		AtomicReference<Integer> discarded = new AtomicReference<>();
		AbstractPeriodicPoller<Integer> applying = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(3);
			}
			@Override
			protected Integer onTargetReached(Integer value) throws java.io.IOException {
				throw ioe;
			}
			@Override
			protected void onTargetDiscarded(Integer value) {
				discarded.set(value);
			}
		};
		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, applying::run);
		Assertions.assertSame(ioe, thrown.getCause());
		Assertions.assertEquals(Integer.valueOf(3), discarded.get());
	}

	@Test
	public void testOnTargetDiscardedExceptionIsLoggedAndIgnored() throws Exception {
		// onTargetDiscarded의 예외는 원래의 실패 원인을 가리지 않아야 한다.
		IllegalStateException boom = new IllegalStateException("apply failed");
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS) {
			@Override
			protected FOption<Integer> tryPoll() {
				return FOption.of(1);
			}
			@Override
			protected Integer onTargetReached(Integer value) {
				throw boom;
			}
			@Override
			protected void onTargetDiscarded(Integer value) {
				throw new IllegalStateException("discard failed");
			}
		};
		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertSame(boom, thrown.getCause());
	}

	@Test
	public void testCleanupPollerOutcomeWithFOptionResultType() throws Exception {
		// 결과 타입이 FOption인 하위 클래스도 정리 hook을 override할 수 있어야 한다(이전 오버로드의 name clash 회귀 방지).
		AtomicReference<AsyncResult<FOption<String>>> finalized = new AtomicReference<>();
		AbstractPeriodicPoller<FOption<String>> poller = new AbstractPeriodicPoller<FOption<String>>(INTERVAL_50MS) {
			@Override
			protected FOption<FOption<String>> tryPoll() {
				return FOption.of(FOption.of("x"));
			}
			@Override
			protected void cleanupPoller(AsyncResult<FOption<String>> outcome) {
				finalized.set(outcome);
			}
		};
		Assertions.assertEquals("x", poller.run().get());
		Assertions.assertEquals("x", finalized.get().getUnchecked().get());
	}

	// ----- PeriodicPoller (builder) -----

	@Test
	public void testPeriodicPollerSupplierExceptionIsFailureCause() throws Exception {
		// supplier의 checked 예외는 감싸지지 않고 그대로 실패 원인이 되어야 한다.
		java.io.IOException ioe = new java.io.IOException("io failure");
		PeriodicPoller<String> poller = PeriodicPoller.<String>poll(() -> { throw ioe; })
														.fixedDelay(INTERVAL_50MS).build();
		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertSame(ioe, thrown.getCause());
		Assertions.assertSame(ioe, poller.poll().getFailureCause());
	}

	@Test
	public void testPeriodicPollerOnDiscardedAndCleanup() throws Exception {
		// supplier가 값을 얻었지만 그 사이 외부에서 실패가 확정되면, 그 값은 onDiscarded로 넘겨져야 하고
		// cleanup에는 확정된 실패가 전달되어야 한다.
		IllegalStateException external = new IllegalStateException("external");
		AtomicReference<PeriodicPoller<String>> self = new AtomicReference<>();
		AtomicReference<String> discarded = new AtomicReference<>();
		AtomicReference<AsyncResult<String>> cleaned = new AtomicReference<>();
		PeriodicPoller<String> poller = PeriodicPoller.poll(() -> {
												self.get().notifyFailed(external);
												return "connection";
											})
											.fixedDelay(INTERVAL_50MS)
											.onDiscarded(discarded::set)
											.cleanup(cleaned::set)
											.build();
		self.set(poller);

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertEquals("connection", discarded.get());
		Assertions.assertTrue(cleaned.get().isFailed());
		Assertions.assertSame(external, cleaned.get().getFailureCause());
	}

	@Test
	public void testPeriodicPollerCleanupReceivesOutcome() throws Exception {
		// 정상 완료 시 cleanup에 완료 결과가, timeout 시 TimeoutException 실패가 전달되어야 하며,
		// 채택된 결과는 onDiscarded로 넘겨지지 않아야 한다.
		AtomicReference<AsyncResult<String>> completed = new AtomicReference<>();
		AtomicReference<String> discarded = new AtomicReference<>();
		Assertions.assertEquals("ok", PeriodicPoller.poll(() -> "ok").fixedDelay(INTERVAL_50MS)
														.onDiscarded(discarded::set)
														.cleanup(completed::set)
														.build().run());
		Assertions.assertEquals("ok", completed.get().getUnchecked());
		Assertions.assertNull(discarded.get());

		AtomicReference<AsyncResult<Boolean>> timedOut = new AtomicReference<>();
		PeriodicPoller<Boolean> poller = PeriodicPoller.pollUntil(() -> false).fixedDelay(INTERVAL_50MS)
														.timeout(Duration.ofMillis(150))
														.cleanup(timedOut::set)
														.build();
		Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertTrue(timedOut.get().isFailed());
		Assertions.assertInstanceOf(TimeoutException.class, timedOut.get().getFailureCause());
	}

	@Test
	public void testPeriodicPollerTimeoutMeasuredFromStart() throws Exception {
		// timeout은 빌더 생성 시점이 아니라 polling이 실제로 시작된 시점부터 측정되어야 한다.
		AtomicInteger count = new AtomicInteger();
		PeriodicPoller<Integer> poller = PeriodicPoller.poll(() -> count.incrementAndGet() >= 2 ? 1 : null)
														.fixedDelay(INTERVAL_50MS)
														.timeout(Duration.ofMillis(300))
														.build();
		Thread.sleep(400);		// 빌더 생성 후 timeout보다 오래 지난 뒤 시작한다.
		Assertions.assertEquals(1, (int)poller.run());
	}

	@Test
	public void testPeriodicPollerPollContext() throws Exception {
		// PollContext를 받는 polling 함수는 시도 번호(0부터)와 남은 제한 시간을 사용할 수 있어야 한다.
		java.util.List<Long> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
		java.util.List<FOption<Duration>> remainings = new java.util.concurrent.CopyOnWriteArrayList<>();
		PeriodicPoller<Long> poller = PeriodicPoller.poll(ctx -> {
												attempts.add(ctx.attempt());
												remainings.add(ctx.remainingTime());
												return ctx.attempt() == 2 ? ctx.attempt() : null;
											})
											.fixedDelay(Duration.ofMillis(10))
											.timeout(Duration.ofSeconds(10))
											.build();
		Assertions.assertEquals(2L, (long)poller.run());
		Assertions.assertEquals(java.util.List.of(0L, 1L, 2L), attempts);
		for ( FOption<Duration> remaining: remainings ) {
			Assertions.assertTrue(remaining.isPresent());
			Assertions.assertTrue(remaining.get().compareTo(Duration.ZERO) > 0
									&& remaining.get().compareTo(Duration.ofSeconds(10)) <= 0, remaining.get().toString());
		}

		// 제한 시간이 없으면 remainingTime()은 empty여야 한다.
		AtomicReference<FOption<Duration>> unlimited = new AtomicReference<>();
		PeriodicPoller.poll(ctx -> { unlimited.set(ctx.remainingTime()); return 1; }).build().run();
		Assertions.assertTrue(unlimited.get().isAbsent());
	}

	@Test
	public void testPeriodicPollerDiscardsResultWhenFailedDuringCleanup() throws Exception {
		// supplier가 연결을 반환한 뒤 cleanup(COMPLETED) 수행 도중 외부에서 실패가 확정되면,
		// 완료 전이가 실패하므로 그 연결은 onDiscarded로 정확히 1회 넘겨져야 한다.
		IllegalStateException external = new IllegalStateException("external");
		AtomicReference<PeriodicPoller<String>> self = new AtomicReference<>();
		java.util.List<String> discarded = new java.util.concurrent.CopyOnWriteArrayList<>();
		AtomicReference<AsyncResult<String>> cleaned = new AtomicReference<>();
		PeriodicPoller<String> poller = PeriodicPoller.poll(() -> "connection")
											.fixedDelay(INTERVAL_50MS)
											.onDiscarded(discarded::add)
											.cleanup(outcome -> {
												cleaned.set(outcome);
												self.get().notifyFailed(external);
											})
											.build();
		self.set(poller);

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertTrue(poller.isFailed());
		Assertions.assertTrue(cleaned.get().isCompleted());		// 정리 시점의 결과는 아직 완료
		Assertions.assertEquals(java.util.List.of("connection"), discarded);

		// start() 경로에서도 동일해야 한다.
		discarded.clear();
		PeriodicPoller<String> async = PeriodicPoller.poll(() -> "connection")
											.fixedDelay(INTERVAL_50MS)
											.onDiscarded(discarded::add)
											.cleanup(outcome -> self.get().notifyFailed(external))
											.build();
		self.set(async);
		async.start();
		Assertions.assertTrue(async.waitForFinished(2, TimeUnit.SECONDS).isFailed());
		Assertions.assertTrue(waitUntil(() -> discarded.size() == 1), "connection not discarded");
		Thread.sleep(50);
		Assertions.assertEquals(java.util.List.of("connection"), discarded);
	}

	@Test
	public void testPeriodicPollerConditionFactoriesRejectNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> PeriodicPoller.pollUntil(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> PeriodicPoller.pollWhile(null));
	}

	@Test
	public void testPeriodicPollerDueInPastSkipsInitializerAndCleanup() throws Exception {
		// due가 이미 지났으면 시작 단계에서 실패하며, initializer/supplier/cleanup 모두 호출되지 않아야 한다.
		AtomicInteger initCalls = new AtomicInteger();
		AtomicInteger pollCalls = new AtomicInteger();
		AtomicInteger cleanupCalls = new AtomicInteger();
		PeriodicPoller<Integer> poller = PeriodicPoller.poll(() -> { pollCalls.incrementAndGet(); return 1; })
														.due(Instant.now().minusSeconds(1))
														.initializer(initCalls::incrementAndGet)
														.cleanup(r -> cleanupCalls.incrementAndGet())
														.build();
		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, poller::run);
		Assertions.assertInstanceOf(TimeoutException.class, thrown.getCause());
		Assertions.assertEquals(0, initCalls.get());
		Assertions.assertEquals(0, pollCalls.get());
		Assertions.assertEquals(0, cleanupCalls.get());
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
		// 원인을 알 수 있도록 tryPoll()을 지목하는 메시지가 있어야 한다.
		String msg = result.getFailureCause().getMessage();
		Assertions.assertNotNull(msg);
		Assertions.assertTrue(msg.startsWith("tryPoll() must not return null"), msg);
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
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS, IntervalMode.FIXED_RATE) {
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
		// IntervalMode.FIXED_DELAY를 명시한 경우 매 iteration 종료 후 interval 만큼 대기.
		// interval=50ms, action=10ms, target=4 → 약 3 * 50 + action ≈ 160ms.
		AtomicInteger pollCount = new AtomicInteger(0);
		AbstractPeriodicPoller<Integer> poller = new AbstractPeriodicPoller<Integer>(INTERVAL_50MS, IntervalMode.FIXED_DELAY) {
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
