package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link AbstractThreadedExecution}의 {@link AbstractThreadedExecution#start()}/
 * {@link AbstractThreadedExecution#run()} 라이프사이클 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractThreadedExecutionTest {

	// ---------- start() : 비동기 실행 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_success() throws Exception {
		ResultTask task = new ResultTask("ok");
		task.start();
		AsyncResult<String> result = task.waitForFinished();

		Assertions.assertTrue(result.isCompleted());
		Assertions.assertEquals("ok", result.getUnchecked());
		Assertions.assertEquals(AsyncState.COMPLETED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_runtime_exception_transitions_to_FAILED() throws Exception {
		IllegalStateException cause = new IllegalStateException("boom");
		FailingTask task = new FailingTask(cause);
		task.start();
		AsyncResult<Void> result = task.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		Assertions.assertSame(cause, result.getFailureCause());
		Assertions.assertEquals(AsyncState.FAILED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_executeWork_throws_InterruptedException_transitions_to_CANCELLED()
																				throws Exception {
		FailingTask task = new FailingTask(new InterruptedException("ie"));
		task.start();
		AsyncResult<Void> result = task.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_executeWork_throws_CancellationException_transitions_to_CANCELLED()
																				throws Exception {
		FailingTask task = new FailingTask(new CancellationException("ce"));
		task.start();
		AsyncResult<Void> result = task.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_external_cancel_interrupts_and_transitions_to_CANCELLED() throws Exception {
		InterruptibleTask task = new InterruptibleTask();
		task.start();
		Assertions.assertTrue(task.awaitInExecuteWork());
		Assertions.assertEquals(AsyncState.RUNNING, task.getState());

		Assertions.assertTrue(task.cancel(true));
		AsyncResult<Void> result = task.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_initializeThread_failure_skips_executeWork() throws Exception {
		IllegalStateException cause = new IllegalStateException("init-boom");
		InitFailingTask task = new InitFailingTask(cause);
		task.start();
		AsyncResult<Void> result = task.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		Assertions.assertSame(cause, result.getFailureCause());
		Assertions.assertFalse(task.executeWorkCalled.get());
	}

	// initializeThread()가 취소 예외를 던지면, 시작 준비 중인 스레드가 STARTING을 쥐고 있는 상태에서
	// 취소 전이를 해야 한다. cancelTimeout(기본 3초)을 기다리지 않고 바로 CANCELLED가 되어야 한다.
	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_initializeThread_InterruptedException_transitions_to_CANCELLED() throws Exception {
		assertStartInitCancelled(new InterruptedException("init-interrupted"));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_initializeThread_CancellationException_transitions_to_CANCELLED() throws Exception {
		assertStartInitCancelled(new CancellationException("init-cancelled"));
	}

	private static void assertStartInitCancelled(Exception cause) throws Exception {
		InitFailingTask task = new InitFailingTask(cause);
		CountDownLatch finished = new CountDownLatch(1);
		task.whenFinished(r -> finished.countDown());
		task.start();

		AsyncResult<Void> result = task.waitForFinished(1, TimeUnit.SECONDS);
		Assertions.assertTrue(result.isCancelled(), result.toString());
		Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
		Assertions.assertTrue(finished.await(1, TimeUnit.SECONDS), "whenFinished listener not called");
		Assertions.assertFalse(task.executeWorkCalled.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_with_injected_executor_runs_on_executor_thread() throws Exception {
		AtomicReference<String> threadNameRef = new AtomicReference<>();
		ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "injected-pool-thread");
			t.setDaemon(true);
			return t;
		});
		try {
			ResultTask task = new ResultTask("ok") {
				@Override
				protected String executeWork() throws Exception {
					threadNameRef.set(Thread.currentThread().getName());
					return super.executeWork();
				}
			};
			task.setExecutor(executor);
			task.start();
			task.waitForFinished();

			Assertions.assertEquals("injected-pool-thread", threadNameRef.get());
		}
		finally {
			executor.shutdown();
		}
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_executor_rejects_transitions_to_FAILED_and_propagates() {
		RejectedExecutionException rejection = new RejectedExecutionException("nope");
		Executor rejecting = r -> { throw rejection; };
		ResultTask task = new ResultTask("ok");
		task.setExecutor(rejecting);

		try {
			task.start();
			Assertions.fail("expected RejectedExecutionException");
		}
		catch ( RejectedExecutionException expected ) {
			Assertions.assertSame(rejection, expected);
		}
		Assertions.assertEquals(AsyncState.FAILED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_twice_throws() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			BlockingTask task = new BlockingTask();
			task.start();
			task.waitForStarted();   // RUNNING 도달 보장
			try {
				task.start();
			}
			finally {
				task.release();
				task.waitForFinished();
			}
			});
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_after_start_throws() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			BlockingTask task = new BlockingTask();
			task.start();
			task.waitForStarted();   // RUNNING 도달 보장
			try {
				task.run();
			}
			finally {
				task.release();
				task.waitForFinished();
			}
			});
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_default_thread_is_daemon() throws Exception {
		ThreadCapturingTask task = new ThreadCapturingTask();
		task.start();
		Assertions.assertTrue(task.awaitInExecuteWork());
		try {
			Thread t = task.thread.get();
			Assertions.assertNotNull(t);
			Assertions.assertTrue(t.isDaemon(), "default thread should be daemon");
		}
		finally {
			task.release();
			task.waitForFinished();
		}
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_setDaemonThread_false_creates_user_thread() throws Exception {
		ThreadCapturingTask task = new ThreadCapturingTask();
		task.setDaemonThread(false);
		task.start();
		Assertions.assertTrue(task.awaitInExecuteWork());
		try {
			Thread t = task.thread.get();
			Assertions.assertNotNull(t);
			Assertions.assertFalse(t.isDaemon(), "non-daemon flag must be honored");
		}
		finally {
			task.release();
			task.waitForFinished();
		}
	}

	// ---------- run() : 동기 실행 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_success_returns_value() throws Exception {
		ResultTask task = new ResultTask("ok");
		Assertions.assertEquals("ok", task.run());
		Assertions.assertEquals(AsyncState.COMPLETED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_runtime_exception_wraps_in_ExecutionException() {
		IllegalStateException cause = new IllegalStateException("boom");
		FailingTask task = new FailingTask(cause);
		try {
			task.run();
			Assertions.fail("expected ExecutionException");
		}
		catch ( ExecutionException e ) {
			Assertions.assertSame(cause, e.getCause());
		}
		catch ( Exception e ) {
			Assertions.fail("expected ExecutionException, got " + e);
		}
		Assertions.assertEquals(AsyncState.FAILED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_existing_ExecutionException_passes_through() {
		ExecutionException original = new ExecutionException(new RuntimeException("inner"));
		FailingTask task = new FailingTask(original);
		try {
			task.run();
			Assertions.fail("expected ExecutionException");
		}
		catch ( ExecutionException e ) {
			Assertions.assertSame(original, e);
		}
		catch ( Exception e ) {
			Assertions.fail("expected ExecutionException, got " + e);
		}
		Assertions.assertEquals(AsyncState.FAILED, task.getState());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_executeWork_throws_InterruptedException() throws Exception {
		Assertions.assertThrows(InterruptedException.class, () -> {
			FailingTask task = new FailingTask(new InterruptedException("ie"));
			try {
				task.run();
			}
			finally {
				Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
			}
			});
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_executeWork_throws_CancellationException() throws Exception {
		Assertions.assertThrows(CancellationException.class, () -> {
			FailingTask task = new FailingTask(new CancellationException("ce"));
			try {
				task.run();
			}
			finally {
				Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
			}
			});
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_initializeThread_failure_wraps_in_ExecutionException() {
		IllegalStateException cause = new IllegalStateException("init-boom");
		InitFailingTask task = new InitFailingTask(cause);
		try {
			task.run();
			Assertions.fail("expected ExecutionException");
		}
		catch ( ExecutionException e ) {
			Assertions.assertSame(cause, e.getCause());
		}
		catch ( Exception e ) {
			Assertions.fail("expected ExecutionException, got " + e);
		}
		Assertions.assertFalse(task.executeWorkCalled.get());
		Assertions.assertEquals(AsyncState.FAILED, task.getState());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_initializeThread_InterruptedException_transitions_to_CANCELLED() {
		assertRunInitCancelled(new InterruptedException("init-interrupted"), InterruptedException.class);
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_initializeThread_CancellationException_transitions_to_CANCELLED() {
		assertRunInitCancelled(new CancellationException("init-cancelled"), CancellationException.class);
	}

	private static void assertRunInitCancelled(Exception cause, Class<? extends Exception> expected) {
		InitFailingTask task = new InitFailingTask(cause);
		long started = System.nanoTime();
		Assertions.assertThrows(expected, task::run);
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

		Assertions.assertEquals(AsyncState.CANCELLED, task.getState());
		Assertions.assertTrue(elapsedMs < 1_000, "waited for cancelTimeout: " + elapsedMs + "ms");
		Assertions.assertFalse(task.executeWorkCalled.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_twice_throws() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			ResultTask task = new ResultTask("ok");
			task.run();
			task.run();
			});
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_after_run_throws() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			ResultTask task = new ResultTask("ok");
			task.run();
			task.start();
			});
	}

	@Test
	public void queued_start_rejects_second_start_and_run() throws Exception {
		java.util.List<Runnable> queued = new java.util.ArrayList<>();
		ResultTask task = new ResultTask("ok");
		task.setExecutor(queued::add);
		task.start();
		Assertions.assertThrows(IllegalStateException.class, task::start);
		Assertions.assertThrows(IllegalStateException.class, task::run);
		Assertions.assertEquals(1, queued.size());
		queued.get(0).run();
		Assertions.assertEquals("ok", task.get());
	}

	@Test
	@Timeout(5)
	public void concurrent_start_submits_only_one_worker() throws Exception {
		java.util.List<Runnable> queued = new java.util.concurrent.CopyOnWriteArrayList<>();
		ResultTask task = new ResultTask("ok");
		task.setExecutor(queued::add);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();
		ExecutorService callers = Executors.newFixedThreadPool(2);
		try {
			java.util.List<java.util.concurrent.Future<?>> attempts = new java.util.ArrayList<>();
			for ( int i = 0; i < 2; ++i ) {
				attempts.add(callers.submit(() -> {
					ready.countDown();
					go.await();
					try { task.start(); }
					catch ( IllegalStateException expected ) { rejected.incrementAndGet(); }
					return null;
				}));
			}
			Assertions.assertTrue(ready.await(2, TimeUnit.SECONDS));
			go.countDown();
			for ( var attempt : attempts ) { attempt.get(2, TimeUnit.SECONDS); }
			Assertions.assertEquals(1, rejected.get());
			Assertions.assertEquals(1, queued.size());
			queued.get(0).run();
			Assertions.assertEquals("ok", task.get());
		}
		finally {
			go.countDown();
			callers.shutdownNow();
		}
	}

	@Test
	public void run_and_start_preserve_domain_exception_with_nested_cause() throws Exception {
		IllegalStateException domain = new IllegalStateException("domain", new java.io.IOException("transport"));
		FailingTask sync = new FailingTask(domain);
		Assertions.assertSame(domain, Assertions.assertThrows(ExecutionException.class, sync::run).getCause());
		Assertions.assertSame(domain, Assertions.assertThrows(ExecutionException.class, sync::get).getCause());
		FailingTask async = new FailingTask(domain);
		async.start();
		Assertions.assertSame(domain, Assertions.assertThrows(ExecutionException.class,
				() -> async.get(2, TimeUnit.SECONDS)).getCause());
	}

	// ---------- finalizeThread ----------

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_calls_finalizeThread_once_after_terminal_transition() throws Exception {
		for ( Exception workThrow: new Exception[] { null, new IllegalStateException("boom"),
													new CancellationException("cancel") } ) {
			FinalizingTask task = new FinalizingTask();
			task.workThrow = workThrow;
			task.start();

			Assertions.assertTrue(task.finalized.await(2, TimeUnit.SECONDS), "finalizeThread not called: " + workThrow);
			Assertions.assertTrue(task.waitForFinished().isFinished());
			Assertions.assertEquals(1, task.finalizeCalls.get(), String.valueOf(workThrow));
			Assertions.assertTrue(task.doneWhenFinalized, "finalizeThread must run after the terminal transition");
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_calls_finalizeThread_once_after_terminal_transition() throws Exception {
		FinalizingTask task = new FinalizingTask();
		Assertions.assertEquals("done", task.run());
		Assertions.assertEquals(1, task.finalizeCalls.get());
		Assertions.assertTrue(task.doneWhenFinalized, "finalizeThread must run after the terminal transition");
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeThread_not_called_when_initializeThread_fails() throws Exception {
		FinalizingTask started = new FinalizingTask();
		started.initThrow = new IllegalStateException("init");
		started.start();
		Assertions.assertTrue(started.waitForFinished(2, TimeUnit.SECONDS).isFailed());
		Assertions.assertEquals(0, started.finalizeCalls.get());

		FinalizingTask ran = new FinalizingTask();
		ran.initThrow = new IllegalStateException("init");
		Assertions.assertThrows(ExecutionException.class, ran::run);
		Assertions.assertEquals(0, ran.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeThread_exception_does_not_affect_result() throws Exception {
		FinalizingTask ran = new FinalizingTask();
		ran.finalizeThrows = true;
		Assertions.assertEquals("done", ran.run());
		Assertions.assertTrue(ran.isCompleted());

		FinalizingTask started = new FinalizingTask();
		started.finalizeThrows = true;
		started.start();
		Assertions.assertEquals("done", started.get(2, TimeUnit.SECONDS));
		Assertions.assertTrue(started.finalized.await(2, TimeUnit.SECONDS));
		Assertions.assertEquals(1, started.finalizeCalls.get());
	}

	// ---------- onResultDiscarded ----------

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void onResultDiscarded_called_when_completion_transition_fails() throws Exception {
		// executeWork가 결과를 반환했지만 그 전에 외부에서 FAILED로 확정되면, 그 결과가 onResultDiscarded로 넘겨져야 한다.
		IllegalStateException external = new IllegalStateException("external");
		DiscardingTask ran = new DiscardingTask(external);
		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, ran::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertEquals(java.util.List.of("result"), ran.discarded);

		DiscardingTask started = new DiscardingTask(external);
		started.start();
		Assertions.assertTrue(started.waitForFinished(2, TimeUnit.SECONDS).isFailed());
		Assertions.assertTrue(started.finalized.await(2, TimeUnit.SECONDS));
		Assertions.assertEquals(java.util.List.of("result"), started.discarded);
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void onResultDiscarded_not_called_on_normal_completion() throws Exception {
		DiscardingTask task = new DiscardingTask(null);
		Assertions.assertEquals("result", task.run());
		Assertions.assertTrue(task.discarded.isEmpty());
	}

	// ---------- helper task classes ----------

	/**
	 * {@code executeWork()} 안에서 (지정된 경우) 외부 실패를 확정시킨 뒤 결과를 반환하고,
	 * {@code onResultDiscarded}로 넘겨진 결과를 기록하는 작업.
	 */
	private static class DiscardingTask extends AbstractThreadedExecution<String> {
		final java.util.List<String> discarded = new java.util.concurrent.CopyOnWriteArrayList<>();
		final CountDownLatch finalized = new CountDownLatch(1);
		private final Throwable m_externalFailure;
		DiscardingTask(Throwable externalFailure) { m_externalFailure = externalFailure; }
		@Override
		protected String executeWork() {
			if ( m_externalFailure != null ) {
				notifyFailed(m_externalFailure);
			}
			return "result";
		}
		@Override
		protected void onResultDiscarded(String result) {
			discarded.add(result);
		}
		@Override
		protected void finalizeThread() {
			finalized.countDown();
		}
	}

	/**
	 * {@link AbstractThreadedExecution#finalizeThread()} 호출 여부와 시점을 기록하는 작업.
	 */
	private static class FinalizingTask extends AbstractThreadedExecution<String> {
		final AtomicInteger finalizeCalls = new AtomicInteger();
		final CountDownLatch finalized = new CountDownLatch(1);
		volatile boolean doneWhenFinalized;
		Exception initThrow;
		Exception workThrow;
		boolean finalizeThrows;

		@Override
		protected void initializeThread() throws Exception {
			if ( initThrow != null ) {
				throw initThrow;
			}
		}
		@Override
		protected String executeWork() throws Exception {
			if ( workThrow != null ) {
				throw workThrow;
			}
			return "done";
		}
		@Override
		protected void finalizeThread() {
			finalizeCalls.incrementAndGet();
			doneWhenFinalized = isDone();
			finalized.countDown();
			if ( finalizeThrows ) {
				throw new IllegalStateException("finalize-boom");
			}
		}
	}

	private static class ResultTask extends AbstractThreadedExecution<String> {
		private final String m_result;
		ResultTask(String result) { m_result = result; }
		@Override
		protected String executeWork() throws Exception {
			return m_result;
		}
	}

	/**
	 * {@link #executeWork()}에서 주어진 {@link Throwable}을 그대로 던진다.
	 * {@code Error}는 테스트 대상이 아니므로 {@code RuntimeException} 또는 {@code Exception}만 지원.
	 */
	private static class FailingTask extends AbstractThreadedExecution<Void> {
		private final Throwable m_throwable;
		FailingTask(Throwable t) { m_throwable = t; }
		@Override
		protected Void executeWork() throws Exception {
			if ( m_throwable instanceof RuntimeException ) {
				throw (RuntimeException)m_throwable;
			}
			if ( m_throwable instanceof Exception ) {
				throw (Exception)m_throwable;
			}
			throw new RuntimeException(m_throwable);
		}
	}

	/**
	 * {@link #initializeThread()}에서 주어진 {@link Throwable}을 던진다.
	 * {@link #executeWork()}는 호출되지 않아야 한다 (검증용 플래그 제공).
	 */
	private static class InitFailingTask extends AbstractThreadedExecution<Void> {
		final AtomicBoolean executeWorkCalled = new AtomicBoolean(false);
		private final Throwable m_throwable;
		InitFailingTask(Throwable t) { m_throwable = t; }
		@Override
		protected void initializeThread() throws Exception {
			if ( m_throwable instanceof RuntimeException ) {
				throw (RuntimeException)m_throwable;
			}
			if ( m_throwable instanceof Exception ) {
				throw (Exception)m_throwable;
			}
			throw new RuntimeException(m_throwable);
		}
		@Override
		protected Void executeWork() throws Exception {
			executeWorkCalled.set(true);
			return null;
		}
	}

	/**
	 * {@code release()} 호출 전까지 {@code executeWork()}에서 차단된다.
	 */
	private static class BlockingTask extends AbstractThreadedExecution<Void> {
		private final CountDownLatch m_release = new CountDownLatch(1);
		void release() { m_release.countDown(); }
		@Override
		protected Void executeWork() throws Exception {
			m_release.await();
			return null;
		}
	}

	/**
	 * 외부에서 cancel(true) 호출 시 실제로 인터럽트를 통해 종료되는 작업.
	 */
	private static class InterruptibleTask extends AbstractThreadedExecution<Void>
															implements CancellableWork {
		private final CountDownLatch m_inExecuteWork = new CountDownLatch(1);
		private volatile Thread m_thread;
		boolean awaitInExecuteWork() throws InterruptedException {
			return m_inExecuteWork.await(3, TimeUnit.SECONDS);
		}
		@Override
		protected Void executeWork() throws Exception {
			m_thread = Thread.currentThread();
			m_inExecuteWork.countDown();
			Thread.sleep(60_000);
			return null;
		}
		@Override
		public boolean cancelWork() {
			Thread t = m_thread;
			if ( t != null ) {
				t.interrupt();
			}
			return true;
		}
	}

	/**
	 * {@code executeWork()} 진입 시점의 수행 스레드를 캡처해서 외부에서 검사할 수 있게 한다.
	 * {@code release()} 호출 전까지 차단됨.
	 */
	private static class ThreadCapturingTask extends AbstractThreadedExecution<Void> {
		final AtomicReference<Thread> thread = new AtomicReference<>();
		private final CountDownLatch m_inExecuteWork = new CountDownLatch(1);
		private final CountDownLatch m_release = new CountDownLatch(1);
		boolean awaitInExecuteWork() throws InterruptedException {
			return m_inExecuteWork.await(3, TimeUnit.SECONDS);
		}
		void release() { m_release.countDown(); }
		@Override
		protected Void executeWork() throws Exception {
			thread.set(Thread.currentThread());
			m_inExecuteWork.countDown();
			m_release.await();
			return null;
		}
	}
}
