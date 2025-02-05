package utils.async;


import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.async.op.AsyncExecutions;
import utils.func.CheckedRunnable;
import utils.func.Unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutionsTest {
	private boolean m_started;
	private boolean m_finished;
	private boolean m_completed;
	private boolean m_failed;
	private boolean m_cancelled;
	private Object m_result;
	private Throwable m_cause;
	
	@Before
	public void setup() {
		m_started = false;
		m_finished = false;
		m_completed = false;
		m_failed = false;
		m_cancelled = false;
		m_result = null;
		m_cause = null;
	}
	
	private final CheckedRunnable TASK_COMPLETE = new CheckedRunnable() {
		@Override
		public void run() throws InterruptedException {
			Thread.sleep(700);
		}
	};
	private final CheckedRunnable TASK_FAILED = new CheckedRunnable() {
		@Override
		public void run() throws InterruptedException {
			Thread.sleep(700);
			throw new IllegalStateException("failed");
		}
	};
	private final CheckedRunnable TASK_CANCEL = new CheckedRunnable() {
		@Override
		public void run() throws InterruptedException {
			Thread.sleep(700);
			throw new CancellationException("cancelled");
		}
	};
	
	private final Supplier<String> SUPPLY_COMPLETE = new Supplier<String>() {
		@Override
		public String get() {
			Unchecked.runOrRTE(() -> Thread.sleep(700));
			return "completed";
		}
	};
	private final Supplier<String> SUPPLY_FAIL = new Supplier<String>() {
		@Override
		public String get() {
			Unchecked.runOrRTE(() -> Thread.sleep(700));
			throw new IllegalStateException("failed");
		}
	};
	private final Supplier<String> SUPPLY_CANCEL = new Supplier<String>() {
		@Override
		public String get() {
			Unchecked.runOrRTE(() -> Thread.sleep(700));
			throw new CancellationException("cancelled");
		}
	};
	
	@Test
	public void testRunAsync01() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(TASK_COMPLETE);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isCompleted() && exec.isDone());
		Assert.assertTrue(m_finished && m_completed);
		Assert.assertEquals(m_result, exec.get());
	}
	
	@Test
	public void testRunAsync02() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(TASK_FAILED);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isFailed() && exec.isDone());
		Assert.assertTrue(m_finished && m_failed);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( ExecutionException expected ) {
			Assert.assertTrue(expected.getCause() instanceof IllegalStateException);
			Assert.assertTrue(m_cause instanceof IllegalStateException);
		}
	}
	
	@Test
	public void testRunAsync03() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(TASK_CANCEL);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isCancelled() && exec.isDone());
		Assert.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( CancellationException expected ) { }
	}
	
	@Test
	public void testRunAsync04() throws Exception {
		StartableExecution<Void> exec = Executions.toExecution(TASK_COMPLETE);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		boolean cancelled = exec.cancel(true);
		Assert.assertTrue(cancelled);
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertEquals(true, exec.isCancelled() && exec.isDone());
		Assert.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( CancellationException expected ) { }
	}
	
	@Test
	public void testSupplyAsync01() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLY_COMPLETE);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isCompleted() && exec.isDone());
		Assert.assertTrue(m_finished && m_completed);
		Assert.assertEquals("completed", exec.get());
	}
	
	@Test
	public void testSupplyAsync02() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLY_FAIL);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isFailed() && exec.isDone());
		Assert.assertTrue(m_finished && m_failed);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( ExecutionException expected ) { }
	}
	
	@Test
	public void testSupplyAsync03() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLY_CANCEL);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());
		
		Unchecked.runOrRTE(() -> Thread.sleep(700));
		Assert.assertEquals(true, exec.isCancelled() && exec.isDone());
		Assert.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( CancellationException expected ) { }
	}
	
	@Test
	public void testSupplyAsync04() throws Exception {
		CompletableFutureAsyncExecution<String> exec = Executions.supplyAsync(SUPPLY_CANCEL);
		exec.whenStartedAsync(() -> m_started = true);
		exec.whenCompleted((r) -> { m_finished = true; m_completed = true; m_result=r; });
		exec.whenFailed((ex) -> { m_finished = true; m_failed = true; m_cause = ex;});
		exec.whenCancelled(() -> { m_finished = true; m_cancelled = true; });
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isRunning());
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertTrue(m_started);
		Unchecked.runOrRTE(() -> Thread.sleep(300));
		Assert.assertEquals(true, exec.isRunning());

		boolean cancelled = exec.cancel(true);
		Assert.assertTrue(cancelled);
		Unchecked.runOrRTE(() -> Thread.sleep(100));
		Assert.assertEquals(true, exec.isCancelled() && exec.isDone());
		Assert.assertTrue(m_finished && m_cancelled);
		try {
			exec.get();
			Assert.fail();
		}
		catch ( CancellationException expected ) { }
	}
	
	@Test
	public void testNop() throws Exception {
		AbstractAsyncExecution<String> exec = AsyncExecutions.nop("abc");
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals("abc", exec.get());
	}
	
	@Test
	public void testIdle01() throws Exception {
		StartableExecution<Void> exec = AsyncExecutions.idle(Duration.ofMillis(300));
		
		Assert.assertEquals(AsyncState.NOT_STARTED, exec.getState());
		
		long started = System.currentTimeMillis();
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		exec.waitForFinished();
		
		Assert.assertEquals(true, exec.isCompleted());
		Assert.assertEquals(null, exec.get());
		Assert.assertTrue(System.currentTimeMillis() - started >= 300);
	}
	
	@Test
	public void testIdle02() throws Exception {
		StartableExecution<Void> exec = AsyncExecutions.idle(Duration.ofMillis(300));
		
		exec.start();
		Assert.assertEquals(true, exec.isStarted());
		
		boolean done = exec.cancel(true);
		Assert.assertEquals(true, done);
		Assert.assertEquals(true, exec.isCancelled());
	}
}
