package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

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
public class EventDrivenExecution0Test {
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
		m_exec.whenFinishedAsync(ret -> {
			ret.ifSuccessful(m_completeListener)
				.ifFailed(m_failureListener)
				.ifNone(m_cancelListener);
		});
	}
	
	@Test
	public void test_NOT_STARTED_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.STARTING));
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
	}
	
	@Test
	public void test_NOT_STARTED_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.RUNNING));
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}
	
	@Test
	public void test_NOT_STARTED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.CANCELLING));
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}
	
	@Test
	public void test_NOT_STARTED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.CANCELLED));
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, times(1)).run();
	}
	
	@Test(expected = IllegalStateException.class)
	public void test_NOT_STARTED_05() throws Exception {
		m_exec.notifyCompleted("ok");
	}
	
	@Test(expected = IllegalStateException.class)
	public void test_NOT_STARTED_06() throws Exception {
		m_exec.notifyFailed(m_cause);
	}
}
