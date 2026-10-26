package utils.async;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


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
	@SuppressWarnings("deprecation")
	public void deprecated_interval_and_delay_delegate() {
		PeriodicLoopService rate = PeriodicLoopService.builder(() -> { }).interval(Duration.ofMillis(100)).build();
		Assertions.assertEquals(IntervalMode.FIXED_RATE, rate.getIntervalMode());
		Assertions.assertTrue(rate.isCumulativeInterval());

		PeriodicLoopService delay = PeriodicLoopService.builder(() -> { }).delay(Duration.ofMillis(100)).build();
		Assertions.assertEquals(IntervalMode.FIXED_DELAY, delay.getIntervalMode());
		Assertions.assertFalse(delay.isCumulativeInterval());
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
}
