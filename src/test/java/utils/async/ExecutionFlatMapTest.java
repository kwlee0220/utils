package utils.async;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.Result;

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
		
		m_leader.whenStartedAsync(m_startListener);
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
		
		assertEquals(comp.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.RUNNING);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(m_leader.getState(), AsyncState.COMPLETED);
		assertEquals(follower.getState(), AsyncState.RUNNING);
		
		ret = follower.notifyCompleted(3);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.COMPLETED);
	}

	@Test
	public void test02() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertEquals(comp.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.RUNNING);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyFailed(m_cause);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(m_leader.getState(), AsyncState.FAILED);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		assertEquals(comp.getState(), AsyncState.FAILED);
	}

	@Test
	public void test03() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertEquals(comp.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.RUNNING);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyCancelled();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(m_leader.getState(), AsyncState.CANCELLED);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		assertEquals(comp.getState(), AsyncState.CANCELLED);
	}

	@Test
	public void test04() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertEquals(comp.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.RUNNING);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(m_leader.getState(), AsyncState.COMPLETED);
		assertEquals(follower.getState(), AsyncState.RUNNING);
		
		ret = follower.notifyFailed(m_cause);
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.FAILED);
	}

	@Test
	public void test05() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		EventDrivenExecution<Integer> comp = m_leader.flatMapOnCompleted(str -> {
			follower.notifyStarted();
			return follower;
		});
		
		boolean ret;
		
		assertEquals(comp.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyStarted();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.RUNNING);
		assertEquals(follower.getState(), AsyncState.NOT_STARTED);
		
		ret = m_leader.notifyCompleted("abc");
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(m_leader.getState(), AsyncState.COMPLETED);
		assertEquals(follower.getState(), AsyncState.RUNNING);
		
		ret = follower.notifyCancelled();
		try { Thread.sleep(100); } catch ( Exception e ) { }
		assertEquals(ret, true);
		assertEquals(comp.getState(), AsyncState.CANCELLED);
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
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
		Execution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
		Execution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
		Execution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
		Execution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
		assertEquals(m_cause, exec.waitForFinished().getFailureCause());
	}

	@Test
	public void test32() throws Exception {
		Execution<Integer> exec = m_leader.flatMap(r -> m_follower);
		assertEquals(exec.getState(), AsyncState.NOT_STARTED);

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
	
	private StartableExecution<Integer> startCount(Result<String> r) {
		StartableExecution<Integer> exec = Executions.supplyAsync(() -> {
			if ( r.isSuccessful() ) {
				String output = r.getUnchecked();
				sleep(1000);
				
				return output.length();
			}
			else if ( r.isFailed() ) {
				sleep(1000);
				return -1;
			}
			else if ( r.isNone() ) {
				sleep(1000);
				return 0;
			}
			else {
				throw new AssertionError();
			}
		});
		exec.start();
		return exec;
	}

	@Test
	public void test41() throws Exception {
		EventDrivenExecution<Integer> follower = new EventDrivenExecution<>();
		Execution<Integer> comp = m_leader.flatMap(r -> follower);
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = m_leader.notifyStarted();
		sleep(1000);
		assertEquals(true, ret);
		assertEquals(AsyncState.RUNNING, comp.getState());
		assertEquals(AsyncState.NOT_STARTED, follower.getState());
		
		follower.notifyStarted();
		ret = m_leader.notifyCompleted("abc");
		sleep(1000);
		assertEquals(true, ret);
		assertEquals(AsyncState.COMPLETED, m_leader.getState());
		assertEquals(AsyncState.RUNNING, follower.getState());
		
		ret = follower.notifyCompleted(3);
		sleep(100);
		assertEquals(true, ret);
		assertEquals(AsyncState.COMPLETED, comp.getState());
	}

	@Test
	public void test42() throws Exception {
		Execution<Integer> comp = m_leader.flatMap(r -> startCount(r));
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = m_leader.notifyStarted();
		assertEquals(true, ret);
		sleep(500);
		
		ret = m_leader.notifyCompleted("abc");
		assertEquals(true, ret);
		sleep(500);
		assertTrue(comp.isRunning());
		assertTrue(comp.poll().isRunning());
		sleep(700);
		
		AsyncResult<Integer> aret = comp.poll();
		assertTrue(aret.isCompleted());
		assertEquals(3, (int)aret.get());
	}

	@Test
	public void test43() throws Exception {
		Execution<Integer> comp = m_leader.flatMap(r -> startCount(r));
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = m_leader.notifyStarted();
		ret = m_leader.notifyFailed(new Exception());
		assertEquals(true, ret);
		sleep(500);
		assertEquals(AsyncState.RUNNING, comp.getState());
		assertTrue(comp.poll().isRunning());
		sleep(700);
		
		AsyncResult<Integer> aret = comp.poll();
		assertTrue(aret.isCompleted());
		assertEquals(-1, (int)aret.get());
	}

	@Test
	public void test44() throws Exception {
		Execution<Integer> comp = m_leader.flatMap(r -> startCount(r));
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = m_leader.notifyStarted();
		ret = m_leader.notifyCancelled();
		assertEquals(true, ret);
		sleep(500);
		assertEquals(AsyncState.RUNNING, comp.getState());
		assertTrue(comp.poll().isRunning());
		sleep(700);
		
		AsyncResult<Integer> aret = comp.poll();
		assertTrue(aret.isCompleted());
		assertEquals(0, (int)aret.get());
	}
	
	static class Person {
		protected final String m_name;
		
		Person(String name) {
			m_name = name;
		}
		
		String getName() {
			return m_name;
		}
		
		@Override
		public String toString() {
			return String.format("Person(%s)", m_name);
		}
	}
	
	static class Student extends Person {
		private final float  m_gpa;
		
		Student(String name, float gpa) {
			super(name);
			
			m_gpa = gpa;
		}
		
		@Override
		public String toString() {
			return String.format("Student(%s, %f)", m_name, m_gpa);
		}
	}
	
	private StartableExecution<Integer> nameLength(Result<Person> r) {
		StartableExecution<Integer> exec = Executions.supplyAsync(() -> {
			if ( r.isSuccessful() ) {
				Person p = r.getUnchecked();
				sleep(1000);
				
				return p.getName().length();
			}
			else if ( r.isFailed() ) {
				sleep(1000);
				return -1;
			}
			else if ( r.isNone() ) {
				sleep(1000);
				return 0;
			}
			else {
				throw new AssertionError();
			}
		});
		exec.start();
		return exec;
	}

	@Test
	public void test45() throws Exception {
		EventDrivenExecution<Person> leader = new EventDrivenExecution<>();
		Execution<Integer> comp = leader.flatMap(r -> nameLength(r));
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = leader.notifyStarted();
		assertEquals(true, ret);
		sleep(500);
		
		Person p = new Person("Tom");
		
		ret = leader.notifyCompleted(p);
		assertEquals(true, ret);
		sleep(500);
		assertTrue(comp.isRunning());
		assertTrue(comp.poll().isRunning());
		sleep(700);
		
		AsyncResult<Integer> aret = comp.poll();
		assertTrue(aret.isCompleted());
		assertEquals(3, (int)aret.get());
	}

	@Test
	public void test46() throws Exception {
		EventDrivenExecution<Person> leader = new EventDrivenExecution<>();
		Execution<Integer> comp = leader.flatMap(r -> nameLength(r));
		
		boolean ret;
		
		assertEquals(AsyncState.NOT_STARTED, comp.getState());
		
		ret = leader.notifyStarted();
		assertEquals(true, ret);
		sleep(500);
		
		Student p = new Student("Tom", 3.5f);
		
		ret = leader.notifyCompleted(p);
		assertEquals(true, ret);
		sleep(500);
		assertTrue(comp.isRunning());
		assertTrue(comp.poll().isRunning());
		sleep(700);
		
		AsyncResult<Integer> aret = comp.poll();
		assertTrue(aret.isCompleted());
		assertEquals(3, (int)aret.get());
	}
	
	private static void sleep(long millis) {
		try { Thread.sleep(millis); } catch ( Exception e ) { }
	}
}
