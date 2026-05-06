package utils.async.command;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import utils.async.AsyncResult;
import utils.async.AsyncState;
import utils.async.command.CommandVariable.StringVariable;


/**
 * Linux 환경 기준 sub-process 실행 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecutionTest {
	private File m_workDir;
	private File m_outFile;

	@Before
	public void setup() throws Exception {
		Assume.assumeTrue("Linux 환경에서만 동작",
							System.getProperty("os.name").toLowerCase().contains("linux"));
		m_workDir = Files.createTempDirectory("cmdexec-test-").toFile();
		m_outFile = new File(m_workDir, "out.txt");
	}

	@After
	public void cleanup() {
		if ( m_outFile != null && m_outFile.exists() ) {
			m_outFile.delete();
		}
		if ( m_workDir != null && m_workDir.exists() ) {
			for ( File f : m_workDir.listFiles() ) {
				f.delete();
			}
			m_workDir.delete();
		}
	}

	private String readOutput() throws Exception {
		return Files.readString(m_outFile.toPath(), StandardCharsets.UTF_8).trim();
	}

	// ----- 기본 실행 / 종료 코드 -----

	@Test
	public void testEchoSucceeds() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "hello")
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("hello", readOutput());
	}

	@Test
	public void testNonZeroExitFails() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/false")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertTrue(result.getFailureCause() instanceof ExecutionException);
		Assert.assertTrue(result.getFailureCause().getMessage().contains("retCode="));
	}

	@Test
	public void testTrueSucceedsWithExit0() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/true")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
	}

	@Test
	public void testCustomExitCodeFails() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "exit 42")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertTrue(result.getFailureCause().getMessage().contains("retCode=42"));
	}

	// ----- 변수 치환 -----

	@Test
	public void testStringVariableSubstitutionDefaultModifier() throws Exception {
		// ${var} (modifier 생략)은 변수 이름을 반환한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "name=${greeting}")
												.addVariable(new StringVariable("greeting", "hi"))
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("name=greeting", readOutput());
	}

	@Test
	public void testStringVariableSubstitutionValueModifier() throws Exception {
		// ${var:value}로 값을 가져온다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "value=${greeting:value}")
												.addVariable(new StringVariable("greeting", "hi"))
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("value=hi", readOutput());
	}

	@Test
	public void testWORKING_DIRSubstitution() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "wd=${WORKING_DIR}")
												.workingDirectory(m_workDir)
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("wd=" + m_workDir.getAbsolutePath(), readOutput());
	}

	@Test
	public void testUndefinedVariableLeftAsIs() throws Exception {
		// 일반/명령 변수 치환 모두에서 미정의된 ${X}는 sub-process에 그대로 전달된다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "raw=${undefined_var}")
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("raw=${undefined_var}", readOutput());
	}

	@Test
	public void testStringVariableValueModifier() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "${greeting:value}")
												.addVariable(new StringVariable("greeting", "hi"))
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("hi", readOutput());
	}

	// ----- 환경 변수 -----

	@Test
	public void testEnvironmentVariablesPassed() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "MY_VAR")
												.environmentVariables(Map.of("MY_VAR", "abc123"))
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("abc123", readOutput());
	}

	@Test
	public void testEnvironmentFileLoaded() throws Exception {
		File envFile = new File(m_workDir, "env.properties");
		Files.writeString(envFile.toPath(), "FROM_FILE=fromfile\n", StandardCharsets.UTF_8);

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "FROM_FILE")
												.environmentFile(envFile)
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("fromfile", readOutput());
	}

	@Test
	public void testEnvironmentFileMissingIsIgnored() throws Exception {
		// 존재하지 않는 envFile은 무시된다 (warn 로그만).
		File envFile = new File(m_workDir, "missing.env");
		Assert.assertFalse(envFile.exists());

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "ok")
												.environmentFile(envFile)
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("ok", readOutput());
	}

	// ----- working directory -----

	@Test
	public void testWorkingDirectoryApplied() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/pwd")
												.workingDirectory(m_workDir)
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();

		// /tmp 등의 경로는 macOS/Linux에서 symlink 차이가 있으므로 endsWith로 비교.
		Assert.assertTrue(readOutput().endsWith(m_workDir.getName()));
	}

	// ----- timeout -----

	@Test
	public void testTimeoutTriggersFailure() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sleep", "5")
												.timeout(Duration.ofMillis(200))
												.build();

		long started = System.currentTimeMillis();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// timeout(200ms) + grace(최대 1s) 안에 종료되어야 한다.
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 2000);
	}

	@Test
	public void testTimeoutTerminatesDescendantProcess() throws Exception {
		File childPidFile = new File(m_workDir, "child.pid");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "sleep 10 & echo $! > child.pid; wait")
												.workingDirectory(m_workDir)
												.timeout(Duration.ofMillis(200))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);

		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertTrue(result.getFailureCause() instanceof TimeoutException);

		long childPid = Long.parseLong(Files.readString(childPidFile.toPath(), StandardCharsets.UTF_8).trim());
		boolean childAlive = ProcessHandle.of(childPid)
											.map(ProcessHandle::isAlive)
											.orElse(false);
		if ( childAlive ) {
			ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
		}
		Assert.assertFalse("descendant process should be terminated: pid=" + childPid, childAlive);
	}

	@Test
	public void testCompletesBeforeTimeout() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "fast")
												.timeout(Duration.ofSeconds(5))
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("fast", readOutput());
	}

	@Test
	public void testCompletedShellTerminatesDescendantProcess() throws Exception {
		File childPidFile = new File(m_workDir, "child.pid");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "sleep 10 & echo $! > child.pid; sleep 0.2")
												.workingDirectory(m_workDir)
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());

		long childPid = Long.parseLong(Files.readString(childPidFile.toPath(), StandardCharsets.UTF_8).trim());
		boolean childAlive = ProcessHandle.of(childPid)
											.map(ProcessHandle::isAlive)
											.orElse(false);
		if ( childAlive ) {
			ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
		}
		Assert.assertFalse("descendant process should be terminated: pid=" + childPid, childAlive);
	}

	// ----- cancel -----

	@Test
	public void testCancelDuringExecution() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sleep", "10")
												.build();
		exec.start();
		// 약간의 시간을 두고 시작이 안정되면 cancel
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(150);
				exec.cancel(true);
			}
			catch ( InterruptedException ignored ) { }
		});
		long started = System.currentTimeMillis();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertTrue(result.isCancelled());
		// 10초 sleep 중 ~150ms 후 cancel되어 빠르게 종료.
		Assert.assertTrue("elapsed=" + elapsed, elapsed < 2000);
	}

	// ----- close() 시맨틱 -----

	@Test
	public void testCloseAfterCompletionIsSafe() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "ok")
												.redirectStdoutToFile(m_outFile)
												.build();
		exec.start();
		exec.waitForFinished();
		// 정상 종료 후 close 호출 — 예외 없이 처리되어야 한다.
		exec.close();
	}

	@Test
	public void testCloseDuringExecutionCancels() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sleep", "10")
												.build();
		exec.start();
		Thread.sleep(100);

		long started = System.currentTimeMillis();
		exec.close();   // close가 cancel + waitForFinished를 수행
		long elapsed = System.currentTimeMillis() - started;

		Assert.assertTrue(exec.isDone());
		Assert.assertTrue("close should not block long; elapsed=" + elapsed, elapsed < 2000);
	}

	// ----- Builder 검증 -----

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyCommandRejected() {
		CommandExecution.builder().build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidWorkingDirectoryRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.workingDirectory(new File("/non/existent/path/xyz"))
						.build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCommandRejected() {
		CommandExecution.builder().addCommand((String[]) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCommandElementRejected() {
		CommandExecution.builder().addCommand("foo", null, "bar");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testZeroTimeoutRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.timeout(Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNegativeTimeoutRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.timeout(Duration.ofMillis(-1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSubMillisTimeoutRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.timeout(Duration.ofNanos(500_000));
	}

	@Test
	public void testNullTimeoutAllowed() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.timeout(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullEnvironmentVariablesRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.environmentVariables(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullVariableRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.addVariable(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullStdinFileRejected() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.redirectStdinFromFile(null);
	}

	@Test
	public void testMissingStdinFileRejected() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/cat")
												.redirectStdinFromFile(new File("/non/existent/stdin.txt"))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.FAILED, result.getState());
		Assert.assertTrue(result.getFailureCause() instanceof IllegalArgumentException);
	}

	@Test
	public void testRelativeStdinFileResolvedAgainstWorkingDirectory() throws Exception {
		File inFile = new File(m_workDir, "in.txt");
		Files.writeString(inFile.toPath(), "from-working-dir\n", StandardCharsets.UTF_8);

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/cat")
												.workingDirectory(m_workDir)
												.redirectStdinFromFile(new File("in.txt"))
												.redirectStdoutToFile(m_outFile)
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("from-working-dir", readOutput());
	}

	@Test
	public void testRelativeStdoutFileResolvedAgainstWorkingDirectory() throws Exception {
		File relativeOutFile = new File(m_workDir, "relative-out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "to-relative-stdout")
												.workingDirectory(m_workDir)
												.redirectStdoutToFile(new File("relative-out.txt"))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("to-relative-stdout",
							Files.readString(relativeOutFile.toPath(), StandardCharsets.UTF_8).trim());
	}

	@Test
	public void testRelativeStderrFileResolvedAgainstWorkingDirectory() throws Exception {
		File relativeErrFile = new File(m_workDir, "relative-err.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "echo to-relative-stderr >&2")
												.workingDirectory(m_workDir)
												.redirectStderrToFile(new File("relative-err.txt"))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assert.assertEquals(AsyncState.COMPLETED, result.getState());
		Assert.assertEquals("to-relative-stderr",
							Files.readString(relativeErrFile.toPath(), StandardCharsets.UTF_8).trim());
	}

	// ----- 방어적 복사 -----

	@Test
	public void testEnvironmentVariablesDefensiveCopy() throws Exception {
		java.util.Map<String,String> envs = new java.util.HashMap<>();
		envs.put("MY_VAR", "original");

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "MY_VAR")
												.environmentVariables(envs)
												.redirectStdoutToFile(m_outFile)
												.build();
		// build 후 원본 map을 mutate해도 영향 없어야 한다.
		envs.put("MY_VAR", "mutated");
		envs.put("EXTRA", "should-not-leak");

		exec.start();
		exec.waitForFinished();

		Assert.assertEquals("original", readOutput());
	}
}
