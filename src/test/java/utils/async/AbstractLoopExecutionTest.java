package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.FOption;


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
		Assert.assertEquals(true, exec.isDone());
	}
	
	@Test
	public void test3() throws Exception {
		AsyncResult<Integer> result;
		
		StartableExecution<Integer> exec = new TestExecution2(5);
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
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
		protected FOption<Integer> isLoopFinished() {
			return m_index >= m_limit ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void iterate() throws Exception {
			MILLISECONDS.sleep(100);
			++m_index;
		}

		@Override
		protected void finalizeLoop() throws Exception { }
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
		protected FOption<Integer> isLoopFinished() {
			return m_index >= m_limit ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void iterate() throws Exception {
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new CancellationException();
			}
		}

		@Override
		protected void finalizeLoop() throws Exception { }
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
		protected FOption<Integer> isLoopFinished() {
			return m_index >= m_limit ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void iterate() throws Exception {
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new Exception("test");
			}
		}

		@Override
		protected void finalizeLoop() throws Exception { }
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
		protected FOption<Integer> isLoopFinished() {
			return m_index >= m_limit ? FOption.of(m_index) : FOption.empty();
		}

		@Override
		protected void iterate() throws Exception {
			MILLISECONDS.sleep(100);
			if ( ++m_index == 3 ) {
				throw new Exception("test");
			}
		}

		@Override
		protected void finalizeLoop() throws Exception { }
	}
}
