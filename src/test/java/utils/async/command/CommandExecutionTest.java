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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.async.AsyncResult;
import utils.async.AsyncState;
import utils.async.command.CommandExecution.Input;
import utils.async.command.CommandExecution.Output;


/**
 * Linux 환경 기준 sub-process 실행 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecutionTest {
	private File m_workDir;
	private File m_outFile;

	@BeforeEach
	public void setup() throws Exception {
		Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
							"Linux 환경에서만 동작");
		m_workDir = Files.createTempDirectory("cmdexec-test-").toFile();
		m_outFile = new File(m_workDir, "out.txt");
	}

	@AfterEach
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
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("hello", readOutput());
	}

	@Test
	public void testNonZeroExitFails() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/false")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertTrue(result.getFailureCause() instanceof ExecutionException);
		Assertions.assertTrue(result.getFailureCause().getMessage().contains("retCode="));
	}

	@Test
	public void testTrueSucceedsWithExit0() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/true")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
	}

	@Test
	public void testCustomExitCodeFails() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "exit 42")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertTrue(result.getFailureCause().getMessage().contains("retCode=42"));
	}

	// ----- 변수 치환 -----

	@Test
	public void testStringVariableSubstitutionDefaultModifier() throws Exception {
		// ${var} (modifier 생략)은 변수 이름을 반환한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "name=${greeting}")
												.addVariable(new CommandVariable("greeting", "hi"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		exec.waitForFinished();

		Assertions.assertEquals("name=greeting", readOutput());
	}

	@Test
	public void testStringVariableSubstitutionValueModifier() throws Exception {
		// ${var:value}로 값을 가져온다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "value=${greeting:value}")
												.addVariable(new CommandVariable("greeting", "hi"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		exec.waitForFinished();

		Assertions.assertEquals("value=hi", readOutput());
	}

	@Test
	public void testVariableValueIsNotResubstituted() throws Exception {
		// 치환된 변수 값 안의 ${...}는 다시 치환되지 않아야 한다. 재치환되면 값에 들어 있는
		// ${secret:value}가 다른 변수(secret)의 값으로 바뀌어 노출된다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "${msg:value}")
												.addVariable(new CommandVariable("msg", "hello ${secret:value}"))
												.addVariable(new CommandVariable("secret", "TOP-SECRET"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState(),
								() -> "cause: " + result.getFailureCause());
		Assertions.assertEquals("hello ${secret:value}", readOutput());
	}

	@Test
	public void testVariableValueWithUndefinedReferenceDoesNotFail() throws Exception {
		// 값에 정의되지 않은 ${X}가 들어 있어도(예: 스크립트·템플릿 문자열) 미정의 변수 예외로 실패하지 않고
		// 그대로 전달되어야 한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "${script:value}")
												.addVariable(new CommandVariable("script", "echo ${HOME}"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState(),
								() -> "cause: " + result.getFailureCause());
		Assertions.assertEquals("echo ${HOME}", readOutput());
	}

	@Test
	public void testWORKING_DIRSubstitution() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "wd=${WORKING_DIR}")
												.workingDirectory(m_workDir)
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		exec.waitForFinished();

		Assertions.assertEquals("wd=" + m_workDir.getAbsolutePath(), readOutput());
	}

	@Test
	public void testUndefinedVariableFails() throws Exception {
		// 일반/명령 변수 치환 모두에서 매칭되지 않은 ${X}는 예외를 발생시키며,
		// sub-process는 아예 실행되지 않는다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "raw=${undefined_var}")
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Throwable cause = result.getFailureCause();
		Assertions.assertTrue(cause instanceof IllegalArgumentException, String.valueOf(cause));
		Assertions.assertTrue(cause.getMessage().contains("undefined_var"), cause.getMessage());
		Assertions.assertFalse(m_outFile.exists(), "sub-process가 실행되지 않아야 함");
	}

	@Test
	public void testStringVariableValueModifier() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "${greeting:value}")
												.addVariable(new CommandVariable("greeting", "hi"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		exec.waitForFinished();

		Assertions.assertEquals("hi", readOutput());
	}

	// ----- 환경 변수 -----

	@Test
	public void testEnvironmentVariablesPassed() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "MY_VAR")
												.environmentVariables(Map.of("MY_VAR", "abc123"))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("abc123", readOutput());
	}

	@Test
	public void testEnvironmentFileLoaded() throws Exception {
		File envFile = new File(m_workDir, "env.properties");
		Files.writeString(envFile.toPath(), "FROM_FILE=fromfile\n", StandardCharsets.UTF_8);

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "FROM_FILE")
												.environmentFile(envFile)
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("fromfile", readOutput());
	}

	@Test
	public void testEnvironmentFileMissingIsIgnored() throws Exception {
		// 존재하지 않는 envFile은 무시된다 (warn 로그만).
		File envFile = new File(m_workDir, "missing.env");
		Assertions.assertFalse(envFile.exists());

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "ok")
												.environmentFile(envFile)
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("ok", readOutput());
	}

	// ----- working directory -----

	@Test
	public void testWorkingDirectoryApplied() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/pwd")
												.workingDirectory(m_workDir)
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		exec.waitForFinished();

		// /tmp 등의 경로는 macOS/Linux에서 symlink 차이가 있으므로 endsWith로 비교.
		Assertions.assertTrue(readOutput().endsWith(m_workDir.getName()));
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

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);
		// timeout(200ms) + grace(최대 1s) 안에 종료되어야 한다.
		Assertions.assertTrue(elapsed < 2000, "elapsed=" + elapsed);
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

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertTrue(result.getFailureCause() instanceof TimeoutException);

		long childPid = Long.parseLong(Files.readString(childPidFile.toPath(), StandardCharsets.UTF_8).trim());
		boolean childAlive = ProcessHandle.of(childPid)
											.map(ProcessHandle::isAlive)
											.orElse(false);
		if ( childAlive ) {
			ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
		}
		Assertions.assertFalse(childAlive, "descendant process should be terminated: pid=" + childPid);
	}

	@Test
	public void testCompletesBeforeTimeout() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "fast")
												.timeout(Duration.ofSeconds(5))
												.stdout(Output.file(m_outFile))
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(3, TimeUnit.SECONDS);

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("fast", readOutput());
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

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());

		long childPid = Long.parseLong(Files.readString(childPidFile.toPath(), StandardCharsets.UTF_8).trim());
		boolean childAlive = ProcessHandle.of(childPid)
											.map(ProcessHandle::isAlive)
											.orElse(false);
		if ( childAlive ) {
			ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
		}
		Assertions.assertFalse(childAlive, "descendant process should be terminated: pid=" + childPid);
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

		Assertions.assertTrue(result.isCancelled());
		// 10초 sleep 중 ~150ms 후 cancel되어 빠르게 종료.
		Assertions.assertTrue(elapsed < 2000, "elapsed=" + elapsed);
	}

	// ----- close() 시맨틱 -----

	@Test
	public void testCloseAfterCompletionIsSafe() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "ok")
												.stdout(Output.file(m_outFile))
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

		Assertions.assertTrue(exec.isDone());
		Assertions.assertTrue(elapsed < 2000, "close should not block long; elapsed=" + elapsed);
	}

	@Test
	public void testCloseRetriesCancelWhenStartIsDelayed() throws Exception {
		// Executor에서 시작이 cancel timeout보다 오래 지연되면, 첫 cancel(true)는 STARTING 상태에서
		// 시간 초과로 false를 반환한다. 이때 close()가 명령이 스스로 끝날 때까지(30초) 막히지 않고,
		// 시작 직후 다시 취소해야 한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sleep", "30")
												.build();
		exec.setCancelTimeout(200, TimeUnit.MILLISECONDS);
		exec.setExecutor(task -> new Thread(() -> {
			try {
				Thread.sleep(1000);
			}
			catch ( InterruptedException e ) {
				return;
			}
			task.run();
		}).start());
		exec.start();

		long started = System.currentTimeMillis();
		exec.close();
		long elapsed = System.currentTimeMillis() - started;

		Assertions.assertTrue(exec.isCancelled(), "state=" + exec.getState());
		Assertions.assertTrue(elapsed < 5000, "close should not wait for the command; elapsed=" + elapsed);
	}

	// ----- Builder 검증 -----

	@Test
	public void testEmptyCommandRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder().build();
			});
	}

	@Test
	public void testInvalidWorkingDirectoryRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.workingDirectory(new File("/non/existent/path/xyz"))
							.build();
			});
	}

	@Test
	public void testNullCommandRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder().addCommand((String[]) null);
			});
	}

	@Test
	public void testNullCommandElementRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder().addCommand("foo", null, "bar");
			});
	}

	@Test
	public void testZeroTimeoutRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.timeout(Duration.ZERO);
			});
	}

	@Test
	public void testNegativeTimeoutRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.timeout(Duration.ofMillis(-1));
			});
	}

	@Test
	public void testSubMillisTimeoutRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.timeout(Duration.ofNanos(500_000));
			});
	}

	@Test
	public void testNullTimeoutAllowed() {
		CommandExecution.builder()
						.addCommand("/bin/echo")
						.timeout(null);
	}

	@Test
	public void testNullEnvironmentVariablesRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.environmentVariables(null);
			});
	}

	@Test
	public void testNullVariableRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.addVariable(null);
			});
	}

	@Test
	public void testDuplicateVariableRejected() throws Exception {
		CommandVariable first = new CommandVariable("greeting", "hi");
		CommandExecution.Builder builder = CommandExecution.builder()
															.addCommand("/bin/echo")
															.addVariable(first);
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> builder.addVariable(new CommandVariable("greeting", "bye")));

		// 거부된 경우 기존 변수가 그대로 유지되어야 한다.
		Assertions.assertSame(first, builder.build().getVariableMap().get("greeting"));
	}

	@Test
	public void testNullStdinFileRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CommandExecution.builder()
							.addCommand("/bin/echo")
							.stdin(Input.file(null));
			});
	}

	@Test
	public void testMissingStdinFileRejected() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/cat")
												.stdin(Input.file(new File("/non/existent/stdin.txt")))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertTrue(result.getFailureCause() instanceof IllegalArgumentException);
	}

	@Test
	public void testInputNoneGivesImmediateEof() throws Exception {
		// Input.none()은 null device를 읽기용으로 연결해야 한다. (예전에는 출력 전용인 Redirect.DISCARD를
		// redirectInput()에 넘겨 IllegalArgumentException으로 FAILED되었다.)
		// stdin을 읽는 cat은 즉시 EOF를 받아 빈 출력으로 정상 종료해야 한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/cat")
												.workingDirectory(m_workDir)
												.stdin(Input.none())
												.stdout(Output.file(m_outFile))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished(5, TimeUnit.SECONDS);

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState(),
								() -> "cause: " + result.getFailureCause());
		Assertions.assertEquals("", readOutput());
	}

	@Test
	public void testRelativeStdinFileResolvedAgainstWorkingDirectory() throws Exception {
		File inFile = new File(m_workDir, "in.txt");
		Files.writeString(inFile.toPath(), "from-working-dir\n", StandardCharsets.UTF_8);

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/cat")
												.workingDirectory(m_workDir)
												.stdin(Input.file(new File("in.txt")))
												.stdout(Output.file(m_outFile))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("from-working-dir", readOutput());
	}

	@Test
	public void testRelativeStdoutFileResolvedAgainstWorkingDirectory() throws Exception {
		File relativeOutFile = new File(m_workDir, "relative-out.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/echo", "to-relative-stdout")
												.workingDirectory(m_workDir)
												.stdout(Output.file(new File("relative-out.txt")))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("to-relative-stdout",
							Files.readString(relativeOutFile.toPath(), StandardCharsets.UTF_8).trim());
	}

	@Test
	public void testRelativeStderrFileResolvedAgainstWorkingDirectory() throws Exception {
		File relativeErrFile = new File(m_workDir, "relative-err.txt");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "echo to-relative-stderr >&2")
												.workingDirectory(m_workDir)
												.stderr(Output.file(new File("relative-err.txt")))
												.build();

		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("to-relative-stderr",
							Files.readString(relativeErrFile.toPath(), StandardCharsets.UTF_8).trim());
	}

	// ----- stdout append -----

	@Test
	public void testStdoutOverwriteByDefault() throws Exception {
		// 기본(Output.file(file))은 덮어쓰기이므로 마지막 실행 내용만 남는다.
		CommandExecution first = CommandExecution.builder()
												.addCommand("/bin/echo", "first")
												.stdout(Output.file(m_outFile))
												.build();
		first.start();
		first.waitForFinished();

		CommandExecution second = CommandExecution.builder()
												.addCommand("/bin/echo", "second")
												.stdout(Output.file(m_outFile))
												.build();
		second.start();
		second.waitForFinished();

		Assertions.assertEquals("second", readOutput());
	}

	@Test
	public void testStdoutAppendKeepsPreviousContent() throws Exception {
		// append=true이면 기존 내용 뒤에 이어 쓴다.
		CommandExecution first = CommandExecution.builder()
												.addCommand("/bin/echo", "first")
												.stdout(Output.file(m_outFile).append())
												.build();
		first.start();
		first.waitForFinished();

		CommandExecution second = CommandExecution.builder()
												.addCommand("/bin/echo", "second")
												.stdout(Output.file(m_outFile).append())
												.build();
		second.start();
		AsyncResult<Void> result = second.waitForFinished();

		Assertions.assertEquals(AsyncState.COMPLETED, result.getState());
		Assertions.assertEquals("first\nsecond", readOutput());
	}

	// ----- 방어적 복사 -----

	@Test
	public void testEnvironmentVariablesDefensiveCopy() throws Exception {
		java.util.Map<String,String> envs = new java.util.HashMap<>();
		envs.put("MY_VAR", "original");

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/usr/bin/printenv", "MY_VAR")
												.environmentVariables(envs)
												.stdout(Output.file(m_outFile))
												.build();
		// build 후 원본 map을 mutate해도 영향 없어야 한다.
		envs.put("MY_VAR", "mutated");
		envs.put("EXTRA", "should-not-leak");

		exec.start();
		exec.waitForFinished();

		Assertions.assertEquals("original", readOutput());
	}

	// ----- stdin 기본값 -----

	@Test
	public void testDefaultStdinGivesImmediateEof() throws Exception {
		// stdin을 설정하지 않으면 null device가 연결되어야 한다. (예전 기본값인 PIPE는 아무도 쓸 수 없어
		// stdin을 읽는 cat이 영원히 멈췄다.)
		try ( CommandExecution exec = CommandExecution.builder()
														.addCommand("/bin/cat")
														.workingDirectory(m_workDir)
														.stdout(Output.file(m_outFile))
														.build() ) {
			exec.start();
			AsyncResult<Void> result = exec.waitForFinished(5, TimeUnit.SECONDS);

			Assertions.assertEquals(AsyncState.COMPLETED, result.getState(),
									() -> "state=" + result.getState() + ", cause=" + result.getFailureCause());
			Assertions.assertEquals("", readOutput());
		}
	}

	// ----- 로그 -----

	@Test
	public void testResolvedCommandLineIsNotLogged() throws Exception {
		// 변수 값(비밀값일 수 있음)이 치환된 command line은 어떤 레벨로도 로그에 남지 않아야 한다.
		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
				org.slf4j.LoggerFactory.getLogger(CommandExecutionTest.class.getName() + ".secretLog");
		logger.setLevel(ch.qos.logback.classic.Level.TRACE);
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender
				= new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			CommandExecution exec = CommandExecution.builder()
													.addCommand("/bin/echo", "${token:value}")
													.addVariable(new CommandVariable("token", "TOP-SECRET"))
													.stdout(Output.file(m_outFile))
													.build();
			exec.setLogger(logger);
			exec.start();
			Assertions.assertEquals(AsyncState.COMPLETED, exec.waitForFinished().getState());
			Assertions.assertEquals("TOP-SECRET", readOutput());

			java.util.List<String> messages = appender.list.stream().map(e -> e.getFormattedMessage()).toList();
			Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("/bin/echo")), messages::toString);
			Assertions.assertTrue(messages.stream().noneMatch(m -> m.contains("TOP-SECRET")), messages::toString);
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	// ----- 실패 정보 -----

	@Test
	public void testNonZeroExitGivesExitCodeAndOutputTail() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "echo out-line; echo boom-reason 1>&2; exit 3")
												.workingDirectory(m_workDir)
												.stdout(Output.file(m_outFile))
												.stderr(Output.toStdout())
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		CommandFailedException cause = Assertions.assertInstanceOf(CommandFailedException.class,
																	result.getFailureCause());
		Assertions.assertEquals(3, cause.getExitCode());
		Assertions.assertTrue(cause.getMessage().contains("retCode=3"));
		Assertions.assertTrue(cause.getOutputTail().contains("boom-reason"), cause.getOutputTail());
	}

	@Test
	public void testOutputTailIsNullWhenOutputIsNotInFile() throws Exception {
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "exit 5")
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();

		CommandFailedException cause = Assertions.assertInstanceOf(CommandFailedException.class,
																	result.getFailureCause());
		Assertions.assertEquals(5, cause.getExitCode());
		Assertions.assertNull(cause.getOutputTail());
	}

	// ----- cancel -----

	@Test
	public void testCancelDoesNotWaitForProcessTermination() throws Exception {
		// SIGTERM을 무시하는 프로세스라도 cancel(true)는 grace(1초) 동안 기다리지 않고 곧바로 반환해야 한다.
		// (예전에는 cancelWork()가 호출 쓰레드에서 grace만큼 대기했다.) 프로세스는 이후 SIGKILL로 정리된다.
		File ready = new File(m_workDir, "ready");
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c",
															"trap '' TERM; touch " + ready.getAbsolutePath()
															+ "; while true; do sleep 0.05; done")
												.workingDirectory(m_workDir)
												.build();
		exec.start();
		exec.waitForStarted();
		long due = System.currentTimeMillis() + 5_000;
		while ( !ready.exists() && System.currentTimeMillis() < due ) {
			Thread.sleep(20);
		}
		Assertions.assertTrue(ready.exists(), "trap not installed");

		long started = System.nanoTime();
		exec.cancel(true);
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
		Assertions.assertTrue(elapsedMillis < 500, "cancel(true) took " + elapsedMillis + "ms");

		AsyncResult<Void> result = exec.waitForFinished(5, TimeUnit.SECONDS);
		Assertions.assertEquals(AsyncState.CANCELLED, result.getState());
	}

	// ----- 성공 종료 코드 -----

	@Test
	public void testExitCodeOfSuccessfulRun() throws Exception {
		CommandExecution exec = CommandExecution.builder().addCommand("/bin/true").build();
		Assertions.assertTrue(exec.getExitCode().isEmpty(), "no exit code before start");

		exec.start();
		Assertions.assertEquals(AsyncState.COMPLETED, exec.waitForFinished().getState());
		Assertions.assertEquals(0, exec.getExitCode().getAsInt());
	}

	@Test
	public void testSuccessExitCodesTreatsListedCodeAsCompleted() throws Exception {
		// grep: 0=찾음, 1=못 찾음(정상), 2=오류.
		File input = new File(m_workDir, "input.txt");
		Files.writeString(input.toPath(), "apple\nbanana\n", StandardCharsets.UTF_8);

		CommandExecution notFound = CommandExecution.builder()
													.addCommand("/bin/grep", "cherry", input.getAbsolutePath())
													.successExitCodes(0, 1)
													.build();
		notFound.start();
		AsyncResult<Void> result = notFound.waitForFinished();
		Assertions.assertEquals(AsyncState.COMPLETED, result.getState(),
								() -> "cause: " + result.getFailureCause());
		Assertions.assertEquals(1, notFound.getExitCode().getAsInt());

		CommandExecution error = CommandExecution.builder()
												.addCommand("/bin/grep", "cherry", new File(m_workDir, "missing").getAbsolutePath())
												.successExitCodes(0, 1)
												.build();
		error.start();
		AsyncResult<Void> errResult = error.waitForFinished();
		Assertions.assertEquals(AsyncState.FAILED, errResult.getState());
		Assertions.assertEquals(2, Assertions.assertInstanceOf(CommandFailedException.class,
																errResult.getFailureCause()).getExitCode());
		Assertions.assertEquals(2, error.getExitCode().getAsInt());
	}

	@Test
	public void testSuccessExitCodesReplacesDefault() throws Exception {
		// 0을 목록에서 빼면 0으로 끝나도 실패한다.
		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/true")
												.successExitCodes(1)
												.build();
		exec.start();
		AsyncResult<Void> result = exec.waitForFinished();
		Assertions.assertEquals(AsyncState.FAILED, result.getState());
		Assertions.assertEquals(0, Assertions.assertInstanceOf(CommandFailedException.class,
																result.getFailureCause()).getExitCode());
	}

	@Test
	public void testSuccessExitCodesRejectsEmpty() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> CommandExecution.builder().successExitCodes());
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> CommandExecution.builder().successExitCodes((int[])null));
	}

	@Test
	public void testExitCodeIsEmptyWhenCancelled() throws Exception {
		CommandExecution exec = CommandExecution.builder().addCommand("/bin/sleep", "30").build();
		exec.start();
		exec.waitForStarted();
		exec.cancel(true);
		Assertions.assertEquals(AsyncState.CANCELLED, exec.waitForFinished(5, TimeUnit.SECONDS).getState());
		Assertions.assertTrue(exec.getExitCode().isEmpty());
	}

	// ----- 입출력 값 객체 -----

	@Test
	public void testToStdoutIsRejectedForStdout() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> CommandExecution.builder().stdout(Output.toStdout()));
	}

	@Test
	public void testNullIoArgumentsAreRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandExecution.builder().stdin(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandExecution.builder().stdout(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandExecution.builder().stderr(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Input.file(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Output.file(null));
	}

	@Test
	public void testOutputFileAppendIsImmutable() {
		Output.ToFile overwrite = Output.file(m_outFile);
		Output.ToFile append = overwrite.append();
		Assertions.assertFalse(overwrite.appending());
		Assertions.assertTrue(append.appending());
		Assertions.assertEquals(overwrite.file(), append.file());
	}

	@Test
	public void testStderrFileAppend() throws Exception {
		// 예전 API에는 없던 조합: stderr를 파일 뒤에 이어 쓴다.
		File errFile = new File(m_workDir, "err.txt");
		Files.writeString(errFile.toPath(), "previous\n", StandardCharsets.UTF_8);

		CommandExecution exec = CommandExecution.builder()
												.addCommand("/bin/sh", "-c", "echo appended 1>&2")
												.stderr(Output.file(errFile).append())
												.build();
		exec.start();
		Assertions.assertEquals(AsyncState.COMPLETED, exec.waitForFinished().getState());
		Assertions.assertEquals("previous\nappended\n", Files.readString(errFile.toPath(), StandardCharsets.UTF_8));
	}
}
