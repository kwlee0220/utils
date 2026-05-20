package utils.async;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
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
public class EventDrivenExecutionTest1 {
	private EventDrivenExecution<String> m_exec;
	private final Exception m_cause = new Exception();
	private volatile int m_tag;
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@BeforeEach
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
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.STARTING, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}

	@Test
	public void test_STARTING_02() throws Exception {
		boolean ret = m_exec.notifyStarted();
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.RUNNING, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}

	@Test
	public void test_STARTING_03() throws Exception {
		Assertions.assertEquals(AsyncState.STARTING, m_exec.getState());
		
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
				Assertions.assertEquals(0, m_tag);
				m_tag = 1;
				m_exec.notifyStarted();
			}
			catch ( InterruptedException e ) { }
		});
		Assertions.assertEquals(0, m_tag);
		Assertions.assertEquals(true, m_exec.notifyCancelling());
		Assertions.assertEquals(1, m_tag);
		Assertions.assertEquals(AsyncState.CANCELLING, m_exec.getState());
		
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
				Assertions.assertEquals(0, m_tag);
				m_tag = 1;
				m_exec.notifyStarted();
			}
			catch ( InterruptedException e ) { }
		});
		Assertions.assertEquals(0, m_tag);
		Assertions.assertEquals(true, m_exec.notifyCancelled());
		Assertions.assertEquals(1, m_tag);
		Assertions.assertEquals(AsyncState.CANCELLED, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, times(1)).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, times(1)).run();
	}

	@Test
	public void test_STARTING_05() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			m_exec.notifyCompleted("ok");
			});
	}
	
	@Test
	public void test_STARTING_06() throws Exception {
		boolean ret = m_exec.notifyFailed(m_cause);
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.FAILED, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, times(1)).accept(m_cause);
	}
}
