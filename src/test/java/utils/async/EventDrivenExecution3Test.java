package utils.async;


import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class EventDrivenExecution3Test {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@BeforeEach
	public void setup() {
		m_exec = new EventDrivenExecution<>();
		m_exec.notifyStarted();
		m_exec.notifyCompleted("ok");
		
		m_exec.whenStartedAsync(m_startListener);
		m_exec.whenCompleted(m_completeListener);
		m_exec.whenCancelled(m_cancelListener);
		m_exec.whenFailed(m_failureListener);
	}

	@Test
	public void test_COMPLETED_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.COMPLETED, m_exec.getState());
	}

	@Test
	public void test_COMPLETED_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		Assertions.assertEquals(false, ret);
	}

	@Test
	public void test_COMPLETED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.COMPLETED, m_exec.getState());
	}

	@Test
	public void test_COMPLETED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.COMPLETED, m_exec.getState());
	}

	@Test
	public void test_COMPLETED_05() throws Exception {
		boolean ret = m_exec.notifyCompleted("ok");
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.COMPLETED, m_exec.getState());
	}

	@Test
	public void test_COMPLETED_06() throws Exception {
		boolean ret = m_exec.notifyFailed(m_cause);
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.COMPLETED, m_exec.getState());
	}
}
