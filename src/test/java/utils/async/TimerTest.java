package utils.async;


import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.RuntimeInterruptedException;
import utils.func.Unchecked;
import utils.thread.Timer;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class TimerTest {
	Timer m_timer;
	
	@BeforeEach
	public void setUp() {
		m_timer = new Timer();
	}
	
	@AfterEach
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
		Assertions.assertEquals(true, taskA.isCancelled());
	}
	
	@Test
	public void test02() throws Exception {
		StartableExecution<String> taskA = create("a", 1000000, true);
		StartableExecution<String> taskB = create("b", 1000000, true);
		
		m_timer.setTimer(taskA, 500, TimeUnit.MILLISECONDS);
		m_timer.setTimer(taskB, 100, TimeUnit.MILLISECONDS);
		taskB.waitForFinished();
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isCancelled());
		
		Unchecked.runOrRTE(() -> Thread.sleep(500));
		Assertions.assertEquals(true, taskA.isCancelled());
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
		
		Assertions.assertEquals(true, taskA.isCancelled());
		Assertions.assertEquals(true, taskB.isCancelled());
		Assertions.assertEquals(true, taskC.isCompleted());
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
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isRunning());
		Assertions.assertEquals(true, taskC.isCancelled());
		
		taskB.waitForFinished();
		Assertions.assertEquals(true, taskA.isRunning());
		Assertions.assertEquals(true, taskB.isCancelled());
		
		taskA.waitForFinished();
		Assertions.assertEquals(true, taskA.isCancelled());
	}
}
