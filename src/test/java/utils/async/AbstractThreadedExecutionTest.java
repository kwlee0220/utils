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

	// NOTE:
	// initializeThread()가 InterruptedException/CancellationException을 던지는 경우,
	// 현재 구현은 STARTING 상태에서 notifyCancelled()를 호출하게 되며,
	// EventDrivenExecution.notifyCancelled()는 STARTING 상태에서 외부 전이를 대기하도록
	// 설계되어 있어 자가-호출(self-call)에서 데드락(또는 cancelTimeout 후 false) 상태가 된다.
	// 이는 별도 버그로 다뤄야 하므로 해당 시나리오 테스트는 의도적으로 생략했다.
	// (FAILED 전이는 정상 동작하므로 위 start_initializeThread_failure_skips_executeWork만 검증.)

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

	// NOTE: run()의 initializeThread()가 InterruptedException/CancellationException을 던지는
	// 시나리오는 위와 동일한 STARTING-self-cancel 이슈로 인해 누락. 별도 버그로 보고.

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

	// ---------- helper task classes ----------

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
