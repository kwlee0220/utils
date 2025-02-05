package utils.async;


import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.RuntimeInterruptedException;
import utils.func.Unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class TimerTest {
	Timer m_timer;
	
	@Before
	public void setUp() {
		m_timer = new Timer();
	}
	
	@After
	public void tearDown() {
		m_timer.shutdown();
	}
	
	private StartableExecution<String> create(String ret, long timeout, boolean start) {
		StartableExecution<String> exec = Executions.supplyAsync(() -> {
			try {
				Thread.sleep(timeout);
			}
			catch ( InterruptedException e ) {
				throw new RuntimeInterruptedException(e);
			}
			return ret;
		});
		if ( start ) {
			exec.start();
		}
		return exec;
	}
	
	@Test
	public void test01() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		
		m_timer.setTimer(taskA, 300, TimeUnit.MILLISECONDS);
		taskA.waitForFinished();
		Assert.assertEquals(true, taskA.isCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		
		m_timer.setTimer(taskA, 500, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskB, 100, TimeUnit.MILLISECONDS);
		taskB.waitForFinished();
		Assert.assertEquals(true, taskA.isRunning());
		Assert.assertEquals(true, taskB.isCancelled());
		
		Unchecked.runOrRTE(() -> Thread.sleep(500));
		Assert.assertEquals(true, taskA.isCancelled());
	}
	
	@Test
	public void test03() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		StartableExecution<String> taskC = create("c", 300, true);
		
		m_timer.setTimer(taskA, 400, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskB, 100, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskC, 700, TimeUnit.MILLISECONDS);
		taskA.waitForFinished();
		
		Assert.assertEquals(true, taskA.isCancelled());
		Assert.assertEquals(true, taskB.isCancelled());
		Assert.assertEquals(true, taskC.isCompleted());
	}
	
	@Test
	public void test04() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		StartableExecution<String> taskC = create("c", 1000000, true);
		
		taskA.setTimeout(700, TimeUnit.MILLISECONDS);
		taskB.setTimeout(400, TimeUnit.MILLISECONDS);
		taskC.setTimeout(100, TimeUnit.MILLISECONDS);
		
		taskC.waitForFinished();
		Assert.assertEquals(true, taskA.isRunning());
		Assert.assertEquals(true, taskB.isRunning());
		Assert.assertEquals(true, taskC.isCancelled());
		
		taskB.waitForFinished();
		Assert.assertEquals(true, taskA.isRunning());
		Assert.assertEquals(true, taskB.isCancelled());
		
		taskA.waitForFinished();
		Assert.assertEquals(true, taskA.isCancelled());
	}
}
