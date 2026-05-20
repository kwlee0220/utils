package utils.async;


import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.func.CheckedRunnable;
import utils.func.Unchecked;


/**
 * {@link Executions#toExecution(CheckedRunnable)} / {@link Executions#supplyAsync(Supplier)}
 * 팩토리의 라이프사이클·리스너 발화·취소 의미론 검증.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class ExecutionsTest {
	private static final String VALUE = "completed";

	private volatile boolean m_started;
	private volatile boolean m_finished;
	private volatile boolean m_completed;
	private volatile boolean m_failed;
	private volatile boolean m_cancelled;
	private final AtomicReference<Object> m_result = new AtomicReference<>();
	private final AtomicReference<Throwable> m_cause = new AtomicReference<>();

	@BeforeEach
	public void setup() {
		m_started = false;
		m_finished = false;
		m_completed = false;
		m_failed = false;
		m_cancelled = false;
		m_result.set(null);
		m_cause.set(null);
	}

	// ---------- 작업 ----------

	private static final CheckedRunnable RUNNABLE_NOP = () -> {};
	private static final CheckedRunnable RUNNABLE_FAIL = () -> { throw new IllegalStateException("failed"); };
	private static final CheckedRunnable RUNNABLE_CANCEL_SELF = () -> {
		throw new CancellationException("cancelled");
	};
	/** 외부 cancel(true)이 도달할 때까지 대기하는 작업. */
	private static final CheckedRunnable RUNNABLE_BLOCK_UNTIL_INTERRUPTED = () -> {
		try {
			Thread.sleep(Long.MAX_VALUE);
		}
		catch ( InterruptedException expected ) {
			Thread.currentThread().interrupt();
		}
	};

	private static final Supplier<String> SUPPLIER_NOP = () -> VALUE;
	private static final Supplier<String> SUPPLIER_FAIL = () -> { throw new IllegalStateException("failed"); };
	private static final Supplier<String> SUPPLIER_CANCEL_SELF = () -> {
		throw new CancellationException("cancelled");
	};
	private static final Supplier<String> SUPPLIER_BLOCK_UNTIL_INTERRUPTED = () -> {
		try {
			Thread.sleep(Long.MAX_VALUE);
			return VALUE;
		}
		catch ( InterruptedException expected ) {
			Thread.currentThread().interrupt();
			throw new CancellationException("interrupted");
		}
	};

	// ---------- toExecution(CheckedRunnable) ----------

	@Test
	public void toExecution_completes_normally() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(RUNNABLE_NOP);
		registerListeners(exec);

		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		exec.start();

		AsyncResult<Void> result = exec.waitForFinished();
		Assertions.assertTrue(result.isCompleted());
		assertCompletedListeners(null);
		Assertions.assertNull(exec.get());
	}

	@Test
	public void toExecution_propagates_failure() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(RUNNABLE_FAIL);
		registerListeners(exec);

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_failed);
		Assertions.assertTrue(m_cause.get() instanceof IllegalStateException);
		try {
			exec.get();
			Assertions.fail("ExecutionException expected");
		}
		catch ( ExecutionException expected ) {
			Assertions.assertTrue(expected.getCause() instanceof IllegalStateException);
		}
	}

	@Test
	public void toExecution_self_cancels_on_CancellationException() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(RUNNABLE_CANCEL_SELF);
		registerListeners(exec);

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assertions.fail("CancellationException expected");
		}
		catch ( CancellationException expected ) { }
	}

	@Test
	public void toExecution_external_cancel_transitions_to_cancelled() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(RUNNABLE_BLOCK_UNTIL_INTERRUPTED);
		registerListeners(exec);

		exec.start();
		exec.waitForStarted();

		Assertions.assertTrue(exec.cancel(true));

		AsyncResult<Void> result = exec.waitForFinished();
		Assertions.assertTrue(result.isCancelled());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assertions.fail("CancellationException expected");
		}
		catch ( CancellationException expected ) { }
	}

	// ---------- supplyAsync(Supplier) ----------

	@Test
	public void supplyAsync_completes_normally() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLIER_NOP);
		registerListeners(exec);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isCompleted());
		assertCompletedListeners(VALUE);
		Assertions.assertEquals(VALUE, exec.get());
	}

	@Test
	public void supplyAsync_propagates_failure() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLIER_FAIL);
		registerListeners(exec);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_failed);
		try {
			exec.get();
			Assertions.fail("ExecutionException expected");
		}
		catch ( ExecutionException expected ) { }
	}

	@Test
	public void supplyAsync_self_cancels_on_CancellationException() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLIER_CANCEL_SELF);
		registerListeners(exec);

		exec.start();
		AsyncResult<String> result = exec.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assertions.fail("CancellationException expected");
		}
		catch ( CancellationException expected ) { }
	}

	@Test
	public void supplyAsync_external_cancel_transitions_to_cancelled() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLIER_BLOCK_UNTIL_INTERRUPTED);
		registerListeners(exec);

		exec.start();
		exec.waitForStarted();

		Assertions.assertTrue(exec.cancel(true));

		AsyncResult<String> result = exec.waitForFinished();
		Assertions.assertTrue(result.isCancelled());
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assertions.fail("CancellationException expected");
		}
		catch ( CancellationException expected ) { }
	}

	// ---------- 헬퍼 ----------

	private <T> void registerListeners(StartableExecution<T> exec) {
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted(r -> { m_finished = true; m_completed = true; m_result.set(r); });
		exec.whenFailed(ex -> { m_finished = true; m_failed = true; m_cause.set(ex); });
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
	}

	private void assertCompletedListeners(Object expectedResult) throws InterruptedException {
		awaitFinishedListener();
		Assertions.assertTrue(m_finished && m_completed);
		Assertions.assertFalse(m_failed);
		Assertions.assertFalse(m_cancelled);
		Assertions.assertEquals(expectedResult, m_result.get());
	}

	/** {@code whenFinished} 계열 리스너는 종료 직후 발화하지만 비동기일 수 있으므로 짧게 대기. */
	private void awaitFinishedListener() {
		long deadline = System.currentTimeMillis() + 1_000;
		while ( !m_finished && System.currentTimeMillis() < deadline ) {
			Unchecked.runOrRTE(() -> Thread.sleep(10));
		}
		Assertions.assertTrue(m_finished, "whenFinished 계열 리스너가 호출되어야 함");
	}
}
