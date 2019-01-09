package utils.react;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import utils.async.AsyncExecution;
import utils.async.ExecutionProgress;
import utils.async.op.AsyncExecutions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ObservablesTest {
	private ScheduledExecutorService m_scheduler;
	private final Exception m_cause = new Exception();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	@Mock io.reactivex.functions.Consumer<ExecutionProgress<String>> m_onNextHandler;
	@Mock io.reactivex.functions.Consumer<Throwable> m_onErrorHandler;
	@Mock Action m_onCompleteHandler;
	@Mock io.reactivex.functions.Consumer<Integer> m_onNextHandler2;
	
	@Before
	public void setup() {
		m_scheduler = Executors.newScheduledThreadPool(4);
	}
	
	private void setListeners(AsyncExecution<String> exec) {
		exec.whenStarted(m_startListener);
		exec.whenCompleted(m_completeListener);
		exec.whenCancelled(m_cancelListener);
		exec.whenFailed(m_failureListener);
	}
	
	@Test
	public void test01() throws Exception {
		AsyncExecution<String> exec = AsyncExecutions.idle("RESULT", 300,
													TimeUnit.MILLISECONDS, m_scheduler);
		setListeners(exec);
		
		Observable<ExecutionProgress<String>> ob = exec.observe(false);
		ob.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);
		
		verify(m_onNextHandler, never()).accept(any());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();
		
		InOrder order = Mockito.inOrder(m_onNextHandler);
		
		exec.start();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.started());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();
		
		exec.waitForDone();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.completed("RESULT"));
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, times(1)).run();
	}
	
	@Test
	public void test02() throws Exception {
		AsyncExecution<String> exec1 = AsyncExecutions.idle("RESULT", 300,
													TimeUnit.MILLISECONDS, m_scheduler);
		AsyncExecution<String> exec2 = AsyncExecutions.cancelled();
		AsyncExecution<String> exec = AsyncExecutions.sequential(exec1, exec2);
		setListeners(exec);

		Observable<ExecutionProgress<String>> ob = exec.observe(false);
		ob.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);
		
		InOrder order = Mockito.inOrder(m_onNextHandler);
		
		exec.start();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.started());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();

		exec.waitForDone();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.cancelled());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, times(1)).run();
	}

	@Test
	public void test03() throws Exception {
		AsyncExecution<String> exec1 = AsyncExecutions.idle("RESULT", 300,
													TimeUnit.MILLISECONDS, m_scheduler);
		AsyncExecution<String> exec2 = AsyncExecutions.failure(m_cause);
		AsyncExecution<String> exec = AsyncExecutions.sequential(exec1, exec2);
		setListeners(exec);

		Observable<ExecutionProgress<String>> ob = exec.observe(false);
		ob.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);
		
		InOrder order = Mockito.inOrder(m_onNextHandler);
		
		exec.start();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.started());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();
		
		exec.waitForDone();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.failed(m_cause));
		verify(m_onErrorHandler, times(1)).accept(m_cause);
		verify(m_onCompleteHandler, never()).run();
	}

	@Test
	public void test04() throws Exception {
		AsyncExecution<String> exec = AsyncExecutions.idle("RESULT", 300,
													TimeUnit.MILLISECONDS, m_scheduler);
		setListeners(exec);

		Observable<ExecutionProgress<String>> ob = exec.observe(true);
		Disposable dis = ob.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);
		
		InOrder order = Mockito.inOrder(m_onNextHandler);
		
		exec.start();
		TimeUnit.MILLISECONDS.sleep(50);
		order.verify(m_onNextHandler).accept(ExecutionProgress.started());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();
		
		dis.dispose();
		TimeUnit.MILLISECONDS.sleep(50);

		Assert.assertTrue(exec.isCancelled());
		verify(m_cancelListener, times(1)).run();
		order.verify(m_onNextHandler, never()).accept(any());
		verify(m_onErrorHandler, never()).accept(any());
		verify(m_onCompleteHandler, never()).run();
	}
}
