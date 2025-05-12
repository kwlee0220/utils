package utils.async;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;

import utils.async.CommandVariable.StringVariable;
import utils.io.FileUtils;
import utils.io.IOUtils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecutionTest {
	@Test
	public void test1() throws Exception {
		File outFile = new File("out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("cmd.exe", "/C", "echo", "a", "b")
												.redictStdoutToFile(outFile)
												.build();
		exec.start();
		exec.waitForFinished();
		String str = IOUtils.toString(outFile).trim();
		FileUtils.deleteAnyway(outFile);
		Assert.assertEquals("a b", str);
	}
	
	@Test
	public void testSubstitute() throws Exception {
		File outFile = new File("out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("cmd.exe", "/C", "echo ${a} b")
												.redictStdoutToFile(outFile)
												.addVariable(new StringVariable("a", "AAA"))
												.build();
		exec.start();
		exec.waitForFinished();
		String str = IOUtils.toString(outFile).trim();
		FileUtils.deleteAnyway(outFile);
		Assert.assertEquals("a b", str);
	}
	
	@Test
	public void testSleep() throws Exception {
		File outFile = new File("out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("cmd.exe", "/C", "ping 127.0.0.1 -n 1 > nul & echo a b")
												.redictStdoutToFile(outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();
		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		
		String str = IOUtils.toString(outFile).trim();
		FileUtils.deleteAnyway(outFile);
		Assert.assertEquals("a b", str);
	}
	
	@Test
	public void testSleepAndCancel() throws Exception {
		File outFile = new File("out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("cmd.exe", "/C", "ping 127.0.0.1 -n 2 > nul & echo a b")
												.redictStdoutToFile(outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(300, TimeUnit.MILLISECONDS);
		Assert.assertEquals(AsyncState.RUNNING, result.getState());
		
		boolean ret = exec.cancel(true);
		Assert.assertTrue(ret);
	}
	
	@Test
	public void testSleepTimeout() throws Exception {
		File outFile = new File("out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("cmd.exe", "/C", "ping 127.0.0.1 -n 2 > nul & echo a b")
												.redictStdoutToFile(outFile)
												.setTimeout(Duration.ofMillis(300))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();
		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertEquals(TimeoutException.class, result.getFailureCause().getClass());
	}
}
