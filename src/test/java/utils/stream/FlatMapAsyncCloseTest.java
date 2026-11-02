package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@code keepOrder=true}인 {@link FStream#flatMapAsync}를 중간에 닫거나 소비자가 인터럽트되어도, 미리 만들어
 * 두었지만 읽지 않은 하위 스트림과 나중에 만들어진 하위 스트림이 닫히는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class FlatMapAsyncCloseTest {
	private static final int WORKERS = 4;

	private static AsyncExecutionOptions keepOrder() {
		return AsyncExecutionOptions.create().withKeepOrder(true).withWorkerCount(WORKERS);
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

	@Test
	public void closesSubStreamCreatedAfterConsumerInterrupt() throws Exception {
		CountDownLatch mapperStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean closed = new AtomicBoolean(false);

		FStream<Integer> stream = FStream.of(1).flatMapAsync(i -> {
			mapperStarted.countDown();
			release.await();
			return FStream.of(i).onClose(() -> closed.set(true));
		}, AsyncExecutionOptions.create().withKeepOrder(true).withWorkerCount(1));

		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread consumer = new Thread(() -> {
			try {
				stream.next();
			}
			catch ( Throwable e ) {
				failure.set(e);
			}
			finally {
				stream.closeQuietly();
			}
		});
		consumer.start();

		assertTrue(mapperStarted.await(5, TimeUnit.SECONDS));
		Thread.sleep(100);		// 소비자가 결과를 기다리기 시작하도록 둔다.
		consumer.interrupt();
		consumer.join(5000);
		assertInstanceOf(InterruptedException.class, failure.get());

		// 매핑 함수가 소비자 인터럽트 뒤에 하위 스트림을 만든다.
		release.countDown();
		awaitTrue(closed::get);
		assertTrue(closed.get(), "the sub-stream created after the interrupt must be closed");
	}
}
