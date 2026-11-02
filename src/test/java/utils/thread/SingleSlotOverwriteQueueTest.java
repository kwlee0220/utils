package utils.thread;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link SingleSlotOverwriteQueue}의 latest-wins 동작과 {@code BlockingQueue} 관례의 꺼내기 메소드를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class SingleSlotOverwriteQueueTest {
	@Test
	public void latest_item_wins() throws Exception {
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();
		queue.add("A");
		queue.add("B");

		Assertions.assertEquals("B", queue.peek());
		Assertions.assertEquals("B", queue.take());
		Assertions.assertNull(queue.peek());
	}

	@Test
	public void add_rejects_null() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new SingleSlotOverwriteQueue<String>().add(null));
	}

	@Test
	public void poll_does_not_block() {
		// Queue.poll()과 같이 비어 있으면 기다리지 않고 null을 돌려준다(2026-10-31 이전에는 블록했다).
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();
		Assertions.assertNull(queue.poll());

		queue.add("A");
		Assertions.assertEquals("A", queue.poll());
		Assertions.assertNull(queue.poll());
	}

	@Test
	public void timed_poll_returns_null_on_timeout() throws Exception {
		// BlockingQueue.poll(timeout)과 같이 시간 초과 시 null을 돌려준다(2026-10-31 이전에는 TimeoutException).
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();

		long started = System.nanoTime();
		Assertions.assertNull(queue.poll(Duration.ofMillis(100)));
		Assertions.assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(90));
		Assertions.assertNull(queue.poll(Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class, () -> queue.poll(Duration.ofMillis(-1)));
	}

	@Test
	public void take_and_timed_poll_wait_for_an_item() throws Exception {
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();

		CompletableFuture<String> taken = CompletableFuture.supplyAsync(() -> {
			try {
				return queue.take();
			}
			catch ( InterruptedException e ) {
				throw new IllegalStateException(e);
			}
		});
		Thread.sleep(100);
		Assertions.assertFalse(taken.isDone(), "take() must block while empty");
		queue.add("A");
		Assertions.assertEquals("A", taken.get(2, TimeUnit.SECONDS));

		CompletableFuture<String> polled = CompletableFuture.supplyAsync(() -> {
			try {
				return queue.poll(Duration.ofSeconds(5));
			}
			catch ( InterruptedException e ) {
				throw new IllegalStateException(e);
			}
		});
		Thread.sleep(100);
		queue.add("B");
		Assertions.assertEquals("B", polled.get(2, TimeUnit.SECONDS));
	}

	@Test
	public void take_is_interruptible() throws Exception {
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();
		CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
		Thread thread = new Thread(() -> {
			try {
				queue.take();
				interrupted.complete(false);
			}
			catch ( InterruptedException e ) {
				interrupted.complete(true);
			}
		});
		thread.start();
		Thread.sleep(100);
		thread.interrupt();

		Assertions.assertTrue(interrupted.get(2, TimeUnit.SECONDS));
	}

	@Test
	public void interrupted_thread_does_not_consume_item() throws Exception {
		// 이미 인터럽트된 쓰레드의 take()/poll(Duration)은 항목이 있어도 꺼내지 않고 InterruptedException을 던진다
		// (JDK BlockingQueue와 같은 정책). 2026-10-31 이전에는 인터럽트를 무시하고 꺼냈다.
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();
		queue.add("A");

		Thread.currentThread().interrupt();
		Assertions.assertThrows(InterruptedException.class, queue::take);
		Assertions.assertFalse(Thread.currentThread().isInterrupted(), "interrupt flag must be cleared");
		Assertions.assertEquals("A", queue.peek(), "item must remain");

		Thread.currentThread().interrupt();
		Assertions.assertThrows(InterruptedException.class, () -> queue.poll(Duration.ofSeconds(1)));
		Assertions.assertFalse(Thread.currentThread().isInterrupted());
		Assertions.assertEquals("A", queue.peek());

		// 기다리지 않는 poll()은 인터럽트를 확인하지 않는다(Queue.poll()과 같음). 플래그도 그대로 둔다.
		Thread.currentThread().interrupt();
		try {
			Assertions.assertEquals("A", queue.poll());
			Assertions.assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	public void timed_poll_rejects_null_and_saturates_huge_timeout() throws Exception {
		SingleSlotOverwriteQueue<String> queue = new SingleSlotOverwriteQueue<>();
		Assertions.assertThrows(IllegalArgumentException.class, () -> queue.poll(null));

		queue.add("A");
		Assertions.assertEquals("A", queue.poll(java.time.temporal.ChronoUnit.FOREVER.getDuration()));
	}
}
