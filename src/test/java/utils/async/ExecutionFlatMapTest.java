package utils.async;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
public class ExecutionFlatMapTest {
	private EventDrivenExecution<String> m_leader;
	private final Exception m_cause = new Exception();
	private final ExecutionImpl<Integer> m_follower = new ExecutionImpl<>();
	
	@Mock Runnable m_startListener;
	@Mock Consumer<String> m_completeListener;
	@Mock Runnable m_cancelListener;
	@Mock Consumer<Throwable> m_failureListener;
	
	@Before
	public void setup() {
		m_leader = new EventDrivenExecution<>();
		
		m_leader.whenStarted(m_startListener);
		m_leader.whenCompleted(m_completeListener);
		m_leader.whenCancelled(m_cancelListener);
		m_leader.whenFailed(m_failureListener);
	}

	@Test
	public void test01() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertThat(comp.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.RUNNING));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(m_leader.getState(), is(AsyncState.COMPLETED));
		assertThat(follower.getState(), is(AsyncState.RUNNING));
		
		ret = follower.notifyCompleted(3);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.COMPLETED));
	}

	@Test
	public void test02() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertThat(comp.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.RUNNING));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyFailed(m_cause);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(m_leader.getState(), is(AsyncState.FAILED));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		assertThat(comp.getState(), is(AsyncState.FAILED));
	}

	@Test
	public void test03() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertThat(comp.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.RUNNING));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyCancelled();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(m_leader.getState(), is(AsyncState.CANCELLED));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		assertThat(comp.getState(), is(AsyncState.CANCELLED));
	}

	@Test
	public void test04() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertThat(comp.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.RUNNING));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(m_leader.getState(), is(AsyncState.COMPLETED));
		assertThat(follower.getState(), is(AsyncState.RUNNING));
		
		ret = follower.notifyFailed(m_cause);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.FAILED));
	}

	@Test
	public void test05() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertThat(comp.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.RUNNING));
		assertThat(follower.getState(), is(AsyncState.NOT_STARTED));
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(m_leader.getState(), is(AsyncState.COMPLETED));
		assertThat(follower.getState(), is(AsyncState.RUNNING));
		
		ret = follower.notifyCancelled();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertThat(ret, is(true));
		assertThat(comp.getState(), is(AsyncState.CANCELLED));
	}
	
	private static class ExecutionImpl<T> extends EventDrivenExecution<T>
											implements StartableExecution<T> {
		@Override
		public void start() {
			notifyStarted();
		}
	}

	@Test
	public void test11() throws Exception {
		EventDrivenExecution<Integer> follower = new ExecutionImpl<>();
		EventDrivenExecution<Integer> exec = m_leader.flatMapOnCompleted(str -> follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());
		assertEquals(AsyncState.NOT_STARTED, follower.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);
		assertTrue(m_leader.isCompleted());
		assertTrue(follower.isStarted());

		assertTrue(follower.notifyCompleted(3));
		sleep(100);
		assertTrue(exec.isCompleted());
	}

	@Test
	public void test21() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);
		assertTrue(m_leader.isCompleted());
		assertTrue(m_follower.isStarted());

		assertTrue(m_follower.notifyCompleted(3));
		sleep(100);
		assertTrue(exec.isCompleted());
		assertEquals(3, (int)exec.get());
	}

	@Test
	public void test22() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyFailed(m_cause));
		sleep(100);
		assertTrue(m_leader.isFailed());
		assertTrue(m_follower.isStarted());

		assertTrue(m_follower.notifyCompleted(-1));
		sleep(100);
		assertTrue(exec.isCompleted());
		assertEquals(-1, (int)exec.get());
	}

	@Test
	public void test23() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCancelled());
		sleep(100);
		assertTrue(m_leader.isCancelled());
		assertTrue(m_follower.isStarted());

		assertTrue(m_follower.notifyCompleted(0));
		sleep(100);
		assertTrue(exec.isCompleted());
		assertEquals(0, (int)exec.get());
	}

	@Test
	public void test31() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);
		assertTrue(m_leader.isCompleted());
		assertTrue(m_follower.isStarted());

		assertTrue(m_follower.notifyFailed(m_cause));
		sleep(100);
		assertTrue(exec.isFailed());
		assertEquals(m_cause, exec.pollInfinite().getCause());
	}

	@Test
	public void test32() throws Exception {
		EventDrivenExecution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertThat(exec.getState(), is(AsyncState.NOT_STARTED));

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, exec.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);
		assertTrue(m_leader.isCompleted());
		assertTrue(m_follower.isStarted());

		assertTrue(m_follower.notifyCancelled());
		sleep(100);
		assertTrue(exec.isCancelled());
	}
	
	private void sleep(long millis) {
		try { Thread.sleep(millis); } catch ( Exception e ) { }
	}
}
