package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutionMapTest {
	private EventDrivenExecution<String> m_leader;
	private static final RuntimeException s_cause = new RuntimeException();
	
	@Before
	public void setup() {
		m_leader = new EventDrivenExecution<>();
	}

	@Test
	public void test01() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.mapOnCompleted(new Action());
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);
		assertTrue(m_leader.isCompleted());
		assertTrue(exec.isCompleted());
		assertEquals(3, (int)exec.get());
	}

	@Test
	public void test02() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.mapOnCompleted(new Action());
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyFailed(s_cause));
		sleep(100);
		assertTrue(m_leader.isFailed());
		assertTrue(exec.isFailed());
		assertEquals(s_cause, exec.waitForResult().getCause());
	}

	@Test
	public void test03() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.mapOnCompleted(new Action());
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCancelled());
		sleep(100);
		assertTrue(m_leader.isCancelled());
		assertTrue(exec.isCancelled());
	}
	
	private static class Action implements Function<String,Integer> {
		@Override
		public Integer apply(String str) {
			return str.length();
		}
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
