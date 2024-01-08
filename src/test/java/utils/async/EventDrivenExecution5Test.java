package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class EventDrivenExecution5Test {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@Before
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
		Assert.assertEquals(false, ret);
		Assert.assertEquals(AsyncState.RUNNING, m_exec.getState());
	}

	@Test
	public void test_CANCELLED_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		Assert.assertEquals(true, ret);
	}

	@Test
	public void test_CANCELLED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		Assert.assertEquals(true, ret);
	}

	@Test
	public void test_CANCELLED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		Assert.assertEquals(true, ret);
		Assert.assertEquals(AsyncState.CANCELLED, m_exec.getState());
		
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
		Assert.assertEquals(false, ret);
		Assert.assertEquals(AsyncState.CANCELLED, m_exec.getState());
	}

	@Test
	public void test_CANCELLED_06() throws Exception {
		m_exec.notifyCancelled();
		
		boolean ret = m_exec.notifyFailed(m_cause);
		Assert.assertEquals(false, ret);
		Assert.assertEquals(AsyncState.CANCELLED, m_exec.getState());
	}
}
