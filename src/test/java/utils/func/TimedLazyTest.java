package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link TimedLazy} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimedLazyTest {
	/**
	 * 만료된 값을 여러 스레드가 동시에 읽어도 supplier는 한 번만 호출되고, 모두 같은 새 값을 받는다.
	 */
	@Test
	@Timeout(30)
	public void concurrentGetAfterExpiryReloadsOnce() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		TimedLazy<Integer> lazy = TimedLazy.of(Duration.ofMillis(50), () -> {
			int n = calls.incrementAndGet();
			try { Thread.sleep(200); } catch ( InterruptedException ignored ) { }
			return n;
		});
		assertEquals(1, lazy.get());
		Thread.sleep(100);		// TTL 경과

		int nthreads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(nthreads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for ( int i = 0; i < nthreads; ++i ) {
				results.add(executor.submit(() -> { start.await(); return lazy.get(); }));
			}
			start.countDown();
			for ( Future<Integer> r: results ) {
				assertEquals(2, r.get(10, TimeUnit.SECONDS));
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertEquals(2, calls.get());
	}

	@Test
	public void reloadAlwaysCallsSupplier() {
		AtomicInteger calls = new AtomicInteger();
		TimedLazy<Integer> lazy = TimedLazy.of(Duration.ofHours(1), calls::incrementAndGet);
		assertEquals(1, lazy.get());
		assertEquals(1, lazy.get());
		assertEquals(2, lazy.reload());
	}

	/**
	 * {@code peek()}은 {@link FOption}을 돌려주며, 적재된 null 값과 "적재 안 됨"을 구분한다.
	 */
	@Test
	public void peekDistinguishesLoadedNullFromUnloaded() {
		TimedLazy<String> lazy = TimedLazy.of(Duration.ofHours(1), () -> null);
		assertFalse(lazy.peek().isPresent());
		assertNull(lazy.get());
		FOption<String> peeked = lazy.peek();
		assertTrue(peeked.isPresent());
		assertNull(peeked.get());
		assertNull(lazy.peekOrNull());
	}
}
