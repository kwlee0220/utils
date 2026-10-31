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
public class EventDrivenExecution0Test {
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
		m_exec.whenFinishedAsync(ret -> {
			ret.ifSuccessful(m_completeListener)
				.ifFailed(m_failureListener)
				.ifNone(m_cancelListener);
		});
	}
	
	@Test
	public void test_NOT_STARTED_01() throws Exception {
		boolean ret = m_exec.notifyStarting();
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.STARTING, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
	}
	
	@Test
	public void test_NOT_STARTED_02() throws Exception {
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
	public void test_NOT_STARTED_03() throws Exception {
		boolean ret = m_exec.notifyCancelling();
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.CANCELLING, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, never()).run();
		verify(m_failureListener, never()).accept(any());
	}
	
	@Test
	public void test_NOT_STARTED_04() throws Exception {
		boolean ret = m_exec.notifyCancelled();
		Assertions.assertEquals(true, ret);
		Assertions.assertEquals(AsyncState.CANCELLED, m_exec.getState());
		
		Thread.sleep(100);
		verify(m_startListener, never()).run();
		verify(m_completeListener, never()).accept(anyString());
		verify(m_cancelListener, times(1)).run();
	}
	
	@Test
	public void test_NOT_STARTED_05() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			m_exec.notifyCompleted("ok");
			});
	}
	
	@Test
	public void test_NOT_STARTED_06() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			m_exec.notifyFailed(m_cause);
			});
	}

	@Test
	public void notifyFailed_null_cause_still_runs_finish_listener() {
		// 로그에서 cause.toString()을 호출하여 NPE가 나면 종료 리스너가 건너뛰어지던 문제의 회귀 테스트.
		java.util.concurrent.atomic.AtomicInteger finished = new java.util.concurrent.atomic.AtomicInteger();
		m_exec.whenFinished(result -> finished.incrementAndGet());
		m_exec.notifyStarting();
		m_exec.notifyStarted();

		Assertions.assertTrue(m_exec.notifyFailed(null));
		Assertions.assertEquals(AsyncState.FAILED, m_exec.getState());
		Assertions.assertEquals(1, finished.get());
	}
}
