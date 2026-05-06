package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractLoopExecutionTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void test1() throws Exception {
		StartableExecution<Integer> exec = new TestExecution1(5);
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(false, exec.isDone());
		
		AsyncResult<Integer> result;
		result = exec.waitForFinished(100, TimeUnit.MILLISECONDS);
		Assert.assertEquals(true, result.isRunning());
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assert.assertEquals(true, result.isCompleted());
		Assert.assertEquals(5, (int)result.get());
	}
	
	@Test
	public void test2() throws Exception {
		StartableExecution<Integer> exec = new TestExecution1(5000);
		
		exec.start();
		Assert.assertEquals(true, exec.waitForFinished(100, TimeUnit.MILLISECONDS).isRunning());
		
		boolean done;
		done = exec.cancel(true);
		Assert.assertEquals(true, done);
		
		AsyncResult<Integer> result;
		result = exec.waitForFinished();
		Assert.assertEquals(true, result.isCancelled());
		Assert.assertEquals(true, exec.isDone());
	}
	
	@Test
	public void test3() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution2(5);
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		exec.waitForStarted();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(false, exec.isDone());
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assert.assertEquals(true, result.isCancelled());
	}
	
	@Test
	public void test4() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution3(5);
		
		exec.start();
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals("test", result.getFailureCause().getMessage());
	}
	
	@Test
	public void test5() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution4(5);
		
		exec.start();
		
		result = exec.waitForFinished(1, TimeUnit.SECONDS);
		Assert.assertEquals(true, result.isFailed());
		Assert.assertEquals("test", result.getFailureCause().getMessage());
	}
	
	// ---------- finalize 라이프사이클 ----------

	@Test(timeout = 5_000)
	public void finalizeLoop_called_on_normal_completion() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue(result.isCompleted());
		Assert.assertEquals(1, loop.initCalls.get());
		Assert.assertEquals(3, loop.iterateCalls.get());
		Assert.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test(timeout = 5_000)
	public void finalizeLoop_called_on_external_cancel() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(1000);
		loop.iterationDelayMs = 50;
		loop.start();
		loop.waitForStarted();
		MILLISECONDS.sleep(120);   // 적어도 한 번의 iterate 진입 보장

		Assert.assertTrue(loop.cancel(true));
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue(result.isCancelled());
		Assert.assertEquals(1, loop.initCalls.get());
		Assert.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test(timeout = 5_000)
	public void finalizeLoop_called_on_iterate_failure() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(10);
		loop.iterateThrowAt = 2L;
		loop.iterateThrow = new IllegalStateException("boom");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue(result.isFailed());
		Assert.assertEquals(1, loop.initCalls.get());
		Assert.assertEquals(1, loop.finalizeCalls.get());
	}

	@Test(timeout = 5_000)
	public void finalizeLoop_NOT_called_when_initializeLoop_fails() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);
		loop.initThrow = new IllegalStateException("init");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue(result.isFailed());
		Assert.assertEquals(1, loop.initCalls.get());
		Assert.assertEquals(0, loop.iterateCalls.get());
		Assert.assertEquals(0, loop.finalizeCalls.get());
	}

	@Test(timeout = 5_000)
	public void finalizeLoop_exception_swallowed_completion_unaffected() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(2);
		loop.finalizeThrow = new IllegalStateException("finalize-boom");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue("finalizeLoop의 예외는 무시되어야 함", result.isCompleted());
		Assert.assertEquals(2, (int)result.get());
		Assert.assertEquals(1, loop.finalizeCalls.get());
	}

	// ---------- iterate 동작 ----------

	@Test(timeout = 5_000)
	public void iterate_throws_InterruptedException_transitions_to_CANCELLED() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(10);
		loop.iterateThrowAt = 1L;
		loop.iterateThrow = new InterruptedException("ie");
		loop.start();
		AsyncResult<Integer> result = loop.waitForFinished();

		Assert.assertTrue(result.isCancelled());
		Assert.assertEquals(1, loop.finalizeCalls.get());
	}

	// ---------- cancel 동작 ----------

	@Test(timeout = 5_000)
	public void cancel_before_start_skips_initialize_and_iterate() throws Exception {
		LifecycleLoop loop = new LifecycleLoop(3);

		Assert.assertTrue(loop.cancel(true));
		Assert.assertEquals(AsyncState.CANCELLED, loop.getState());
		Assert.assertEquals(0, loop.initCalls.get());
		Assert.assertEquals(0, loop.iterateCalls.get());
		Assert.assertEquals(0, loop.finalizeCalls.get());
	}

	// ---------- helper task classes ----------

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

		LifecycleLoop(int targetIterations) {
			m_targetIterations = targetIterations;
		}

		@Override
		protected void initializeLoop() throws Exception {
			initCalls.incrementAndGet();
			if ( initThrow != null ) {
				throwAs(initThrow);
			}
		}

		@Override
		protected Optional<Integer> iterate(long loopIndex) throws Exception {
			int n = iterateCalls.incrementAndGet();
			if ( iterationDelayMs > 0 ) {
				MILLISECONDS.sleep(iterationDelayMs);
			}
			if ( iterateThrow != null
					&& (iterateThrowAt == null || iterateThrowAt == loopIndex) ) {
				throwAs(iterateThrow);
			}
			return n >= m_targetIterations ? Optional.of(n) : Optional.empty();
		}

		@Override
		protected void finalizeLoop() {
			finalizeCalls.incrementAndGet();
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

		private static void throwAs(Throwable t) throws Exception {
			if ( t instanceof RuntimeException ) {
				throw (RuntimeException)t;
			}
			if ( t instanceof Error ) {
				throw (Error)t;
			}
			if ( t instanceof Exception ) {
				throw (Exception)t;
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
		protected Optional<Integer> iterate(long loopIndex) throws Exception {
			Assert.assertEquals(loopIndex, m_index);
			MILLISECONDS.sleep(100);
			++m_index;
			
			return (m_index >= m_limit) ? Optional.of(m_index) : Optional.empty();
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
		protected Optional<Integer> iterate(long loopIndex) throws Exception {
			Assert.assertEquals(loopIndex, m_index);
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new CancellationException();
			}

			return (m_index >= m_limit) ? Optional.of(m_index) : Optional.empty();
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
		protected Optional<Integer> iterate(long loopIndex) throws Exception {
			Assert.assertEquals(loopIndex, m_index);
			
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new Exception("test");
			}

			return (m_index >= m_limit) ? Optional.of(m_index) : Optional.empty();
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
		protected Optional<Integer> iterate(long loopIndex) throws Exception {
			Assert.assertEquals(loopIndex, m_index);
			
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new Exception("test");
			}

			return (m_index >= m_limit) ? Optional.of(m_index) : Optional.empty();
		}

		@Override
		protected void finalizeLoop() { }
	}
}
