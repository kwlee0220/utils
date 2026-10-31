package utils.thread;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;
import utils.thread.CallerRunsSerialExecutor.Invocation;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CallerRunsSerialExecutorTest {
	private CallerRunsSerialExecutor m_executor;

	@BeforeEach
	public void setup() {
		m_executor = new CallerRunsSerialExecutor();
	}

	// ---- 처리 중인 스레드가 없을 때 ----

	@Test
	public void execute_runs_in_caller_thread_when_idle() {
		AtomicReference<Thread> runner = new AtomicReference<>();
		m_executor.execute(() -> runner.set(Thread.currentThread()));

		assertSame(Thread.currentThread(), runner.get());
		assertFalse(m_executor.isDispatcherThread());
		assertEquals(0, m_executor.getPendingTaskCount());
	}

	@Test
	public void submit_returns_completed_future_when_idle() throws Exception {
		CompletableFuture<String> future = m_executor.submit(() -> "done");

		assertTrue(future.isDone());
		assertEquals("done", future.get());
	}

	@Test
	public void submit_runnable_completes_with_null() throws Exception {
		AtomicBoolean ran = new AtomicBoolean();
		CompletableFuture<Void> future = m_executor.submit(() -> ran.set(true));

		assertTrue(ran.get());
		assertTrue(future.isDone());
		assertNull(future.get());
	}

	@Test
	public void submit_runnable_failure_completes_exceptionally() {
		IllegalStateException cause = new IllegalStateException("boom");
		Runnable task = () -> { throw cause; };
		CompletableFuture<Void> future = m_executor.submit(task);

		ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
		assertSame(cause, thrown.getCause());
	}

	@Test
	public void invokeOrDefer_returns_result_when_idle() {
		Invocation<Integer> inv = m_executor.invokeOrDefer(() -> 1 + 2);

		assertFalse(inv.isDeferred());
		assertTrue(inv.completion().isDone());
		assertEquals(3, inv.get());
	}

	@Test
	public void invokeOrDefer_returns_null_result() {
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> null);

		assertFalse(inv.isDeferred());
		assertNull(inv.get());
	}

	@Test
	public void dispatcher_thread_is_visible_inside_task() {
		AtomicBoolean inside = new AtomicBoolean();
		m_executor.execute(() -> inside.set(m_executor.isDispatcherThread()));

		assertTrue(inside.get());
	}

	@Test
	public void null_task_rejected() {
		assertThrows(NullPointerException.class, () -> m_executor.execute(null));
		assertThrows(NullPointerException.class, () -> m_executor.submit((java.util.concurrent.Callable<?>)null));
		assertThrows(NullPointerException.class, () -> m_executor.submit((Runnable)null));
		assertThrows(NullPointerException.class, () -> m_executor.invokeOrDefer(null));
		assertThrows(NullPointerException.class, () -> m_executor.runAsDispatcher(null));
	}

	// ---- 재진입 ----

	@Test
	public void task_submitted_inside_task_runs_after_current_task_on_same_thread() {
		List<String> order = new ArrayList<>();
		AtomicReference<Thread> innerRunner = new AtomicReference<>();
		m_executor.execute(() -> {
			order.add("outer-begin");
			m_executor.execute(() -> {
				order.add("inner");
				innerRunner.set(Thread.currentThread());
			});
			assertEquals(1, m_executor.getPendingTaskCount());
			order.add("outer-end");
		});

		assertEquals(List.of("outer-begin", "outer-end", "inner"), order);
		assertSame(Thread.currentThread(), innerRunner.get());
	}

	@Test
	public void invokeOrDefer_inside_task_is_deferred_and_delivers_result_later() throws Exception {
		List<String> order = new ArrayList<>();
		AtomicReference<Invocation<String>> inner = new AtomicReference<>();
		m_executor.execute(() -> {
			inner.set(m_executor.invokeOrDefer(() -> {
				order.add("inner");
				return "inner-result";
			}));
			assertFalse(inner.get().completion().isDone());
			order.add("outer-end");
		});

		assertTrue(inner.get().isDeferred());
		assertEquals(List.of("outer-end", "inner"), order);
		assertEquals("inner-result", inner.get().completion().get());
	}

	@Test
	public void deferred_invocation_delivers_failure_through_completion() {
		IllegalStateException cause = new IllegalStateException("deferred failure");
		AtomicReference<Invocation<Object>> inner = new AtomicReference<>();
		m_executor.execute(() -> inner.set(m_executor.invokeOrDefer(() -> { throw cause; })));

		ExecutionException thrown = assertThrows(ExecutionException.class, () -> inner.get().completion().get());
		assertSame(cause, thrown.getCause());
	}

	@Test
	public void get_on_deferred_invocation_rejected_even_after_completion() {
		AtomicReference<Invocation<String>> inner = new AtomicReference<>();
		m_executor.execute(() -> inner.set(m_executor.invokeOrDefer(() -> "x")));

		// 작업은 이미 끝났지만, 지연 여부는 호출 당시 값 그대로이고 get()은 계속 거부된다.
		assertTrue(inner.get().completion().isDone());
		assertTrue(inner.get().isDeferred());
		assertThrows(IllegalStateException.class, () -> inner.get().get());
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void invokeOrDefer_from_future_callback_on_dispatcher_does_not_deadlock() throws Exception {
		AtomicReference<Invocation<String>> callbackResult = new AtomicReference<>();
		AtomicBoolean lastRan = new AtomicBoolean();
		m_executor.execute(() -> {
			// 작업 안에서 넣은 작업의 future는 현재 작업이 끝난 뒤 dispatcher 스레드에서 완료된다.
			// 그 후속 작업은 작업 밖(dispatcher 스레드)에서 실행된다.
			m_executor.submit(() -> "second")
						.thenRun(() -> callbackResult.set(m_executor.invokeOrDefer(() -> {
							lastRan.set(true);
							return "third";
						})));
		});

		assertTrue(callbackResult.get().isDeferred());
		assertTrue(lastRan.get());
		assertEquals("third", callbackResult.get().completion().get());
	}

	// ---- 다른 스레드가 처리 중일 때 ----

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void invokeOrDefer_waits_for_other_dispatcher_and_task_runs_on_it() throws Exception {
		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> m_executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
		}));
		dispatcher.start();
		dispatching.await();

		AtomicReference<Thread> runner = new AtomicReference<>();
		CompletableFuture<Invocation<String>> result = CompletableFuture.supplyAsync(
															() -> m_executor.invokeOrDefer(() -> {
			runner.set(Thread.currentThread());
			return "ok";
		}));
		awaitPending(1);
		assertFalse(result.isDone());

		release.countDown();
		Invocation<String> inv = result.get();
		assertFalse(inv.isDeferred());
		assertEquals("ok", inv.get());
		assertSame(dispatcher, runner.get());
		dispatcher.join();
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void invokeOrDefer_delivers_original_exception_raised_on_other_dispatcher() throws Exception {
		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> m_executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
		}));
		dispatcher.start();
		dispatching.await();

		// 작업은 dispatcher 스레드에서 실패한다. 호출자는 그 원래 예외 객체를 받아야 한다.
		CompletionException original = new CompletionException("outer", new IllegalArgumentException("inner"));
		AtomicReference<Thread> runner = new AtomicReference<>();
		CompletableFuture<Invocation<Object>> result = CompletableFuture.supplyAsync(
															() -> m_executor.invokeOrDefer(() -> {
			runner.set(Thread.currentThread());
			throw original;
		}));
		awaitPending(1);

		release.countDown();
		Invocation<Object> inv = result.get();
		assertFalse(inv.isDeferred());
		assertSame(dispatcher, runner.get());
		assertSame(original, assertThrows(CompletionException.class, inv::get));
		dispatcher.join();
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void submit_returns_immediately_while_other_thread_dispatches() throws Exception {
		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> m_executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
		}));
		dispatcher.start();
		dispatching.await();

		CompletableFuture<String> future = m_executor.submit(() -> "later");
		assertFalse(future.isDone());
		assertEquals(1, m_executor.getPendingTaskCount());

		release.countDown();
		assertEquals("later", future.get());
		dispatcher.join();
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void invokeOrDefer_holding_outer_lock_is_deferred_while_other_thread_dispatches() throws Exception {
		Guard outerLock = Guard.create();
		CallerRunsSerialExecutor executor = new CallerRunsSerialExecutor(outerLock);

		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
			// 작업은 바깥 lock을 잡는다. 호출자가 lock을 쥔 채 기다렸다면 교착된다.
			outerLock.run(() -> { });
		}));
		dispatcher.start();
		dispatching.await();

		Invocation<String> inv;
		outerLock.lock();
		try {
			release.countDown();
			inv = executor.invokeOrDefer(() -> outerLock.get(() -> "deferred"));
			assertTrue(inv.isDeferred());
		}
		finally {
			outerLock.unlock();
		}

		assertEquals("deferred", inv.completion().get());
		dispatcher.join();
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void invokeOrDefer_holding_outer_lock_runs_directly_when_idle() {
		Guard outerLock = Guard.create();
		CallerRunsSerialExecutor executor = new CallerRunsSerialExecutor(outerLock);

		outerLock.lock();
		try {
			Invocation<String> inv = executor.invokeOrDefer(() -> "direct");
			assertFalse(inv.isDeferred());
			assertEquals("direct", inv.get());
		}
		finally {
			outerLock.unlock();
		}
	}

	// ---- 예외 ----

	@Test
	public void invocation_get_rethrows_runtime_exception_as_is() {
		IllegalStateException cause = new IllegalStateException("boom");
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> { throw cause; });

		assertTrue(inv.completion().isCompletedExceptionally());
		IllegalStateException thrown = assertThrows(IllegalStateException.class, inv::get);
		assertSame(cause, thrown);
	}

	@Test
	public void invocation_get_rethrows_completion_exception_thrown_by_task_as_is() {
		// 검토 의견 #1: 작업이 직접 던진 CompletionException을 풀지 않아야 한다.
		CompletionException original = new CompletionException("outer", new IllegalArgumentException("inner"));
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> { throw original; });

		CompletionException thrown = assertThrows(CompletionException.class, inv::get);
		assertSame(original, thrown);
	}

	@Test
	public void invocation_get_rethrows_causeless_completion_exception_as_is() {
		CompletionException original = new CompletionException("no cause", null);
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> { throw original; });

		assertSame(original, assertThrows(CompletionException.class, inv::get));
	}

	@Test
	public void invocation_get_wraps_checked_exception() {
		IOException cause = new IOException("io");
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> { throw cause; });

		RuntimeExecutionException thrown = assertThrows(RuntimeExecutionException.class, inv::get);
		assertSame(cause, thrown.getCause());
	}

	@Test
	public void invocation_get_rethrows_error() {
		AssertionError cause = new AssertionError("err");
		Invocation<Object> inv = m_executor.invokeOrDefer(() -> { throw cause; });

		assertSame(cause, assertThrows(AssertionError.class, inv::get));
	}

	@Test
	public void submit_completes_future_exceptionally() {
		IllegalArgumentException cause = new IllegalArgumentException("bad");
		CompletableFuture<Object> future = m_executor.submit(() -> { throw cause; });

		ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
		assertSame(cause, thrown.getCause());
	}

	@Test
	public void execute_failure_does_not_stop_following_tasks() {
		List<String> order = new ArrayList<>();
		m_executor.execute(() -> {
			m_executor.execute(() -> { throw new IllegalStateException("first fails"); });
			m_executor.execute(() -> { throw new AssertionError("second fails"); });
			m_executor.execute(() -> order.add("third"));
		});

		assertEquals(List.of("third"), order);
		assertFalse(m_executor.isDispatcherThread());
		assertEquals("next", m_executor.invokeOrDefer(() -> "next").get());
	}

	@Test
	public void interrupt_flag_is_preserved() {
		AtomicBoolean seen = new AtomicBoolean();
		Thread.currentThread().interrupt();
		try {
			m_executor.execute(() -> seen.set(Thread.currentThread().isInterrupted()));
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
		assertTrue(seen.get());
	}

	// ---- 취소 ----

	@Test
	public void task_cancelled_before_start_is_skipped() {
		AtomicBoolean ran = new AtomicBoolean();
		AtomicReference<CompletableFuture<String>> pending = new AtomicReference<>();
		AtomicBoolean cancelled = new AtomicBoolean();
		m_executor.execute(() -> {
			pending.set(m_executor.submit(() -> {
				ran.set(true);
				return "never";
			}));
			cancelled.set(pending.get().cancel(false));
		});

		assertTrue(cancelled.get());
		assertTrue(pending.get().isCancelled());
		assertFalse(ran.get());
	}

	@Test
	public void cancel_does_not_stop_running_task() {
		AtomicReference<CompletableFuture<String>> self = new AtomicReference<>();
		AtomicBoolean finished = new AtomicBoolean();
		AtomicReference<CompletableFuture<String>> future = new AtomicReference<>();
		m_executor.execute(() -> {
			future.set(m_executor.submit(() -> {
				self.get().cancel(true);
				finished.set(true);
				return "ignored";
			}));
			self.set(future.get());
		});

		assertTrue(finished.get());
		assertTrue(future.get().isCancelled());
	}

	// ---- runAsDispatcher ----

	@Test
	public void runAsDispatcher_runs_tasks_queued_meanwhile_afterwards() {
		List<String> order = new ArrayList<>();
		m_executor.runAsDispatcher(() -> {
			assertTrue(m_executor.isDispatcherThread());
			m_executor.execute(() -> order.add("queued"));
			order.add("dispatcher");
		});

		assertEquals(List.of("dispatcher", "queued"), order);
		assertFalse(m_executor.isDispatcherThread());
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void runAsDispatcher_waits_for_other_dispatcher() throws Exception {
		List<String> order = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> m_executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
			order.add("running");
		}));
		dispatcher.start();
		dispatching.await();

		CompletableFuture<Void> asDispatcher = CompletableFuture.runAsync(
												() -> m_executor.runAsDispatcher(() -> order.add("dispatcher")));
		Thread.sleep(100);
		assertFalse(asDispatcher.isDone());

		release.countDown();
		asDispatcher.get();
		dispatcher.join();
		assertEquals(List.of("running", "dispatcher"), order);
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void runAsDispatcher_interrupted_while_waiting_does_not_run_task() throws Exception {
		CountDownLatch dispatching = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread dispatcher = new Thread(() -> m_executor.execute(() -> {
			dispatching.countDown();
			awaitQuietly(release);
		}));
		dispatcher.start();
		dispatching.await();

		AtomicBoolean ran = new AtomicBoolean();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicBoolean interruptedAfter = new AtomicBoolean();
		Thread waiter = new Thread(() -> {
			try {
				m_executor.runAsDispatcher(() -> ran.set(true));
			}
			catch ( Throwable e ) {
				failure.set(e);
			}
			interruptedAfter.set(Thread.currentThread().isInterrupted());
		});
		waiter.start();
		while ( waiter.getState() != Thread.State.WAITING ) {
			Thread.sleep(5);
		}
		waiter.interrupt();
		waiter.join();

		assertTrue(failure.get() instanceof RuntimeInterruptedException, "failure=" + failure.get());
		assertTrue(interruptedAfter.get());
		assertFalse(ran.get());

		// 기다리던 스레드가 빠져도 dispatcher와 이후 작업은 정상이어야 한다.
		release.countDown();
		dispatcher.join();
		assertEquals("ok", m_executor.invokeOrDefer(() -> "ok").get());
	}

	@Test
	public void runAsDispatcher_inside_task_runs_inline() {
		List<String> order = new ArrayList<>();
		m_executor.execute(() -> {
			m_executor.execute(() -> order.add("queued"));
			m_executor.runAsDispatcher(() -> order.add("inline"));
			order.add("after");
		});

		assertEquals(List.of("inline", "after", "queued"), order);
	}

	@Test
	public void runAsDispatcher_rejects_holding_outer_lock() {
		Guard outerLock = Guard.create();
		CallerRunsSerialExecutor executor = new CallerRunsSerialExecutor(outerLock);

		AtomicBoolean ran = new AtomicBoolean();
		outerLock.lock();
		try {
			assertThrows(IllegalStateException.class, () -> executor.runAsDispatcher(() -> ran.set(true)));
		}
		finally {
			outerLock.unlock();
		}
		assertFalse(ran.get());
		assertEquals("ok", executor.invokeOrDefer(() -> "ok").get());
	}

	@Test
	public void runAsDispatcher_propagates_failure_after_draining() {
		List<String> order = new ArrayList<>();
		IllegalStateException cause = new IllegalStateException("init failed");
		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> m_executor.runAsDispatcher(() -> {
			m_executor.execute(() -> order.add("queued"));
			throw cause;
		}));

		assertSame(cause, thrown);
		assertEquals(List.of("queued"), order);
		assertFalse(m_executor.isDispatcherThread());
	}

	// ---- 동시성 스트레스 ----

	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	public void concurrent_tasks_run_one_at_a_time_in_per_thread_order() throws Exception {
		int nthreads = 8;
		int ntasks = 500;
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		int[][] seen = new int[nthreads][ntasks];
		int[] next = new int[nthreads];
		AtomicBoolean outOfOrder = new AtomicBoolean();

		CountDownLatch go = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for ( int t = 0; t < nthreads; ++t ) {
			int tid = t;
			Thread thread = new Thread(() -> {
				awaitQuietly(go);
				for ( int i = 0; i < ntasks; ++i ) {
					int seq = i;
					Runnable task = () -> {
						maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
						// 같은 스레드가 넣은 작업은 넣은 순서대로 실행되어야 한다.
						if ( next[tid] != seq ) {
							outOfOrder.set(true);
						}
						next[tid] = seq + 1;
						seen[tid][seq]++;
						running.decrementAndGet();
					};
					switch ( seq % 3 ) {
						case 0 -> m_executor.execute(task);
						case 1 -> m_executor.submit(task);
						default -> m_executor.invokeOrDefer(() -> { task.run(); return null; });
					}
				}
			});
			threads.add(thread);
			thread.start();
		}
		go.countDown();
		for ( Thread thread: threads ) {
			thread.join();
		}
		// 마지막 dispatcher가 남은 작업을 다 실행할 때까지 기다린다.
		m_executor.runAsDispatcher(() -> { });

		assertEquals(1, maxRunning.get());
		assertFalse(outOfOrder.get());
		for ( int t = 0; t < nthreads; ++t ) {
			for ( int i = 0; i < ntasks; ++i ) {
				assertEquals(1, seen[t][i], "thread=" + t + ", task=" + i);
			}
		}
		assertEquals(0, m_executor.getPendingTaskCount());
	}

	private void awaitPending(int count) throws InterruptedException {
		while ( m_executor.getPendingTaskCount() < count ) {
			Thread.sleep(5);
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
	}
}
