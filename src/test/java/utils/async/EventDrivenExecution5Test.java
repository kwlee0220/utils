package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
public class EventDrivenExecution5Test {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@BeforeEach
	public void setup() {
		m_exec = new EventDrivenExecution<>();
		m_exec.whenStartedAsync(m_startListener);
		m_exec.whenCompleted(m_completeListener);
		m_exec.whenCancelled(m_cancelListener);
		m_exec.whenFailed(m_failureListener);
		
		m_exec.notifyStarted();
	}

	@Test
	public void test_CANCELLED_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.RUNNING, m_exec.getState());
	}

	@Test
	public void test_CANCELLED_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		Assertions.assertEquals(true, ret);
	}

	@Test
	public void test_CANCELLED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		Assertions.assertEquals(true, ret);
	}

	@Test
	public void test_CANCELLED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.CANCELLED, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, times(1)).run();
		verify(m_failureListener, never()).accept(any());
	}

	@Test
	public void test_CANCELLED_05() throws Exception {
		m_exec.notifyCancelled();
		
		boolean ret = m_exec.notifyCompleted("ok");
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.CANCELLED, m_exec.getState());
	}

	@Test
	public void test_CANCELLED_06() throws Exception {
		m_exec.notifyCancelled();
		
		boolean ret = m_exec.notifyFailed(m_cause);
		Assertions.assertEquals(false, ret);
		Assertions.assertEquals(AsyncState.CANCELLED, m_exec.getState());
	}
}
