package utils.async;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.common.util.concurrent.Service.State;

import utils.RuntimeInterruptedException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;


/**
 * {@link PeriodicLoopService}의 주기 설정({@code fixedRate}/{@code fixedDelay})을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopServiceTest {
	@Test
	public void builder_default_is_fixedRate_one_second() {
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> { }).build();
		Assertions.assertEquals(IntervalMode.FIXED_RATE, svc.getIntervalMode());
		Assertions.assertEquals(Duration.ofSeconds(1), svc.getLoopInterval());
		Assertions.assertTrue(svc.toString().contains("fixedRate="), svc.toString());
	}

	@Test
	public void fixedRate_and_fixedDelay_set_interval_mode() {
		PeriodicLoopService rate = PeriodicLoopService.builder(() -> { })
														.fixedRate(Duration.ofMillis(200)).build();
		Assertions.assertEquals(IntervalMode.FIXED_RATE, rate.getIntervalMode());
		Assertions.assertEquals(Duration.ofMillis(200), rate.getLoopInterval());

		PeriodicLoopService delay = PeriodicLoopService.builder(() -> { })
														.fixedDelay(Duration.ofMillis(300)).build();
		Assertions.assertEquals(IntervalMode.FIXED_DELAY, delay.getIntervalMode());
		Assertions.assertEquals(Duration.ofMillis(300), delay.getLoopInterval());
		Assertions.assertTrue(delay.toString().contains("fixedDelay="), delay.toString());

		// 나중에 호출된 쪽이 적용된다.
		Assertions.assertEquals(IntervalMode.FIXED_DELAY,
								PeriodicLoopService.builder(() -> { }).fixedRate(Duration.ofMillis(100))
																	.fixedDelay(Duration.ofMillis(100))
																	.build().getIntervalMode());
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> PeriodicLoopService.builder().fixedRate(Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> PeriodicLoopService.builder().fixedDelay(null));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void fixedDelay_waits_after_each_iteration() throws Exception {
		// 작업(150ms)이 주기(100ms)보다 오래 걸려도 fixedDelay는 작업 종료 후 주기만큼 대기해야 한다.
		List<long[]> spans = new CopyOnWriteArrayList<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															long started = System.nanoTime();
															Thread.sleep(150);
															spans.add(new long[] { started, System.nanoTime() });
														})
														.fixedDelay(Duration.ofMillis(100))
														.build();
		svc.startAsync().awaitRunning();
		while ( spans.size() < 3 ) {
			Thread.sleep(20);
		}
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);

		for ( int i = 0; i < 2; ++i ) {
			long gap = TimeUnit.NANOSECONDS.toMillis(spans.get(i+1)[0] - spans.get(i)[1]);
			Assertions.assertTrue(gap >= 90, "gap after iteration end=" + gap);
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void fixedRate_keeps_cadence_and_catches_up() throws Exception {
		// 주기 100ms, 첫 작업만 250ms가 걸리면 FIXED_RATE는 밀린 iteration을 대기 없이 연달아 수행한 뒤
		// 기준 시점 + 100ms*n 박자로 돌아와야 한다.
		List<Long> starts = new CopyOnWriteArrayList<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															starts.add(System.nanoTime());
															if ( starts.size() == 1 ) {
																Thread.sleep(250);
															}
														})
														.fixedRate(Duration.ofMillis(100))
														.build();
		svc.startAsync().awaitRunning();
		while ( starts.size() < 6 ) {
			Thread.sleep(10);
		}
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);

		// 2·3번째 iteration은 첫 작업(250ms)이 끝나자마자 연달아 시작된다(catch-up).
		long gap12 = TimeUnit.NANOSECONDS.toMillis(starts.get(2) - starts.get(1));
		Assertions.assertTrue(gap12 < 50, "catch-up gap=" + gap12);
		// 이후에는 기준 박자(약 100ms 간격)를 유지한다.
		long gap45 = TimeUnit.NANOSECONDS.toMillis(starts.get(5) - starts.get(4));
		Assertions.assertTrue(gap45 >= 80 && gap45 < 200, "steady gap=" + gap45);
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void initialDelay_postpones_first_iteration() throws Exception {
		List<Long> starts = new CopyOnWriteArrayList<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> starts.add(System.nanoTime()))
														.fixedRate(Duration.ofMillis(50))
														.initialDelay(Duration.ofMillis(200))
														.build();
		long started = System.nanoTime();
		svc.startAsync().awaitRunning();
		while ( starts.isEmpty() ) {
			Thread.sleep(10);
		}
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);

		long firstDelay = TimeUnit.NANOSECONDS.toMillis(starts.get(0) - started);
		Assertions.assertTrue(firstDelay >= 180, "first iteration delay=" + firstDelay);
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void stop_wakes_up_waiting_loop_immediately() throws Exception {
		// 긴 주기로 대기 중일 때 stopAsync()를 호출하면 주기가 끝나기를 기다리지 않고 곧바로 종료되어야 한다.
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> { })
														.fixedDelay(Duration.ofSeconds(30))
														.build();
		svc.startAsync().awaitRunning();
		Thread.sleep(100);

		long stopStarted = System.nanoTime();
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted);
		Assertions.assertTrue(stopMillis < 1_000, "stop took " + stopMillis + "ms");
		Assertions.assertEquals(1, svc.getIterationCount());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void loop_thread_is_daemon_by_default_and_named_after_service() throws Exception {
		AtomicReference<Thread> loopThread = new AtomicReference<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> loopThread.set(Thread.currentThread()))
														.name("daemon-check")
														.fixedDelay(Duration.ofSeconds(10))
														.build();
		Assertions.assertTrue(svc.isDaemon());
		svc.startAsync().awaitRunning();
		try {
			while ( loopThread.get() == null ) {
				Thread.sleep(10);
			}
			Assertions.assertTrue(loopThread.get().isDaemon(), "loop thread must be a daemon thread");
			Assertions.assertEquals("daemon-check", loopThread.get().getName());
		}
		finally {
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void daemon_false_creates_non_daemon_loop_thread() throws Exception {
		AtomicReference<Thread> loopThread = new AtomicReference<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> loopThread.set(Thread.currentThread()))
														.fixedDelay(Duration.ofSeconds(10))
														.daemon(false)
														.build();
		Assertions.assertFalse(svc.isDaemon());
		svc.startAsync().awaitRunning();
		try {
			while ( loopThread.get() == null ) {
				Thread.sleep(10);
			}
			Assertions.assertFalse(loopThread.get().isDaemon(), "loop thread must not be a daemon thread");
		}
		finally {
			// non-daemon 쓰레드가 남으면 테스트 JVM 종료를 막으므로 반드시 중지한다.
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void repeated_failures_are_logged_without_flooding() throws Exception {
		// 1~25번째 실패 → 26번째 성공 → 27번째 실패 → 이후 성공.
		// 연속 실패의 첫 번째만 스택과 함께 WARN, 10회째에 "still failing" WARN, 복구 시 INFO가 남아야 한다.
		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
				org.slf4j.LoggerFactory.getLogger(PeriodicLoopServiceTest.class.getName() + ".flood");
		logger.setLevel(Level.DEBUG);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		AtomicInteger calls = new AtomicInteger();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															int n = calls.incrementAndGet();
															if ( n <= 25 || n == 27 ) {
																throw new IllegalStateException("boom-" + n);
															}
														})
														.fixedDelay(Duration.ofMillis(1))
														.stopOnError(false)
														.build();
		svc.setLogger(logger);
		svc.startAsync().awaitRunning();
		try {
			while ( calls.get() < 30 ) {
				Thread.sleep(1);
			}
		}
		finally {
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
			logger.detachAppender(appender);
		}

		List<ILoggingEvent> events = appender.list;
		long warnWithStack = events.stream().filter(ev -> ev.getLevel() == Level.WARN && ev.getThrowableProxy() != null).count();
		long stillFailing = events.stream().filter(ev -> ev.getLevel() == Level.WARN
														&& ev.getFormattedMessage().contains("still failing")).count();
		List<String> recovered = events.stream().filter(ev -> ev.getLevel() == Level.INFO
															&& ev.getFormattedMessage().contains("recovered after"))
											.map(ILoggingEvent::getFormattedMessage).toList();

		Assertions.assertEquals(2, warnWithStack, "one stack-trace WARN per failure streak");
		Assertions.assertEquals(1, stillFailing, "a reminder WARN at the 10th consecutive failure only");
		Assertions.assertEquals(2, recovered.size(), recovered.toString());
		Assertions.assertTrue(recovered.get(0).contains("after 25 consecutive"), recovered.get(0));
		Assertions.assertTrue(recovered.get(1).contains("after 1 consecutive"), recovered.get(1));
		Assertions.assertEquals(0, svc.getConsecutiveFailureCount());
		Assertions.assertEquals(State.TERMINATED, svc.state());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void wrapped_interrupt_is_treated_as_stop() throws Exception {
		// 작업이 인터럽트를 RuntimeInterruptedException으로 감싸 던져도 실패가 아니라 중지(TERMINATED)여야 한다.
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															throw new RuntimeInterruptedException(new InterruptedException("wrapped"));
														})
														.fixedDelay(Duration.ofMillis(10))
														.build();				// stopOnError(true) 기본값
		svc.startAsync().awaitTerminated(2, TimeUnit.SECONDS);
		Assertions.assertEquals(State.TERMINATED, svc.state());
		Assertions.assertTrue(svc.isStopRequested());
		Assertions.assertEquals(1, svc.getIterationCount());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void exception_thrown_while_interrupted_is_treated_as_stop() throws Exception {
		// 작업이 인터럽트를 다른 예외로 바꾸어 던졌더라도 loop 쓰레드가 인터럽트된 상태이면 중지여야 한다.
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															Thread.currentThread().interrupt();
															throw new IllegalStateException("converted interrupt");
														})
														.fixedDelay(Duration.ofMillis(10))
														.build();
		svc.startAsync().awaitTerminated(2, TimeUnit.SECONDS);
		Assertions.assertEquals(State.TERMINATED, svc.state());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void interrupted_cause_from_another_thread_is_a_failure() {
		// 원인 사슬에 InterruptedException이 있는 것만으로는 중지로 보지 않는다(다른 쓰레드의 작업이 인터럽트된 경우).
		ExecutionException failure = new ExecutionException(new InterruptedException("remote task interrupted"));
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> { throw failure; })
														.fixedDelay(Duration.ofMillis(10))
														.build();
		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitTerminated(2, TimeUnit.SECONDS));
		Assertions.assertEquals(State.FAILED, svc.state());
		Assertions.assertSame(failure, svc.failureCause());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void loop_context_reports_iteration_numbers() throws Exception {
		List<Long> iterations = new CopyOnWriteArrayList<>();
		PeriodicLoopService svc = PeriodicLoopService.builder(ctx -> iterations.add(ctx.iteration()))
														.fixedDelay(Duration.ofMillis(5))
														.build();
		svc.startAsync().awaitRunning();
		try {
			while ( iterations.size() < 3 ) {
				Thread.sleep(5);
			}
		}
		finally {
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		}
		Assertions.assertEquals(List.of(0L, 1L, 2L), iterations.subList(0, 3));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void long_task_can_react_to_stop_through_loop_context() throws Exception {
		// 끝나지 않는 작업이라도 LoopContext.isStopRequested()를 확인하면 stopAsync()에 바로 반응할 수 있어야 한다.
		AtomicBoolean entered = new AtomicBoolean();
		AtomicBoolean sawStop = new AtomicBoolean();
		PeriodicLoopService svc = PeriodicLoopService.builder()
														.task(ctx -> {
															entered.set(true);
															while ( !ctx.isStopRequested() ) {
																Thread.sleep(5);
															}
															sawStop.set(true);
														})
														.fixedDelay(Duration.ofSeconds(10))
														.build();
		svc.startAsync().awaitRunning();
		while ( !entered.get() ) {
			Thread.sleep(5);
		}

		long stopStarted = System.nanoTime();
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted);
		Assertions.assertTrue(sawStop.get(), "task must observe the stop request");
		Assertions.assertTrue(stopMillis < 1_000, "stop took " + stopMillis + "ms");
		Assertions.assertEquals(State.TERMINATED, svc.state());
	}

	@Test
	public void error_policy_basics() {
		Assertions.assertEquals(1, PeriodicLoopService.ErrorPolicy.STOP.getMaxConsecutiveFailures());
		Assertions.assertEquals(Long.MAX_VALUE, PeriodicLoopService.ErrorPolicy.CONTINUE.getMaxConsecutiveFailures());
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.STOP, PeriodicLoopService.ErrorPolicy.failAfterConsecutiveFailures(1));
		Assertions.assertEquals("failAfterConsecutiveFailures(5)", PeriodicLoopService.ErrorPolicy.failAfterConsecutiveFailures(5).toString());
		Assertions.assertThrows(IllegalArgumentException.class, () -> PeriodicLoopService.ErrorPolicy.failAfterConsecutiveFailures(0));

		// 기본값은 STOP, stopOnError(boolean)는 STOP/CONTINUE의 별칭이다.
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.STOP,
								PeriodicLoopService.builder(() -> { }).build().getErrorPolicy());
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.CONTINUE,
								PeriodicLoopService.builder(() -> { }).stopOnError(false).build().getErrorPolicy());
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.STOP,
								PeriodicLoopService.builder(() -> { }).stopOnError(true).build().getErrorPolicy());
		Assertions.assertThrows(IllegalArgumentException.class, () -> PeriodicLoopService.builder().errorPolicy(null));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void failAfterConsecutiveFailures_fails_service_at_the_limit() {
		// 계속 실패하는 작업은 연속 3회째 실패에서 FAILED가 되고, 실패 원인은 마지막 예외여야 한다.
		AtomicInteger calls = new AtomicInteger();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															throw new IllegalStateException("boom-" + calls.incrementAndGet());
														})
														.fixedDelay(Duration.ofMillis(1))
														.errorPolicy(PeriodicLoopService.ErrorPolicy.failAfterConsecutiveFailures(3))
														.build();
		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitTerminated(2, TimeUnit.SECONDS));
		Assertions.assertEquals(State.FAILED, svc.state());
		Assertions.assertEquals("boom-3", svc.failureCause().getMessage());
		Assertions.assertEquals(3, svc.getIterationCount());
		Assertions.assertEquals(3, svc.getConsecutiveFailureCount());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void failAfterConsecutiveFailures_resets_on_success() throws Exception {
		// 실패, 실패, 성공이 반복되면 연속 실패가 3회에 이르지 않으므로 서비스가 계속 수행되어야 한다.
		AtomicInteger calls = new AtomicInteger();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															if ( calls.incrementAndGet() % 3 != 0 ) {
																throw new IllegalStateException("transient");
															}
														})
														.fixedDelay(Duration.ofMillis(1))
														.errorPolicy(PeriodicLoopService.ErrorPolicy.failAfterConsecutiveFailures(3))
														.build();
		svc.startAsync().awaitRunning();
		try {
			while ( calls.get() < 15 ) {
				Thread.sleep(2);
			}
			Assertions.assertEquals(State.RUNNING, svc.state());
		}
		finally {
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		}
		Assertions.assertEquals(State.TERMINATED, svc.state());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void cleanup_runs_on_stop() throws Exception {
		AtomicInteger cleaned = new AtomicInteger();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> { })
														.fixedDelay(Duration.ofSeconds(10))
														.cleanup(cleaned::incrementAndGet)
														.build();
		svc.startAsync().awaitRunning();
		svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		Assertions.assertEquals(1, cleaned.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void subclass_constructor_with_name_and_interval() throws Exception {
		AtomicInteger iterations = new AtomicInteger();
		class Counter extends PeriodicLoopService {
			Counter(IntervalMode mode) {
				super("counter", Duration.ofMillis(5), mode);
			}
			@Override
			protected void performPeriodicAction(long loopIndex) {
				iterations.incrementAndGet();
			}
		}

		Counter svc = new Counter(IntervalMode.FIXED_DELAY);
		Assertions.assertEquals("counter", svc.serviceName());
		Assertions.assertEquals(Duration.ofMillis(5), svc.getLoopInterval());
		Assertions.assertEquals(IntervalMode.FIXED_DELAY, svc.getIntervalMode());
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.STOP, svc.getErrorPolicy());
		Assertions.assertTrue(svc.isDaemon());
		Assertions.assertEquals(IntervalMode.FIXED_RATE, new Counter(IntervalMode.FIXED_RATE).getIntervalMode());

		svc.startAsync().awaitRunning();
		try {
			while ( iterations.get() < 3 ) {
				Thread.sleep(5);
			}
		}
		finally {
			svc.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
		}
		Assertions.assertEquals(State.TERMINATED, svc.state());

		Assertions.assertThrows(IllegalArgumentException.class, () -> new Counter(null));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void cleanup_runs_with_interrupt_cleared_after_interrupt_stop() throws Exception {
		// 인터럽트로 loop이 끝나도 정리 작업은 인터럽트 상태가 지워진 채 수행되어 블로킹 호출을 끝까지 마쳐야 한다.
		AtomicBoolean interruptedAtCleanup = new AtomicBoolean(true);
		AtomicBoolean cleanupCompleted = new AtomicBoolean();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> { throw new InterruptedException("stop"); })
														.fixedDelay(Duration.ofMillis(10))
														.cleanup(() -> {
															interruptedAtCleanup.set(Thread.currentThread().isInterrupted());
															Thread.sleep(50);			// 정리 작업의 블로킹 호출
															cleanupCompleted.set(true);
														})
														.build();
		svc.startAsync().awaitTerminated(2, TimeUnit.SECONDS);

		Assertions.assertEquals(State.TERMINATED, svc.state());
		Assertions.assertFalse(interruptedAtCleanup.get(), "cleanup must run with the interrupt flag cleared");
		Assertions.assertTrue(cleanupCompleted.get(), "blocking call in cleanup was interrupted");
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void fixedRate_catch_up_still_detects_interrupt() throws Exception {
		// 주기(1ms)보다 오래 걸린 작업(5ms)이 예외 없이 인터럽트 플래그만 설정하고 반환하면, 대기 없이 연달아 실행되는
		// catch-up 구간이더라도 다음 iteration을 시작하지 않고 중지되어야 한다.
		AtomicInteger calls = new AtomicInteger();
		PeriodicLoopService svc = PeriodicLoopService.builder(() -> {
															if ( calls.incrementAndGet() == 1 ) {
																Thread.sleep(5);
																Thread.currentThread().interrupt();
															}
														})
														.fixedRate(Duration.ofMillis(1))
														.build();
		svc.startAsync().awaitTerminated(2, TimeUnit.SECONDS);

		Assertions.assertEquals(State.TERMINATED, svc.state());
		Assertions.assertEquals(1, calls.get(), "no iteration may start after the interrupt");
		Assertions.assertTrue(svc.isStopRequested());
	}

	@Test
	public void build_requires_a_task_but_subclass_builder_does_not() throws Exception {
		// 작업 없이 build()하면 곧바로 실패해야 한다.
		Assertions.assertThrows(IllegalStateException.class,
								() -> PeriodicLoopService.builder().fixedDelay(Duration.ofSeconds(1)).build());

		// performPeriodicAction()을 재정의하는 하위 클래스는 작업 없는 빌더를 super(builder)로 넘길 수 있다.
		class Sub extends PeriodicLoopService {
			Sub() {
				super(PeriodicLoopService.builder().name("sub").fixedDelay(Duration.ofSeconds(1))
										.errorPolicy(PeriodicLoopService.ErrorPolicy.CONTINUE));
			}
			@Override
			protected void performPeriodicAction(long loopIndex) { }
		}
		Sub sub = new Sub();
		Assertions.assertEquals("sub", sub.serviceName());
		Assertions.assertSame(PeriodicLoopService.ErrorPolicy.CONTINUE, sub.getErrorPolicy());
	}
}
