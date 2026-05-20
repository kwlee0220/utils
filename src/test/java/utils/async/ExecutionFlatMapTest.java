package utils.async;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.op.AsyncExecutions;
import utils.func.Result;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class ExecutionFlatMapTest {
	private EventDrivenExecution<String> m_leader;
	private final Exception m_cause = new Exception();
	private final ExecutionImpl<Integer> m_follower = new ExecutionImpl<>();

	@BeforeEach
	public void setup() {
		m_leader = new EventDrivenExecution<>();
	}

	private static class ExecutionImpl<T> extends EventDrivenExecution<T>
											implements StartableExecution<T> {
		@Override
		public void start() {
			notifyStarted();
		}
	}

	@Test
	public void test21() throws Exception {
		Execution<Integer> exec = AsyncExecutions.flatMap(m_leader, r -> m_follower);
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
		Execution<Integer> exec = AsyncExecutions.flatMap(m_leader, r -> m_follower);
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
		Execution<Integer> exec = AsyncExecutions.flatMap(m_leader, r -> m_follower);
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
		Execution<Integer> exec = AsyncExecutions.flatMap(m_leader, r -> m_follower);
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
		Execution<Integer> exec = AsyncExecutions.flatMap(m_leader, r -> m_follower);
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
	
	private CompletableFutureAsyncExecution<Integer> startCount(Result<String> r) {
		CompletableFutureAsyncExecution<Integer> exec = Executions.supplyAsync(() -> {
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
		// chain이 반환한 follower가 leader 종료 시점에 이미 시작된 상태인 경우,
		// FlatMapAsyncExecution이 follower를 다시 start()하지 않는 분기를 검증.
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);

		boolean ret;

		assertEquals(AsyncState.NOT_STARTED, comp.getState());

		ret = m_leader.notifyStarted();
		sleep(200);
		assertEquals(true, ret);
		assertEquals(AsyncState.RUNNING, comp.getState());
		assertEquals(AsyncState.NOT_STARTED, m_follower.getState());

		m_follower.notifyStarted();
		ret = m_leader.notifyCompleted("abc");
		sleep(100);
		assertEquals(true, ret);
		assertEquals(AsyncState.COMPLETED, m_leader.getState());
		assertEquals(AsyncState.RUNNING, m_follower.getState());

		ret = m_follower.notifyCompleted(3);
		assertEquals(true, ret);
		assertEquals(AsyncState.COMPLETED, comp.getState());
	}

	@Test
	public void test42() throws Exception {
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> startCount(r));
		
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
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> startCount(r));
		
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
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> startCount(r));
		
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
	
	private CompletableFutureAsyncExecution<Integer> nameLength(Result<Person> r) {
		CompletableFutureAsyncExecution<Integer> exec = Executions.supplyAsync(() -> {
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
		Execution<Integer> comp = AsyncExecutions.flatMap(leader, r -> nameLength(r));
		
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
		Execution<Integer> comp = AsyncExecutions.flatMap(leader, r -> nameLength(r));
		
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
	
	// ---------- chain 예외 ----------

	@Test
	public void chain_throws_propagates_unwrapped_failure() throws Exception {
		RuntimeException chainCause = new RuntimeException("chain boom");
		Function<Result<String>, EventDrivenExecution<Integer>> chain = r -> { throw chainCause; };
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, chain);

		assertTrue(m_leader.notifyStarted());
		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(100);

		assertTrue(comp.isFailed());
		assertEquals(chainCause, comp.waitForFinished().getFailureCause());
	}

	// ---------- 비정상 follower ----------

	@Test
	public void non_startable_unstarted_follower_leaves_composite_running() throws Exception {
		// chain이 NOT_STARTED 상태인 plain EventDrivenExecution(=non-Startable)을 반환하면
		// 콜백 안에서 IllegalStateException이 던져진다. 그러나 리스너 디스패처가 try/catch로
		// 삼키므로 호출자에게 전파되지 않고, 합성 실행은 종료 상태로 전이하지 못한 채 RUNNING에 머문다.
		EventDrivenExecution<Integer> nonStartableFollower = new EventDrivenExecution<>();
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> nonStartableFollower);

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, comp.getState());

		assertTrue(m_leader.notifyCompleted("abc"));
		sleep(200);

		// 합성 실행은 RUNNING에 묶여 있고 follower는 시작되지 못한다.
		assertFalse(comp.isDone(), "합성 실행이 종료되어선 안 됨");
		assertEquals(AsyncState.RUNNING, comp.getState());
		assertEquals(AsyncState.NOT_STARTED, nonStartableFollower.getState());
	}

	// ---------- cancel 의미론 ----------

	@Test
	public void cancel_before_start_transitions_to_cancelled() throws Exception {
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);
		assertEquals(AsyncState.NOT_STARTED, comp.getState());

		boolean cancelled = comp.cancel(true);
		assertTrue(cancelled);
		assertTrue(comp.isCancelled());
	}

	@Test
	public void cancel_after_running_does_not_propagate_to_leader_or_follower() throws Exception {
		// FlatMapAsyncExecution은 CancellableWork를 구현하지 않으므로,
		// RUNNING 상태에서의 cancel(true)는 leader/follower에 전파되지 않는다.
		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);

		assertTrue(m_leader.notifyStarted());
		sleep(100);
		assertEquals(AsyncState.RUNNING, comp.getState());

		comp.cancel(true);
		sleep(100);

		// leader는 여전히 RUNNING, follower는 아직 시작되지도 않은 상태여야 한다.
		assertEquals(AsyncState.RUNNING, m_leader.getState());
		assertEquals(AsyncState.NOT_STARTED, m_follower.getState());
	}

	// ---------- 합성 실행 콜백 ----------

	@Test
	public void composite_callbacks_fire_on_successful_completion() throws Exception {
		@SuppressWarnings("unchecked")
		Consumer<Integer> onCompleted = org.mockito.Mockito.mock(Consumer.class);
		Runnable onStarted = org.mockito.Mockito.mock(Runnable.class);
		Runnable onCancelled = org.mockito.Mockito.mock(Runnable.class);
		@SuppressWarnings("unchecked")
		Consumer<Throwable> onFailed = org.mockito.Mockito.mock(Consumer.class);

		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);
		comp.whenStarted(onStarted);
		comp.whenCompleted(onCompleted);
		comp.whenFailed(onFailed);
		comp.whenCancelled(onCancelled);

		m_leader.notifyStarted();
		m_leader.notifyCompleted("abc");
		m_follower.notifyCompleted(7);

		verify(onStarted, timeout(500).times(1)).run();
		verify(onCompleted, timeout(500).times(1)).accept(7);
		verify(onFailed, never()).accept(org.mockito.ArgumentMatchers.any());
		verify(onCancelled, never()).run();
	}

	@Test
	public void composite_callbacks_fire_on_failure() throws Exception {
		@SuppressWarnings("unchecked")
		Consumer<Integer> onCompleted = org.mockito.Mockito.mock(Consumer.class);
		@SuppressWarnings("unchecked")
		Consumer<Throwable> onFailed = org.mockito.Mockito.mock(Consumer.class);

		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);
		comp.whenCompleted(onCompleted);
		comp.whenFailed(onFailed);

		m_leader.notifyStarted();
		m_leader.notifyCompleted("abc");
		m_follower.notifyFailed(m_cause);

		verify(onFailed, timeout(500).times(1)).accept(m_cause);
		verify(onCompleted, never()).accept(org.mockito.ArgumentMatchers.any());
	}

	@Test
	public void composite_callbacks_fire_on_cancel_before_start() throws Exception {
		Runnable onCancelled = org.mockito.Mockito.mock(Runnable.class);
		Runnable onStarted = org.mockito.Mockito.mock(Runnable.class);

		Execution<Integer> comp = AsyncExecutions.flatMap(m_leader, r -> m_follower);
		comp.whenStarted(onStarted);
		comp.whenCancelled(onCancelled);

		comp.cancel(true);

		verify(onCancelled, timeout(500).times(1)).run();
		// NOT_STARTED → CANCELLED는 RUNNING을 거치지 않으므로 onStarted는 호출되지 않는다.
		verify(onStarted, never()).run();
	}

	private static void sleep(long millis) {
		try { Thread.sleep(millis); } catch ( Exception e ) { }
	}
}
