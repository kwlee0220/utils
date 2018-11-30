package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.Execution.State;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class EventDrivenExecution3Test {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@Before
	public void setup() {
		m_exec = new EventDrivenExecution<>();
		m_exec.notifyStarted();
		m_exec.notifyCompleted("ok");
		
		m_exec.whenStarted(m_startListener);
		m_exec.whenCompleted(m_completeListener);
		m_exec.whenCancelled(m_cancelListener);
		m_exec.whenFailed(m_failureListener);
	}

	@Test
	public void test_COMPLETED_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		assertThat(ret, is(false));
		assertThat(m_exec.getState(), is(State.COMPLETED));
	}

	@Test
	public void test_COMPLETED_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		assertThat(ret, is(false));
	}

	@Test
	public void test_COMPLETED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		assertThat(ret, is(false));
		assertThat(m_exec.getState(), is(State.COMPLETED));
	}

	@Test
	public void test_COMPLETED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		assertThat(ret, is(false));
		assertThat(m_exec.getState(), is(State.COMPLETED));
	}

	@Test
	public void test_COMPLETED_05() throws Exception {
		boolean ret = m_exec.notifyCompleted("ok");
		assertThat(ret, is(false));
		assertThat(m_exec.getState(), is(State.COMPLETED));
	}

	@Test
	public void test_COMPLETED_06() throws Exception {
		boolean ret = m_exec.notifyFailed(m_cause);
		assertThat(ret, is(false));
		assertThat(m_exec.getState(), is(State.COMPLETED));
	}
}
