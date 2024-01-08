package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

import utils.Holder;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ExecutionMapTest {
	private EventDrivenExecution<String> m_leader;
	private static final RuntimeException s_cause = new RuntimeException();
	
	private final static Holder<Long> s_workerThreadId = Holder.of(-1L);
	private static class Action implements Function<String,Integer> {
		@Override
		public Integer apply(String str) {
			s_workerThreadId.set(Thread.currentThread().getId());
			return str.length();
		}
	}
	
	@Before
	public void setup() {
		m_leader = new EventDrivenExecution<>();
	}

	@Test
	public void test01() throws Exception {
		Execution<Integer> exec = m_leader.map(new Action());
		assertEquals(AsyncState.NOT_STARTED, exec.getState());

		assertTrue(m_leader.notifyStarted());
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		assertTrue(m_leader.isCompleted());
		assertTrue(exec.isCompleted());
		assertEquals(3, (int)exec.get());
		assertEquals(Thread.currentThread().getId(), (long)s_workerThreadId.get());
	}

	@Test
	public void test02() throws Exception {
		Execution<Integer> exec = m_leader.map(new Action());
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyFailed(s_cause));
		sleep(100);
		assertTrue(m_leader.isFailed());
		assertTrue(exec.isFailed());
		assertEquals(s_cause, exec.waitForFinished().getFailureCause());
	}

	@Test
	public void test03() throws Exception {
		Execution<Integer> exec = m_leader.map(new Action());
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCancelled());
		sleep(100);
		assertTrue(m_leader.isCancelled());
		assertTrue(exec.isCancelled());
	}
	
	private static class FailureAction implements Function<String,Integer> {
		@Override
		public Integer apply(String str) {
			throw s_cause;
		}
	}
	
	private void sleep(long millis) {
		try { Thread.sleep(millis); } catch ( Exception e ) { }
	}
}
