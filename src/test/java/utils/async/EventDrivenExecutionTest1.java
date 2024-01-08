package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
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
public class EventDrivenExecutionTest1 {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	private volatile int m_tag;
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@Before
	public void setup() {
		m_exec = new EventDrivenExecution<>();
		m_exec.notifyStarting();
		
		m_exec.whenStartedAsync(m_startListener);
		m_exec.whenCompleted(m_completeListener);
		m_exec.whenCancelled(m_cancelListener);
		m_exec.whenFailed(m_failureListener);
		m_tag = 0;
	}

	@Test
	public void test_STARTING_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.STARTING));
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}

	@Test
	public void test_STARTING_02() throws Exception {
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
	public void test_STARTING_03() throws Exception {
		assertThat(m_exec.getState(), is(AsyncState.STARTING));
		
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
				assertThat(m_tag, is(0));
				m_tag = 1;
				m_exec.notifyStarted();
			}
			catch ( InterruptedException e ) { }
		});
		assertThat(m_tag, is(0));
		assertThat(m_exec.notifyCancelling(), is(true));
		assertThat(m_tag, is(1));
		assertThat(m_exec.getState(), is(AsyncState.CANCELLING));
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
	}

	@Test
	public void test_STARTING_04() throws Exception {
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
				assertThat(m_tag, is(0));
				m_tag = 1;
				m_exec.notifyStarted();
			}
			catch ( InterruptedException e ) { }
		});
		assertThat(m_tag, is(0));
		assertThat(m_exec.notifyCancelled(), is(true));
		assertThat(m_tag, is(1));
		assertThat(m_exec.getState(), is(AsyncState.CANCELLED));
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, times(1)).run();
	}

	@Test(expected = IllegalStateException.class)
	public void test_STARTING_05() throws Exception {
		m_exec.notifyCompleted("ok");
	}
	
	@Test
	public void test_STARTING_06() throws Exception {
		boolean ret = m_exec.notifyFailed(m_cause);
		assertThat(ret, is(true));
		assertThat(m_exec.getState(), is(AsyncState.FAILED));
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, times(1)).accept(m_cause);
	}
}
