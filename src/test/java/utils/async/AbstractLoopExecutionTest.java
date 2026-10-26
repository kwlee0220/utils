package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.func.FOption;
import org.junit.jupiter.api.Timeout;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class AbstractLoopExecutionTest {
	@BeforeEach
	public void setup() {
	}
	
	@Test
	public void test1() throws Exception {
		StartableExecution<Integer> exec = new TestExecution1(5);
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		Assertions.assertEquals(true, exec.isStarted());
		Assertions.assertEquals(false, exec.isDone());
		
		AsyncResult<Integer> result;
		Assertions.assertThrows(TimeoutException.class,
								() -> exec.waitForFinished(100, TimeUnit.MILLISECONDS));

		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assertions.assertEquals(true, result.isCompleted());
		Assertions.assertEquals(5, (int)result.get());
	}
	
	@Test
	public void test2() throws Exception {
		StartableExecution<Integer> exec = new TestExecution1(5000);
		
		exec.start();
		Assertions.assertThrows(TimeoutException.class,
								() -> exec.waitForFinished(100, TimeUnit.MILLISECONDS));

		boolean done;
		done = exec.cancel(true);
		Assertions.assertEquals(true, done);
		
		AsyncResult<Integer> result;
		result = exec.waitForFinished();
		Assertions.assertEquals(true, result.isCancelled());
		Assertions.assertEquals(true, exec.isDone());
	}
	
	@Test
	public void test3() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution2(5);
		Assertions.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		Assertions.assertEquals(true, exec.isStarted());
		Assertions.assertEquals(false, exec.isDone());
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assertions.assertEquals(true, result.isCancelled());
	}
	
	@Test
	public void test4() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution3(5);
		
		exec.start();
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assertions.assertEquals(true, result.isFailed());
		Assertions.assertEquals("test", result.getFailureCause().getMessage());
	}
	
	@Test
	public void test5() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution4(5);
		
		exec.start();
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assertions.assertEquals(true, result.isFailed());
		Assertions.assertEquals("test", result.getFailureCause().getMessage());
	}
	
	// ---------- finalize 라이프사이클 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_called_on_normal_completion() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isCompleted());
		Assertions.assertEquals(1, loop.initCalls.get());
		Assertions.assertEquals(3, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_called_on_external_cancel() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.iterationDelayMs = 50;
		loop.start();
		loop.waitForStarted();
		MILLISECONDS.sleep(120);   // 적어도 한 번의 iterate 진입 보장

		Assertions.assertTrue(loop.cancel(true));
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(1, loop.initCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_called_on_iterate_failure() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(10);
		loop.iterateThrowAt = 2L;
		loop.iterateThrow = new IllegalStateException("boom");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, loop.initCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_called_when_start_aborted_after_initializeLoop() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.initEntered = new CountDownLatch(1);
		loop.initRelease = new CountDownLatch(1);
		loop.start();
		Assertions.assertTrue(loop.initEntered.await(2, TimeUnit.SECONDS));

		// 초기화 도중 외부에서 실패로 종료시킨다(STARTING 상태의 notifyFailed는 허용된다).
		// 초기화가 성공해도 RUNNING으로 전이하지 못하므로 loop 본체는 실행되지 않는다.
		Assertions.assertTrue(loop.notifyFailed(new IllegalStateException("external")));
		loop.initRelease.countDown();

		Assertions.assertTrue(loop.finalized.await(2, TimeUnit.SECONDS), "finalizeLoop not called");
		Assertions.assertTrue(loop.waitForFinished().isFailed());
		Assertions.assertEquals(1, loop.initCalls.get());
		Assertions.assertEquals(0, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_called_when_run_aborted_after_initializeLoop() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.onInit = () -> loop.notifyFailed(new IllegalStateException("external"));

		Assertions.assertThrows(ExecutionException.class, loop::run);
		Assertions.assertTrue(loop.isFailed());
		Assertions.assertEquals(0, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_runs_without_interrupt_flag_and_flag_is_restored() throws Exception {
		// iterate 도중 스레드가 인터럽트되면 다음 iteration 직전 검사에서 취소로 끝난다.
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.interruptInIterate = true;
		loop.finalizeSleepMs = 20;		// 정리 작업의 블로킹 호출
		try {
			Assertions.assertThrows(CancellationException.class, loop::run);
			Assertions.assertTrue(loop.isCancelled());
			Assertions.assertEquals(1, loop.iterateCalls.get());
			Assertions.assertEquals(1, loop.finalizeCalls.get());
			Assertions.assertFalse(loop.finalizeSawInterrupt, "finalizeLoop must run with the interrupt flag cleared");
			Assertions.assertTrue(loop.finalizeSleepCompleted, "blocking call in finalizeLoop was interrupted");
			Assertions.assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void initializeLoop_cancel_exceptions_transition_to_CANCELLED() throws Exception {
		for ( Throwable cause: new Throwable[] { new InterruptedException("init"), new CancellationException("init") } ) {
			LifecycleLoop started = new LifecycleLoop(3);
			started.initThrow = cause;
			started.start();
			AsyncResult<Integer> result = started.waitForFinished(1, TimeUnit.SECONDS);
			Assertions.assertTrue(result.isCancelled(), cause + ": " + result);
			Assertions.assertEquals(0, started.iterateCalls.get());
			Assertions.assertEquals(0, started.finalizeCalls.get());

			LifecycleLoop ran = new LifecycleLoop(3);
			ran.initThrow = cause;
			Assertions.assertThrows(Exception.class, ran::run);
			Assertions.assertEquals(AsyncState.CANCELLED, ran.getState(), cause.toString());
			Assertions.assertEquals(0, ran.finalizeCalls.get());
		}
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void iterate_returning_null_fails_with_IllegalStateException() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.returnNull = true;
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		Throwable cause = result.getFailureCause();
		Assertions.assertInstanceOf(IllegalStateException.class, cause);
		Assertions.assertTrue(cause.getMessage().startsWith("iterate() returned null: loopIndex=0"), cause.getMessage());
		Assertions.assertEquals(1, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void result_returned_after_cancel_request_completes() throws Exception {
		// 취소 요청 시점에 진행 중이던 iteration이 최종 결과를 반환하면 COMPLETED가 된다.
		LifecycleLoop loop = new LifecycleLoop(1);
		loop.iterEntered = new CountDownLatch(1);
		loop.iterRelease = new CountDownLatch(1);
		loop.start();
		Assertions.assertTrue(loop.iterEntered.await(2, TimeUnit.SECONDS));

		Assertions.assertTrue(loop.cancel(true));
		Assertions.assertEquals(AsyncState.CANCELLING, loop.getState());
		loop.iterRelease.countDown();

		AsyncResult<Integer> result = loop.waitForFinished();
		Assertions.assertTrue(result.isCompleted(), result.toString());
		Assertions.assertEquals(1, result.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_NOT_called_when_initializeLoop_fails() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.initThrow = new IllegalStateException("init");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isFailed());
		Assertions.assertEquals(1, loop.initCalls.get());
		Assertions.assertEquals(0, loop.iterateCalls.get());
		Assertions.assertEquals(0, loop.finalizeCalls.get());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void finalizeLoop_exception_swallowed_completion_unaffected() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(2);
		loop.finalizeThrow = new IllegalStateException("finalize-boom");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isCompleted(), "finalizeLoop의 예외는 무시되어야 함");
		Assertions.assertEquals(2, (int)result.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	// ---------- iterate 동작 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void iterate_throws_InterruptedException_transitions_to_CANCELLED() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(10);
		loop.iterateThrowAt = 1L;
		loop.iterateThrow = new InterruptedException("ie");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	// ---------- cancel 동작 ----------

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void cancel_before_start_skips_initialize_and_iterate() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);

		Assertions.assertTrue(loop.cancel(true));
		Assertions.assertEquals(AsyncState.CANCELLED, loop.getState());
		Assertions.assertEquals(0, loop.initCalls.get());
		Assertions.assertEquals(0, loop.iterateCalls.get());
		Assertions.assertEquals(0, loop.finalizeCalls.get());
	}

	// ---------- 외부 종료 상태 전이 ----------

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void loop_stops_when_cancelWork_failure_makes_it_FAILED() throws Exception {
		// cancelWork()가 예외를 던지면 cancel()이 실행을 FAILED로 전이시킨다.
		// 진행 중이던 iteration이 empty를 반환해도 다음 iteration은 수행되지 않아야 한다.
		IllegalStateException boom = new IllegalStateException("cancelWork-boom");
		CancelWorkFailingLoop loop = new CancelWorkFailingLoop(1000, boom);
		loop.iterEntered = new CountDownLatch(1);
		loop.iterRelease = new CountDownLatch(1);
		loop.start();
		Assertions.assertTrue(loop.iterEntered.await(2, TimeUnit.SECONDS));

		Assertions.assertFalse(loop.cancel(true));
		Assertions.assertEquals(AsyncState.FAILED, loop.getState());
		loop.iterRelease.countDown();

		Assertions.assertTrue(loop.finalized.await(2, TimeUnit.SECONDS), "finalizeLoop not called");
		AsyncResult<Integer> result = loop.waitForFinished();
		Assertions.assertTrue(result.isFailed(), result.toString());
		Assertions.assertSame(boom, result.getFailureCause());
		Assertions.assertEquals(1, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void loop_stops_after_external_notifyFailed() throws Exception {
		IllegalStateException external = new IllegalStateException("external");
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.onIterate = () -> loop.notifyFailed(external);
		loop.start();

		Assertions.assertTrue(loop.finalized.await(2, TimeUnit.SECONDS), "finalizeLoop not called");
		AsyncResult<Integer> result = loop.waitForFinished();
		Assertions.assertTrue(result.isFailed(), result.toString());
		Assertions.assertSame(external, result.getFailureCause());
		Assertions.assertEquals(1, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_reports_external_failure_instead_of_cancellation() throws Exception {
		// run()으로 실행 중 외부에서 FAILED로 확정되면 CancellationException이 아니라
		// 그 실패 원인을 담은 ExecutionException이 전달되어야 한다.
		IllegalStateException external = new IllegalStateException("external");
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.onIterate = () -> loop.notifyFailed(external);

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, loop::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertTrue(loop.isFailed());
		Assertions.assertEquals(1, loop.iterateCalls.get());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_reports_external_failure_when_iterate_then_throws() throws Exception {
		// 외부에서 FAILED로 확정된 뒤 iterate가 다른 예외를 던져도, run()은 확정된 실패 원인을 전달해야 한다.
		IllegalStateException external = new IllegalStateException("external");
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.onIterate = () -> loop.notifyFailed(external);
		loop.iterateThrow = new IllegalArgumentException("own");

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, loop::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertSame(external, loop.poll().getFailureCause());
		Assertions.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void run_reports_external_failure_when_initializeLoop_then_throws() throws Exception {
		// 초기화 도중 외부에서 FAILED로 확정된 뒤 initializeLoop가 다른 예외를 던져도,
		// run()은 확정된 실패 원인을 전달해야 한다.
		IllegalStateException external = new IllegalStateException("external");
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.onInit = () -> loop.notifyFailed(external);
		loop.initThrow = new IllegalArgumentException("own");

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, loop::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertSame(external, loop.poll().getFailureCause());
		Assertions.assertEquals(0, loop.iterateCalls.get());

		// initializeLoop가 취소 예외를 던지는 경우도 마찬가지로 확정된 실패 원인을 따라야 한다.
		LifecycleLoop cancelled = new LifecycleLoop(3);
		cancelled.onInit = () -> cancelled.notifyFailed(external);
		cancelled.initThrow = new CancellationException("own");

		thrown = Assertions.assertThrows(ExecutionException.class, cancelled::run);
		Assertions.assertSame(external, thrown.getCause());
		Assertions.assertTrue(cancelled.isFailed());
	}

	// ---------- getLoopOutcome ----------

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void loopOutcome_reflects_how_the_loop_ended() throws Exception {
		// 정상 완료
		LifecycleLoop completed = new LifecycleLoop(2);
		Assertions.assertEquals(2, completed.run());
		Assertions.assertTrue(completed.outcomeAtFinalize.isCompleted());
		Assertions.assertEquals(2, completed.outcomeAtFinalize.getUnchecked());

		// iterate 실패: 원인 예외가 그대로 전달
		IllegalStateException boom = new IllegalStateException("boom");
		LifecycleLoop failed = new LifecycleLoop(10);
		failed.iterateThrow = boom;
		Assertions.assertThrows(ExecutionException.class, failed::run);
		Assertions.assertTrue(failed.outcomeAtFinalize.isFailed());
		Assertions.assertSame(boom, failed.outcomeAtFinalize.getFailureCause());

		// 취소
		LifecycleLoop cancelled = new LifecycleLoop(10);
		cancelled.iterateThrow = new CancellationException("cancel");
		Assertions.assertThrows(CancellationException.class, cancelled::run);
		Assertions.assertTrue(cancelled.outcomeAtFinalize.isCancelled());

		// 외부에서 확정된 실패: 그 결과가 전달
		IllegalStateException external = new IllegalStateException("external");
		LifecycleLoop externallyFailed = new LifecycleLoop(10);
		externallyFailed.onIterate = () -> externallyFailed.notifyFailed(external);
		Assertions.assertThrows(ExecutionException.class, externallyFailed::run);
		Assertions.assertTrue(externallyFailed.outcomeAtFinalize.isFailed());
		Assertions.assertSame(external, externallyFailed.outcomeAtFinalize.getFailureCause());

		// 초기화 후 시작하지 못한 경우(abortAfterInitialize): 외부에서 확정된 결과가 전달
		LifecycleLoop aborted = new LifecycleLoop(3);
		aborted.onInit = () -> aborted.notifyFailed(external);
		Assertions.assertThrows(ExecutionException.class, aborted::run);
		Assertions.assertSame(external, aborted.outcomeAtFinalize.getFailureCause());
	}

	// ---------- helper task classes ----------

	/**
	 * {@link CancellableWork#cancelWork()}가 항상 예외를 던지는 {@link LifecycleLoop}.
	 */
	private static class CancelWorkFailingLoop extends LifecycleLoop implements CancellableWork {
		private final RuntimeException m_cancelFailure;

		CancelWorkFailingLoop(int targetIterations, RuntimeException cancelFailure) {
			super(targetIterations);
			m_cancelFailure = cancelFailure;
		}

		@Override
		public boolean cancelWork() {
			throw m_cancelFailure;
		}
	}

	/**
	 * {@link AbstractLoopExecution}의 라이프사이클 (초기화/iteration/finalize) 호출 횟수와
	 * 동작을 외부에서 제어/관찰할 수 있게 한 테스트용 구현체.
	 */
	private static class LifecycleLoop extends AbstractLoopExecution<Integer> {
		final AtomicInteger initCalls = new AtomicInteger();
		final AtomicInteger iterateCalls = new AtomicInteger();
		final AtomicInteger finalizeCalls = new AtomicInteger();

		private final int m_targetIterations;
		Throwable initThrow;
		Throwable iterateThrow;
		Long iterateThrowAt;       // null이면 iterateThrow가 설정되었을 때 매 호출마다 throw
		Throwable finalizeThrow;
		long iterationDelayMs = 0;
		CountDownLatch initEntered;		// null이 아니면 initializeLoop 진입을 알리고 initRelease까지 대기
		CountDownLatch initRelease;
		Runnable onInit;				// initializeLoop 안에서 실행할 동작
		final CountDownLatch finalized = new CountDownLatch(1);
		boolean interruptInIterate;		// iterate 안에서 자기 스레드를 인터럽트한다
		boolean returnNull;				// iterate가 계약을 어기고 null을 반환한다
		CountDownLatch iterEntered;		// null이 아니면 iterate 진입을 알리고 iterRelease까지 대기
		CountDownLatch iterRelease;
		Runnable onIterate;				// iterate 안에서 실행할 동작
		long finalizeSleepMs = 0;		// finalizeLoop 안의 블로킹 호출 시간
		volatile boolean finalizeSawInterrupt;
		volatile AsyncResult<Integer> outcomeAtFinalize;	// finalizeLoop 안에서 조회한 getLoopOutcome()
		volatile boolean finalizeSleepCompleted;

		LifecycleLoop(int targetIterations) {
			m_targetIterations = targetIterations;
		}

		@Override
		protected void initializeLoop() throws Exception {
			initCalls.incrementAndGet();
			if ( initEntered != null ) {
				initEntered.countDown();
				initRelease.await();
			}
			if ( onInit != null ) {
				onInit.run();
			}
			if ( initThrow != null ) {
				throwAs(initThrow);
			}
		}

		@Override
		protected FOption<Integer> iterate(long loopIndex) throws InterruptedException, ExecutionException {
			int n = iterateCalls.incrementAndGet();
			if ( iterEntered != null ) {
				iterEntered.countDown();
				iterRelease.await();
			}
			if ( onIterate != null ) {
				onIterate.run();
			}
			if ( returnNull ) {
				return null;
			}
			if ( iterationDelayMs > 0 ) {
				MILLISECONDS.sleep(iterationDelayMs);
			}
			if ( interruptInIterate ) {
				Thread.currentThread().interrupt();
			}
			if ( iterateThrow != null
					&& (iterateThrowAt == null || iterateThrowAt == loopIndex) ) {
				throwAs(iterateThrow);
			}
			return n >= m_targetIterations ? FOption.of(n) : FOption.empty();
		}

		@Override
		protected void finalizeLoop() {
			finalizeCalls.incrementAndGet();
			finalizeSawInterrupt = Thread.currentThread().isInterrupted();
			outcomeAtFinalize = getLoopOutcome();
			if ( finalizeSleepMs > 0 ) {
				try {
					MILLISECONDS.sleep(finalizeSleepMs);
					finalizeSleepCompleted = true;
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
				}
			}
			finalized.countDown();
			if ( finalizeThrow != null ) {
				if ( finalizeThrow instanceof RuntimeException ) {
					throw (RuntimeException)finalizeThrow;
				}
				if ( finalizeThrow instanceof Error ) {
					throw (Error)finalizeThrow;
				}
				throw new RuntimeException(finalizeThrow);
			}
		}

		private static void throwAs(Throwable t) throws InterruptedException, ExecutionException {
			if ( t instanceof RuntimeException ) {
				throw (RuntimeException)t;
			}
			if ( t instanceof Error ) {
				throw (Error)t;
			}
			if ( t instanceof InterruptedException ie ) {
				throw ie;
			}
			if ( t instanceof ExecutionException ee ) {
				throw ee;
			}
			if ( t instanceof Exception ) {
				throw new ExecutionException(t);
			}
			throw new RuntimeException(t);
		}
	}

	private static class TestExecution1 extends AbstractLoopExecution<Integer> {
		private final int m_limit;
		private int m_index;
		
		TestExecution1(int limit) {
			m_limit = limit;
		}

		@Override
		protected void initializeLoop() throws Exception {
			m_index = 0;
		}

		@Override
		protected FOption<Integer> iterate(long loopIndex) throws InterruptedException {
			Assertions.assertEquals(loopIndex, m_index);
			MILLISECONDS.sleep(100);
			++m_index;
			
			return (m_index >= m_limit) ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void finalizeLoop() { }
	}
	
	private static class TestExecution2 extends AbstractLoopExecution<Integer> {
		private final int m_limit;
		private int m_index;
		
		TestExecution2(int limit) {
			m_limit = limit;
		}

		@Override
		protected void initializeLoop() throws Exception {
			m_index = 0;
		}

		@Override
		protected FOption<Integer> iterate(long loopIndex) throws InterruptedException {
			Assertions.assertEquals(loopIndex, m_index);
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new CancellationException();
			}

			return (m_index >= m_limit) ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void finalizeLoop() { }
	}
	
	private static class TestExecution3 extends AbstractLoopExecution<Integer> {
		private final int m_limit;
		private int m_index;
		
		TestExecution3(int limit) {
			m_limit = limit;
		}

		@Override
		protected void initializeLoop() throws Exception {
			m_index = 0;
		}

		@Override
		protected FOption<Integer> iterate(long loopIndex) throws InterruptedException, ExecutionException {
			Assertions.assertEquals(loopIndex, m_index);
			
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new ExecutionException(new Exception("test"));
			}

			return (m_index >= m_limit) ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void finalizeLoop() { }
	}
	
	private static class TestExecution4 extends AbstractLoopExecution<Integer> {
		private final int m_limit;
		private int m_index;
		
		TestExecution4(int limit) {
			m_limit = limit;
		}

		@Override
		protected void initializeLoop() throws Exception {
			throw new Exception("test");
		}

		@Override
		protected FOption<Integer> iterate(long loopIndex) throws InterruptedException, ExecutionException {
			Assertions.assertEquals(loopIndex, m_index);
			
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new ExecutionException(new Exception("test"));
			}

			return (m_index >= m_limit) ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void finalizeLoop() { }
	}
}
