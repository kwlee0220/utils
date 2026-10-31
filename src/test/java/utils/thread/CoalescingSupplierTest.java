package utils.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.func.CheckedSupplier;


/**
 * {@link CoalescingSupplier}가 겹치는 호출에 진행 중인 생성 결과를 공유하는지 검증한다.
 * <p>
 * 대기자가 진행 중인 생성에 합류했는지는 {@link CoalescingSupplier#getWaitingCount()}로 확인한다
 * (시간 경과에 기대지 않는다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class CoalescingSupplierTest {
	/** {@code release}가 열릴 때까지 막혀 있다가 호출 순번을 돌려주는 생성 함수. */
	private static final class BlockingSource implements CheckedSupplier<Integer> {
		final AtomicInteger m_calls = new AtomicInteger();
		final CountDownLatch m_started = new CountDownLatch(1);
		volatile CountDownLatch m_release = new CountDownLatch(1);

		@Override
		public Integer get() throws Exception {
			int call = m_calls.incrementAndGet();
			m_started.countDown();
			m_release.await();
			return call;
		}
	}

	/** 별도 스레드에서 실행 중인 호출. */
	private record Call<T>(Thread thread, CompletableFuture<T> result) { }

	private static <T> Call<T> async(CheckedSupplier<T> call) {
		CompletableFuture<T> future = new CompletableFuture<>();
		Thread thread = new Thread(() -> {
			try {
				future.complete(call.get());
			}
			catch ( Throwable e ) {
				future.completeExceptionally(e);
			}
		});
		thread.setDaemon(true);
		thread.start();
		return new Call<>(thread, future);
	}

	/** 진행 중인 생성을 기다리는 호출이 {@code count}개가 될 때까지 기다린다. */
	private static void awaitWaiters(CoalescingSupplier<?> supplier, int count) throws InterruptedException {
		long due = System.currentTimeMillis() + 5000;
		while ( supplier.getWaitingCount() < count ) {
			if ( System.currentTimeMillis() > due ) {
				throw new AssertionError("waiters did not join: expected=" + count + ", actual="
										+ supplier.getWaitingCount());
			}
			Thread.sleep(5);
		}
	}

	/** 호출이 던진 예외(별도 스레드에서 던진 것)를 꺼낸다. */
	private static Throwable failureOf(Call<?> call) {
		return assertThrows(CompletionException.class, () -> call.result().join()).getCause();
	}

	@Test
	public void concurrent_callers_wait_for_and_share_the_in_flight_result_by_default() throws Exception {
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> producer = async(supplier);
		source.m_started.await();
		Call<Integer> waiter1 = async(supplier);
		Call<Integer> waiter2 = async(supplier);
		awaitWaiters(supplier, 2);

		source.m_release.countDown();
		assertEquals(1, producer.result().get(5, TimeUnit.SECONDS));
		assertEquals(1, waiter1.result().get(5, TimeUnit.SECONDS));
		assertEquals(1, waiter2.result().get(5, TimeUnit.SECONDS));
		assertEquals(1, source.m_calls.get());
	}

	@Test
	public void non_overlapping_calls_produce_again() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(calls::incrementAndGet);

		assertEquals(1, supplier.get());
		assertEquals(2, supplier.get());
	}

	@Test
	public void a_failure_is_shared_with_waiters_and_the_next_call_produces_again() throws Exception {
		IOException failure = new IOException("down");
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		CoalescingSupplier<String> supplier = new CoalescingSupplier<>(() -> {
			if ( calls.incrementAndGet() == 1 ) {
				started.countDown();
				release.await();
				throw failure;
			}
			return "recovered";
		});

		Call<String> producer = async(supplier);
		started.await();
		Call<String> waiter = async(supplier);
		awaitWaiters(supplier, 1);
		release.countDown();

		// 생성한 호출과 기다린 호출 모두 원인이 같은 ExecutionException을 받는다.
		Throwable e1 = failureOf(producer);
		Throwable e2 = failureOf(waiter);
		assertTrue(e1 instanceof ExecutionException, "" + e1);
		assertTrue(e2 instanceof ExecutionException, "" + e2);
		assertSame(failure, e1.getCause());
		assertSame(failure, e2.getCause());

		// 실패한 결과는 남지 않는다. 다음 호출은 새로 생성한다.
		assertEquals("recovered", supplier.get());
	}

	@Test
	public void a_cancellation_from_the_source_reaches_every_caller_as_execution_exception() throws Exception {
		// CompletableFuture.get()은 CancellationException만 감싸지 않으므로, 대기자에게도 같은 형태로 맞춘다.
		CancellationException cancellation = new CancellationException("cancelled by the source");
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CoalescingSupplier<String> supplier = new CoalescingSupplier<>(() -> {
			started.countDown();
			release.await();
			throw cancellation;
		});

		Call<String> producer = async(supplier);
		started.await();
		Call<String> waiter = async(supplier);
		awaitWaiters(supplier, 1);
		release.countDown();

		Throwable e1 = failureOf(producer);
		Throwable e2 = failureOf(waiter);
		assertTrue(e1 instanceof ExecutionException, "" + e1);
		assertTrue(e2 instanceof ExecutionException, "" + e2);
		assertSame(cancellation, e1.getCause());
		assertSame(cancellation, e2.getCause());
	}

	@Test
	public void a_null_result_is_a_normal_result_and_shared() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CoalescingSupplier<String> supplier = new CoalescingSupplier<>(() -> {
			started.countDown();
			release.await();
			return null;
		});

		Call<String> producer = async(supplier);
		started.await();
		Call<String> waiter = async(supplier);
		awaitWaiters(supplier, 1);
		release.countDown();

		assertNull(producer.result().get(5, TimeUnit.SECONDS));
		assertNull(waiter.result().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void join_timeout_applies_only_to_waiters() throws Exception {
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> producer = async(() -> supplier.get(Duration.ofMillis(50)));
		source.m_started.await();
		assertThrows(TimeoutException.class, () -> supplier.get(Duration.ofMillis(200)));

		// 대기를 포기해도 진행 중인 생성은 계속되고, 직접 생성하는 호출은 joinTimeout의 제한을 받지 않는다.
		Thread.sleep(100);
		assertFalse(producer.result().isDone());
		source.m_release.countDown();
		assertEquals(1, producer.result().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void a_join_timeout_beyond_the_nanosecond_range_waits_without_overflow() throws Exception {
		// Duration.toNanos()는 이 값에서 ArithmeticException을 던진다. 사실상 무제한 대기로 처리해야 한다.
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> producer = async(supplier);
		source.m_started.await();
		Call<Integer> waiter = async(() -> supplier.get(Duration.ofSeconds(Long.MAX_VALUE)));
		awaitWaiters(supplier, 1);
		source.m_release.countDown();

		assertEquals(1, producer.result().get(5, TimeUnit.SECONDS));
		assertEquals(1, waiter.result().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void a_waiter_receives_the_production_it_joined_even_if_a_new_one_starts() throws Exception {
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> first = async(supplier);
		source.m_started.await();
		Call<Integer> waiter = async(supplier);
		awaitWaiters(supplier, 1);

		CountDownLatch firstRelease = source.m_release;
		source.m_release = new CountDownLatch(1);		// 두 번째 생성은 막아 둔다
		firstRelease.countDown();
		assertEquals(1, first.result().get(5, TimeUnit.SECONDS));
		Call<Integer> second = async(supplier);

		assertEquals(1, waiter.result().get(5, TimeUnit.SECONDS));
		source.m_release.countDown();
		assertEquals(2, second.result().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void an_interrupted_waiter_gives_up_without_affecting_the_production() throws Exception {
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> producer = async(supplier);
		source.m_started.await();
		Call<Integer> waiter = async(supplier);
		awaitWaiters(supplier, 1);

		waiter.thread().interrupt();
		assertTrue(failureOf(waiter) instanceof InterruptedException);

		source.m_release.countDown();
		assertEquals(1, producer.result().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void an_interrupted_producer_gets_interrupted_exception_and_waiters_get_execution_exception()
		throws Exception {
		BlockingSource source = new BlockingSource();
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(source);

		Call<Integer> producer = async(supplier);
		source.m_started.await();
		Call<Integer> waiter = async(supplier);
		awaitWaiters(supplier, 1);

		producer.thread().interrupt();		// 생성 함수의 대기(latch)가 InterruptedException을 던진다

		assertTrue(failureOf(producer) instanceof InterruptedException);
		Throwable waiterFailure = failureOf(waiter);
		assertTrue(waiterFailure instanceof ExecutionException, "" + waiterFailure);
		assertTrue(waiterFailure.getCause() instanceof InterruptedException, "" + waiterFailure.getCause());
	}

	@Test
	public void a_reentrant_call_from_the_source_is_rejected_instead_of_waiting_forever() throws Exception {
		AtomicReference<CoalescingSupplier<String>> self = new AtomicReference<>();
		CoalescingSupplier<String> supplier = new CoalescingSupplier<>(() -> self.get().get());
		self.set(supplier);

		ExecutionException e = assertThrows(ExecutionException.class, supplier::get);
		assertTrue(e.getCause() instanceof IllegalStateException, "" + e.getCause());

		// 거부된 뒤에도 정상적으로 다시 쓸 수 있다.
		assertEquals(0, supplier.getWaitingCount());
	}

	@Test
	public void negative_join_timeout_is_rejected() {
		CoalescingSupplier<Integer> supplier = new CoalescingSupplier<>(() -> 1);

		assertThrows(IllegalArgumentException.class, () -> supplier.get(Duration.ofMillis(-1)));
	}
}
