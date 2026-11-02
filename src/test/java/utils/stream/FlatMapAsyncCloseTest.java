package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@code keepOrder=true}인 {@link FStream#flatMapAsync}를 중간에 닫으면, 미리 만들어 두었지만 읽지 않은 하위
 * 스트림도 닫히는지 검증한다(review #26).
 * <p>
 * 2026-11-02 이전에는 출력 채널에 쌓여 있거나 닫은 뒤에 끝난 mapper의 하위 스트림이 닫히지 않았다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class FlatMapAsyncCloseTest {
	private static final int WORKERS = 4;

	private static AsyncExecutionOptions keepOrder() {
		return AsyncExecutionOptions.create().setKeepOrder(true).setWorkerCount(WORKERS);
	}

	private static void awaitTrue(BooleanSupplier cond) throws InterruptedException {
		long due = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while ( !cond.getAsBoolean() && System.nanoTime() < due ) {
			Thread.sleep(10);
		}
	}

	@Test
	public void closesFinishedButUnreadSubStreams() throws Exception {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger finished = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();

		FStream<Integer> stream = FStream.range(0, 100).flatMapAsync(i -> {
			created.incrementAndGet();
			try {
				return FStream.of(i).onClose(closed::incrementAndGet);
			}
			finally {
				finished.incrementAndGet();
			}
		}, keepOrder());

		assertEquals(0, (int)stream.next().get());
		// 미리 시작된 mapper들이 모두 끝나 결과가 출력 채널에 쌓일 때까지 기다린다.
		awaitTrue(() -> finished.get() == created.get() && created.get() > 1);
		assertTrue(created.get() > 1, "created=" + created);
		stream.close();

		awaitTrue(() -> closed.get() == created.get());
		assertEquals(created.get(), closed.get());
	}

	@Test
	public void closesSubStreamsCreatedAfterClose() throws Exception {
		AtomicInteger created = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		AtomicInteger finished = new AtomicInteger();
		CountDownLatch release = new CountDownLatch(1);

		FStream<Integer> stream = FStream.range(0, 100).flatMapAsync(i -> {
			created.incrementAndGet();
			try {
				if ( i > 0 ) {
					// 닫힌 뒤에 하위 스트림을 만들도록 붙잡아 둔다.
					release.await();
				}
				return FStream.of(i).onClose(closed::incrementAndGet);
			}
			catch ( InterruptedException e ) {
				throw new IllegalStateException(e);
			}
			finally {
				finished.incrementAndGet();
			}
		}, keepOrder());

		assertEquals(0, (int)stream.next().get());
		stream.close();
		int createdAtClose = created.get();
		release.countDown();

		awaitTrue(() -> finished.get() == created.get() && closed.get() == created.get());
		assertTrue(createdAtClose > 1, "created=" + createdAtClose);
		assertEquals(created.get(), closed.get());
	}
}
