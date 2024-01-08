package utils.async;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class DependOnTest {
	private EventDrivenExecution<String> m_exec;
	private EventDrivenExecution<String> m_dep = new EventDrivenExecution<>();
	private final Consumer<Try<String>> m_handler = new Consumer<Try<String>>() {
		@Override
		public void accept(Try<String> res) {
			res.ifSuccessful(m_onCompleted)
				.ifFailed(ex -> {
					if ( ex instanceof CancellationException ) {
						m_onCancelled.run();
					}
				});
		}
	};
	@Mock Runnable m_onStarted;
	@Mock Runnable m_onCancelled;
	@Mock Consumer<String> m_onCompleted;
	
	@Before
	public void setup() {
		m_exec = new EventDrivenExecution<>();
		
		m_dep = new EventDrivenExecution<>();
		m_dep.whenStartedAsync(m_onStarted);
		m_dep.whenCancelled(m_onCancelled);
		m_dep.whenCompleted(m_onCompleted);
	}
	
	@Test
	public void test01() throws Exception {
		m_dep.dependsOn(m_exec, "done");
		verify(m_onStarted, times(0)).run();
		
		m_exec.notifyStarted();
		MILLISECONDS.sleep(50);
		verify(m_onStarted, times(1)).run();
		Assert.assertTrue(m_dep.isStarted());
		
		m_exec.notifyCompleted("xxx");
		MILLISECONDS.sleep(50);
		verify(m_onCompleted, times(1)).accept("done");
		Assert.assertTrue(m_dep.isCompleted());
	}
	
	@Test
	public void test02() throws Exception {
		m_dep.dependsOn(m_exec, "done");
		verify(m_onStarted, times(0)).run();
		
		m_exec.notifyStarted();
		MILLISECONDS.sleep(50);
		verify(m_onStarted, times(1)).run();
		Assert.assertTrue(m_dep.isStarted());
		
		m_exec.notifyCancelled();
		MILLISECONDS.sleep(50);
		verify(m_onCancelled, times(1)).run();
		Assert.assertTrue(m_dep.isCancelled());
	}
}
