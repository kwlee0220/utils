package utils.thread;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.FOption;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class CompletionFStreamTest {
	private Executor m_executor = Executors.newFixedThreadPool(5);
	private CompletionFStream<Integer> m_completions = new CompletionFStream<>(5);
	
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(300); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 2; }, m_executor);
		
		FOption<Result<Integer>> ret;
		
		ret = m_completions.next();
		Assert.assertEquals(1, (int)ret.get().get());
		
		ret = m_completions.next();
		Assert.assertEquals(2, (int)ret.get().get());
		
		ret = m_completions.next();
		Assert.assertEquals(0, (int)ret.get().get());
		
		try {
			m_completions.next(10, MILLISECONDS);
			Assert.fail("should throw TimeoutException");
		}
		catch ( TimeoutException expected ) { }
		
		m_completions.endOfSupply();
		ret = m_completions.next(0, MILLISECONDS);
		Assert.assertTrue(ret.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 0; }, m_executor);
		m_completions.submit(() -> { throw new IllegalStateException(); }, m_executor);
		Thread.sleep(200);
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 2; }, m_executor);
		
		Result<Integer> trial;
		
		trial = m_completions.next().get();
		Assert.assertTrue(trial.isFailed());
		Assert.assertEquals(IllegalStateException.class, trial.getCause().getClass());
		
		trial = m_completions.next().get();
		Assert.assertEquals(0, (int)trial.get());
	}
	
	@Test
	public void test2() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.close();
		
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 2; }, m_executor);
	}
	
	@Test
	public void test3() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.endOfSupply();
		
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 2; }, m_executor);
	}
}
