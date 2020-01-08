package utils.thread;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class MultiWorkerExecutorTest {
	private MultiWorkerExecutor<Integer> m_executor
									= new MultiWorkerExecutor<>(Executors.newFixedThreadPool(2));
	
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 0; });
		m_executor.submit(() -> { return 1; });
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 2; });
		
		FOption<Try<Integer>> ret;
		
		ret = m_executor.next();
		Assert.assertEquals(1, (int)ret.get().get());
		
		ret = m_executor.next();
		Assert.assertEquals(0, (int)ret.get().get());
		
		ret = m_executor.next();
		Assert.assertEquals(2, (int)ret.get().get());
		
		ret = m_executor.next(10, MILLISECONDS);
		Assert.assertEquals(TimeoutException.class, ret.get().getCause().getClass());
		
		m_executor.endOfSubmit();
		ret = m_executor.next(0, MILLISECONDS);
		Assert.assertTrue(ret.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 0; });
		m_executor.submit(() -> { throw new IllegalStateException(); });
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 2; });
		
		FOption<Try<Integer>> ret;
		Try<Integer> trial;
		
		trial = m_executor.next().get();
		Assert.assertTrue(trial.isFailure());
		Assert.assertEquals(IllegalStateException.class, trial.getCause().getClass());
		
		trial = m_executor.next().get();
		Assert.assertEquals(0, (int)trial.get());
	}
	
	@Test(expected=IllegalStateException.class)
	public void test2() throws Exception {
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 0; });
		m_executor.submit(() -> { return 1; });
		m_executor.close();
		
		m_executor.submit(() -> { MILLISECONDS.sleep(30); return 2; });
	}
}
