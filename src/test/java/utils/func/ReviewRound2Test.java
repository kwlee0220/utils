package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.stream.FStream;


/**
 * 두 번째 utils.func 검토에서 찾은 결함의 회귀 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ReviewRound2Test {
	/**
	 * 다른 스레드가 set()·invalidate()를 반복해도 Lazy.get()은 null을 돌려주지 않는다.
	 */
	@Test
	@Timeout(30)
	public void lazyGetNeverReturnsNullUnderConcurrentInvalidate() throws Exception {
		Lazy<String> lazy = Lazy.of(() -> "loaded");
		AtomicBoolean stop = new AtomicBoolean();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread mutator = new Thread(() -> {
			while ( !stop.get() ) {
				lazy.set("set");
				lazy.invalidate();
			}
		});
		Thread reader = new Thread(() -> {
			try {
				for ( int i = 0; i < 500_000; ++i ) {
					assertNotNull(lazy.get());
				}
			}
			catch ( Throwable e ) {
				failure.set(e);
			}
		});
		mutator.start();
		reader.start();
		reader.join();
		stop.set(true);
		mutator.join();
		if ( failure.get() != null ) {
			throw new AssertionError(failure.get());
		}
	}

	/**
	 * 적재 중에 호출한 TimedLazy.set()의 값을, 진행 중이던 적재 결과가 덮어쓰지 않는다.
	 */
	@Test
	@Timeout(30)
	public void timedLazySetDuringReloadWins() throws Exception {
		CountDownLatch loading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TimedLazy<String> lazy = TimedLazy.of(Duration.ofHours(1), () -> {
			loading.countDown();
			try { release.await(); } catch ( InterruptedException ignored ) { }
			return "old";
		});

		Thread loader = new Thread(lazy::get);
		loader.start();
		assertTrue(loading.await(10, TimeUnit.SECONDS));

		Thread setter = new Thread(() -> lazy.set("new"));
		setter.start();
		Thread.sleep(200);		// setter가 잠금을 기다리는 상태가 되도록
		release.countDown();
		loader.join();
		setter.join();

		assertEquals("new", lazy.peekOrNull());
	}

	@Test
	public void causelessFailureConvertsToTry() {
		Try<Object> t = Result.failure(null).toTry();
		assertTrue(t.isFailed());
		assertTrue(t.getCause() instanceof IllegalStateException);
	}

	@Test
	public void fOptionStreamRejectsNextAfterClose() throws Exception {
		FStream<Integer> strm = FOption.of(1).fstream();
		strm.close();
		assertThrows(IllegalStateException.class, strm::next);

		FStream<Object> nullValue = FOption.of(null).fstream();
		assertTrue(nullValue.next().isPresent());		// null 값을 가진 존재 상태는 그대로 나온다
	}

	@Test
	public void flatMapRejectsNullContainer() {
		assertThrows(IllegalStateException.class, () -> FOption.of(1).flatMap(v -> null));
		assertThrows(IllegalStateException.class, () -> Either.left(1).flatMapLeft(v -> null));

		Try<Object> t = Try.success(1).flatMap(v -> null);
		assertTrue(t.isFailed());
		assertTrue(t.getCause() instanceof IllegalStateException);

		Result<Object> r = Result.success(1).flatMap(v -> null);
		assertTrue(r.isFailed());
		assertTrue(r.getCause() instanceof IllegalStateException);

		// 값이 null인 컨테이너는 정상이다.
		assertTrue(Try.success(1).flatMap(v -> Try.success(null)).isSuccessful());
	}

	@Test
	public void foldRightWorksOnLinkedList() {
		List<String> list = new LinkedList<>(List.of("a", "b", "c"));
		assertEquals("abc", Funcs.foldRight(list, "", (t, acc) -> t + acc));
		assertSame("", Funcs.foldRight(new LinkedList<String>(), "", (t, acc) -> t + acc));
	}
}
