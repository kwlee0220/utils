package utils.async.command;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Linux 환경 기준 {@link ProgramService} 라이프사이클·재시작 정책 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProgramServiceTest {
	private File m_workDir;

	@BeforeEach
	public void setup() throws Exception {
		Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
							"Linux 환경에서만 동작");
		m_workDir = Files.createTempDirectory("programservice-test-").toFile();
	}

	@AfterEach
	public void cleanup() {
		if ( m_workDir != null && m_workDir.exists() ) {
			for ( File f : m_workDir.listFiles() ) {
				f.delete();
			}
			m_workDir.delete();
		}
	}

	private ProgramServiceConfig config(RestartPolicy policy, String... cmd) {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setCommandLine(Arrays.asList(cmd));
		cfg.setWorkingDirectory(m_workDir);
		cfg.setRestartPolicy(policy);
		// delay 분기를 우회하기 위해 0으로 설정 (isPositive()는 false).
		cfg.setRestartDelay(Duration.ZERO);
		// 재시작 동작을 빠르게 확인하기 위해 backoff를 끈다 (maxRestartDelay <= restartDelay).
		cfg.setMaxRestartDelay(Duration.ZERO);
		return cfg;
	}

	// ---------- 라이프사이클 ----------

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void start_with_quick_success_no_policy_terminates_normally() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo", "hello"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void start_with_failure_command_no_policy_transitions_to_FAILED() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/usr/bin/false"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		try {
			svc.awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.fail("expected exception due to FAILED state");
		}
		catch ( IllegalStateException expected ) {
			// Guava AbstractService.awaitTerminated()는 FAILED 시 IllegalStateException 발생
		}

		Assertions.assertEquals(Service.State.FAILED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void on_failed_policy_does_not_restart_after_success() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ON_FAILED, "/bin/echo", "ok"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void on_completed_policy_fails_on_command_failure() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ON_COMPLETED, "/usr/bin/false"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		try {
			svc.awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.fail("expected exception");
		}
		catch ( IllegalStateException expected ) { }

		Assertions.assertEquals(Service.State.FAILED, svc.state());
	}

	@Test

	@Timeout(value = 15_000, unit = TimeUnit.MILLISECONDS)
	public void always_policy_restarts_then_stop_terminates() throws Exception {
		// 짧게 sleep해서 재시작이 여러 번 일어나도록 함.
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sleep", "0.05"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		// 충분한 시간 동안 재시작이 일어남.
		Thread.sleep(300);
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void stop_during_long_running_command_terminates_service() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sleep", "30"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		Thread.sleep(100);  // 명령이 실제로 돌아가는 시간 보장
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void on_failed_policy_restarts_after_failure_then_stop_terminates() throws Exception {
		// /usr/bin/false는 즉시 실패 → 재시작 반복. stop으로 종료.
		ProgramService svc = ProgramService.create(config(RestartPolicy.ON_FAILED, "/usr/bin/false"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		Thread.sleep(200);  // 몇 번 재시작 보장
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void on_completed_policy_restarts_after_success_then_stop_terminates() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ON_COMPLETED,
															"/bin/sleep", "0.05"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		Thread.sleep(200);
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	// ---------- application.log 생성 ----------

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void stdout_is_redirected_to_application_log() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo", "hello-world"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		File log = new File(m_workDir, "application.log");
		Assertions.assertTrue(log.exists(), "application.log이 작업 디렉터리에 생성되어야 함");
		String content = new String(Files.readAllBytes(log.toPath())).trim();
		Assertions.assertEquals("hello-world", content);
	}

	// ---------- toString / getter ----------

	@Test
	public void toString_contains_config() {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo"));
		Assertions.assertTrue(svc.toString().contains("ProgramService"));
	}

	// ---------- restartDelay 동작 검증 ----------

	/**
	 * 매 실행마다 작업 디렉터리의 {@code count.txt}에 한 줄을 append하는 명령을 생성한다.
	 */
	private String[] appendingCommand() {
		File counter = new File(m_workDir, "count.txt");
		return new String[] { "/bin/sh", "-c", "echo tick >> " + counter.getAbsolutePath() };
	}

	private long countLines() throws Exception {
		File counter = new File(m_workDir, "count.txt");
		if ( !counter.exists() ) return 0;
		return Files.lines(counter.toPath()).count();
	}

	/**
	 * {@code restartDelay}가 양수이면 재시작이 지연된다. 즉시 종료되는 명령을 ALWAYS로 돌리면서
	 * delay 효과로 짧은 시간 동안 발생하는 재시작 횟수가 제한되는지 확인한다.
	 */
	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void positive_restart_delay_limits_restart_frequency() throws Exception {
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, appendingCommand());
		cfg.setRestartDelay(Duration.ofMillis(300));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(700);  // 약 2-3 사이클 정도 진행 (300ms delay 기준)
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		long lineCount = countLines();
		Assertions.assertTrue(lineCount >= 1 && lineCount <= 5, "delay 적용 시 실행 횟수 제한적: 실제 " + lineCount);
	}

	/**
	 * {@code restartDelay=0}이면 지연 없이 즉시 재시작되어 짧은 시간 동안 다회 실행이 발생한다.
	 */
	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void zero_restart_delay_allows_immediate_restart() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, appendingCommand()));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(500);
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		long lineCount = countLines();
		// delay=0 시 fork 오버헤드만큼 빠르게 반복 — 최소 3회 이상 기대.
		Assertions.assertTrue(lineCount >= 3, "delay=0 시 다회 재시작: 실제 " + lineCount);
	}

	/**
	 * {@code restartDelay}가 양수일 때 재시작된 명령도 stop으로 종료되어야 한다.
	 * <p>
	 * 첫 실행은 marker 파일만 만들고 바로 종료하고, 재시작된 실행은 자신의 pid를 기록한 뒤
	 * 장시간 실행된다. 재시작된 실행은 {@link utils.async.op.DelayedAsyncExecution}으로 감싸지므로,
	 * stop의 취소가 감싸진 명령까지 전달되지 않으면 서비스만 TERMINATED가 되고 프로세스는 남는다.
	 */
	@Test
	@Timeout(value = 15_000, unit = TimeUnit.MILLISECONDS)
	public void stop_terminates_command_restarted_after_delay() throws Exception {
		File marker = new File(m_workDir, "marker");
		File pidFile = new File(m_workDir, "pid.txt");
		String script = String.format("if [ -f %s ]; then echo $$ > %s; exec sleep 30; else touch %s; fi",
										marker.getAbsolutePath(), pidFile.getAbsolutePath(),
										marker.getAbsolutePath());
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sh", "-c", script);
		cfg.setRestartDelay(Duration.ofMillis(200));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		// 재시작된 실행이 pid를 기록할 때까지 대기.
		long deadline = System.currentTimeMillis() + 5_000;
		while ( !(pidFile.exists() && pidFile.length() > 0) && System.currentTimeMillis() < deadline ) {
			Thread.sleep(50);
		}
		Assertions.assertTrue(pidFile.exists(), "재시작된 명령이 실행되어야 함");
		long pid = Long.parseLong(Files.readString(pidFile.toPath()).trim());
		ProcessHandle proc = ProcessHandle.of(pid).orElseThrow();

		try {
			svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.assertEquals(Service.State.TERMINATED, svc.state());

			proc.onExit().get(3, TimeUnit.SECONDS);
			Assertions.assertFalse(proc.isAlive(), "재시작된 명령의 프로세스가 종료되어야 함: pid=" + pid);
		}
		catch ( java.util.concurrent.TimeoutException e ) {
			Assertions.fail("stop 후에도 재시작된 명령의 프로세스가 남아 있음: pid=" + pid);
		}
		finally {
			proc.destroyForcibly();
		}
	}

	// ---------- 일반 라이프사이클 ----------

	@Test

	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void multiple_stop_calls_are_safe() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sleep", "30"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(50);

		svc.stopAsync();
		svc.stopAsync();   // 두 번째 호출은 Guava AbstractService에서 no-op.
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test

	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void start_twice_throws() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo", "x"));
		svc.startAsync();
		try {
			svc.startAsync();
			Assertions.fail("두 번째 startAsync는 IllegalStateException을 던져야 함");
		}
		catch ( IllegalStateException expected ) { }
		svc.awaitTerminated(5, TimeUnit.SECONDS);
	}

	// ---------- ALWAYS 정책 + 실패 명령 (커버리지 보완) ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void always_policy_restarts_after_failure_then_stop_terminates() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/usr/bin/false"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		Thread.sleep(200);   // 여러 번 재시작 보장
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void always_policy_with_quick_success_then_stop_terminates() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/true"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		Thread.sleep(200);
		Assertions.assertEquals(Service.State.RUNNING, svc.state());

		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	// ---------- race 회귀 테스트 (whenFinished와 doStop 사이) ----------

	/**
	 * 재시작 결정 중에 stop이 들어오는 race로 인해 서비스가 STOPPING에서 stuck되지 않는지
	 * 검증한다. 짧은 명령을 ALWAYS로 빠르게 재시작시키며 임의 시점에 stop을 호출하는 시도를
	 * 여러 번 반복하여 timing race를 노출시킨다.
	 */
	@Test
	@Timeout(value = 60_000, unit = TimeUnit.MILLISECONDS)
	public void stop_during_restart_decision_does_not_get_stuck() throws Exception {
		for ( int i = 0; i < 30; i++ ) {
			ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/true"));
			svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
			// whenFinished 콜백이 nextExec 빌드 중인 시점에 stop이 들어가도록 유도.
			Thread.sleep(i % 5);
			svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.assertEquals(Service.State.TERMINATED, svc.state(),
									"iteration " + i + ": should reach TERMINATED");
		}
	}

	// ---------- JSON 설정 파일에서 생성 ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void create_from_json_file_loads_config() throws Exception {
		File jsonFile = new File(m_workDir, "config.json");
		String json = "{\n"
					+ "  \"commandLine\": [\"/bin/echo\", \"json-config\"],\n"
					+ "  \"workingDirectory\": \"" + m_workDir.getAbsolutePath() + "\",\n"
					+ "  \"restartPolicy\": \"NO\",\n"
					+ "  \"restartDelay\": \"PT0S\"\n"
					+ "}\n";
		Files.writeString(jsonFile.toPath(), json, StandardCharsets.UTF_8);

		ProgramService svc = ProgramService.create(jsonFile);
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
		File log = new File(m_workDir, "application.log");
		Assertions.assertTrue(log.exists());
		Assertions.assertEquals("json-config",
								Files.readString(log.toPath(), StandardCharsets.UTF_8).trim());
	}

	// ---------- LoggerSettable ----------

	@Test
	public void getLogger_returns_default_when_not_set() {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo"));
		Assertions.assertNotNull(svc.getLogger());
		Assertions.assertEquals(ProgramService.class.getName(), svc.getLogger().getName());
	}

	@Test
	public void setLogger_overrides_default() {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo"));
		Logger custom = LoggerFactory.getLogger("custom.logger.for.test");
		svc.setLogger(custom);
		Assertions.assertSame(custom, svc.getLogger());
	}

	@Test
	public void setLogger_null_restores_default() {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/echo"));
		Logger custom = LoggerFactory.getLogger("custom.logger.for.test");
		svc.setLogger(custom);
		Assertions.assertSame(custom, svc.getLogger());

		svc.setLogger(null);
		Assertions.assertEquals(ProgramService.class.getName(), svc.getLogger().getName());
	}

	// ---------- 환경 변수 전달 ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void environment_variables_are_passed_to_subprocess() throws Exception {
		ProgramServiceConfig cfg = config(RestartPolicy.NO, "/usr/bin/printenv", "MY_PROG_VAR");
		cfg.setEnvironments(java.util.Map.of("MY_PROG_VAR", "value-xyz"));

		ProgramService svc = ProgramService.create(cfg);
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);

		File log = new File(m_workDir, "application.log");
		Assertions.assertTrue(log.exists());
		Assertions.assertEquals("value-xyz",
								Files.readString(log.toPath(), StandardCharsets.UTF_8).trim());
	}

	// ---------- stop이 cancel 경로로 진입하는지 ----------

	/**
	 * 장시간 실행 명령을 stop으로 종료하면 result는 isNone(취소)으로 들어오고,
	 * 재시작 정책이 ALWAYS여도 재시작 없이 TERMINATED에 도달해야 한다.
	 * (m_stopRequested 플래그가 정책 평가를 우회시키는 동작 검증)
	 */
	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void stop_short_circuits_restart_policy() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sleep", "30"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(100);

		long startedAt = System.currentTimeMillis();
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		long elapsed = System.currentTimeMillis() - startedAt;

		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
		// 재시작 없이 즉시 종료되어야 함 (30초 sleep을 새로 시작하면 timeout에 걸림).
		Assertions.assertTrue(elapsed < 3000, "stop 후 재시작 없이 종료되어야 함: elapsed=" + elapsed);
	}

	// ---------- 시작 실패 / 시작 중 중지 ----------

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void start_failure_fails_service_regardless_of_restart_policy() {
		// 실행 파일이 없어 첫 실행이 RUNNING에 이르지 못하면, 재시작 정책과 무관하게 서비스가 FAILED가 되어야 한다.
		// (이전에는 ALWAYS/ON_FAILED에서 재시작만 반복하며 STARTING에 갇혔다.)
		for ( RestartPolicy policy: RestartPolicy.values() ) {
			ProgramService svc = ProgramService.create(config(policy, "/nonexistent/program-for-start-failure"));
			svc.startAsync();

			IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
															() -> svc.awaitRunning(5, TimeUnit.SECONDS),
															"policy=" + policy);
			Assertions.assertEquals(Service.State.FAILED, svc.state(), "policy=" + policy);
			Assertions.assertInstanceOf(java.io.IOException.class, svc.failureCause(), "policy=" + policy);
		}
	}

	@Test
	@Timeout(value = 30_000, unit = TimeUnit.MILLISECONDS)
	public void stop_right_after_start_terminates_service() throws Exception {
		// 시작 직후 곧바로 중지하면 시작 중(STARTING)이든 이미 RUNNING이든 서비스가 TERMINATED로 끝나야 한다.
		for ( int i = 0; i < 10; ++i ) {
			ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "sleep", "30"));
			svc.startAsync();
			svc.stopAsync();
			svc.awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.assertEquals(Service.State.TERMINATED, svc.state(), "round " + i);
		}
	}

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void restart_build_failure_fails_service() throws Exception {
		// 실행 중에 working directory가 사라지면, 재시작하려는 다음 실행을 구성하지 못한다.
		// 이때 서비스가 자식 프로세스 없이 RUNNING으로 남지 않고 FAILED가 되어야 한다.
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "sleep", "0.5"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		for ( File f: m_workDir.listFiles() ) {
			f.delete();
		}
		Assertions.assertTrue(m_workDir.delete(), "working directory must be deleted for the test");

		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitTerminated(5, TimeUnit.SECONDS));
		Assertions.assertEquals(Service.State.FAILED, svc.state());
		Assertions.assertInstanceOf(IllegalArgumentException.class, svc.failureCause());
	}

	// ---------- 재시작 판단 중 중지 (deadlock 방지) ----------

	/**
	 * 지정된 메시지를 기록하려는 쓰레드를 {@code release}가 열릴 때까지 붙잡는 appender.
	 * 재시작 판단 도중의 특정 시점에서 재시작 쓰레드를 멈춰 두기 위해 사용한다.
	 */
	private static final class BlockingAppender
		extends ch.qos.logback.core.UnsynchronizedAppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> {
		final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean();
		final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		final java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
		private final String m_blockOn;

		BlockingAppender(String blockOn) {
			m_blockOn = blockOn;
		}

		@Override
		protected void append(ch.qos.logback.classic.spi.ILoggingEvent ev) {
			String msg = ev.getFormattedMessage();
			messages.add(msg);
			if ( armed.get() && msg.contains(m_blockOn) ) {
				armed.set(false);
				entered.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void stop_during_restart_decision_does_not_block() throws Exception {
		// 실행이 종료되어 다음 실행을 만드는 도중(재시작 판단 중)에 stopAsync()가 호출되어도, stopAsync()는
		// 판단이 끝나기를 기다리지 않고 곧바로 반환해야 한다. (예전에는 Guava의 lock을 쥔 doStop()이 판단 종료를
		// 기다려, 판단 쪽의 notifyStopped()/notifyFailed()와 서로를 기다리는 deadlock이 생길 수 있었다.)
		// 판단이 끝나면 다음 실행은 시작되지 않고 서비스는 TERMINATED가 되어야 한다.
		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
				LoggerFactory.getLogger(ProgramServiceTest.class.getName() + ".restartDecision");
		logger.setLevel(ch.qos.logback.classic.Level.INFO);
		BlockingAppender appender = new BlockingAppender("command output will be redirected");
		appender.start();
		logger.addAppender(appender);

		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "sleep", "0.3"));
		svc.setLogger(logger);
		try {
			svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
			appender.armed.set(true);			// 재시작을 위해 다음 실행을 만드는 시점에서 붙잡는다.
			Assertions.assertTrue(appender.entered.await(5, TimeUnit.SECONDS), "restart decision not reached");

			java.util.concurrent.CompletableFuture<Void> stop
					= java.util.concurrent.CompletableFuture.runAsync(() -> svc.stopAsync());
			stop.get(1, TimeUnit.SECONDS);		// 판단이 끝나기를 기다리지 않고 반환해야 한다.

			appender.release.countDown();
			svc.awaitTerminated(5, TimeUnit.SECONDS);
			Assertions.assertEquals(Service.State.TERMINATED, svc.state());
			Assertions.assertTrue(appender.messages.stream().noneMatch(m -> m.startsWith("restarting the command")),
									"the next execution must not be started after stop: " + appender.messages);
		}
		finally {
			appender.release.countDown();
			logger.detachAppender(appender);
		}
	}

	// ---------- application.log append ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void application_log_is_appended_across_restarts() throws Exception {
		// 재시작해도 이전 실행의 출력(종료 원인 등)이 지워지지 않아야 한다. (예전에는 실행마다 덮어썼다.)
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/echo", "run-line"));
		File log = new File(m_workDir, "application.log");
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		try {
			long due = System.currentTimeMillis() + 5_000;
			long lines = 0;
			while ( System.currentTimeMillis() < due ) {
				lines = log.exists() ? Files.lines(log.toPath()).filter("run-line"::equals).count() : 0;
				if ( lines >= 3 ) {
					break;
				}
				Thread.sleep(20);
			}
			Assertions.assertTrue(lines >= 3, "application.log lines: " + lines);
		}
		finally {
			svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		}
	}

	// ---------- startTimeout ----------

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void start_timeout_fails_service_when_command_does_not_start_in_time() throws Exception {
		// 첫 실행이 startTimeout 안에 RUNNING이 되지 못하면 서비스는 TimeoutException 원인으로 FAILED가 된다.
		// 실행을 executor에서 붙잡아 STARTING에 머물게 한다. 늦게 시작된 실행은 곧바로 취소되어 자식 프로세스가
		// 남지 않아야 한다.
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.Executor delaying = task -> new Thread(() -> {
			try {
				release.await();
			}
			catch ( InterruptedException e ) {
				return;
			}
			task.run();
		}).start();

		File started = new File(m_workDir, "started");
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sh", "-c",
										"touch " + started.getAbsolutePath() + "; exec /bin/sleep 31.7");
		cfg.setStartTimeout(Duration.ofMillis(200));
		ProgramService svc = ProgramService.create(cfg);
		svc.setExecutor(delaying);

		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitRunning(5, TimeUnit.SECONDS));
		Assertions.assertEquals(Service.State.FAILED, svc.state());
		Assertions.assertInstanceOf(java.util.concurrent.TimeoutException.class, svc.failureCause());

		// 실행을 풀어 주면 자식 프로세스가 시작되었다가 취소되어야 한다.
		release.countDown();
		long due = System.currentTimeMillis() + 5_000;
		while ( !started.exists() && System.currentTimeMillis() < due ) {
			Thread.sleep(20);
		}
		Assertions.assertTrue(started.exists(), "the delayed command never started");
		while ( countSleepers("31.7") > 0 && System.currentTimeMillis() < due + 5_000 ) {
			Thread.sleep(50);
		}
		Assertions.assertEquals(0, countSleepers("31.7"), "the late command must be cancelled");
		Assertions.assertEquals(Service.State.FAILED, svc.state());
	}

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void awaitCleanup_waits_for_start_cancelled_by_start_timeout() throws Exception {
		// 시작 제한 시간 초과로 서비스가 먼저 FAILED가 되어도, 시작 중이던 실행의 정리가 끝나기 전에는
		// awaitCleanup()이 정리 완료를 보고하지 않아야 한다. (awaitTerminated()는 FAILED에서 바로 반환한다.)
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.Executor delaying = task -> new Thread(() -> {
			try {
				release.await();
			}
			catch ( InterruptedException e ) {
				return;
			}
			task.run();
		}).start();

		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sleep", "31.8");
		cfg.setStartTimeout(Duration.ofMillis(200));
		ProgramService svc = ProgramService.create(cfg);
		svc.setExecutor(delaying);

		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitRunning(5, TimeUnit.SECONDS));
		Assertions.assertEquals(Service.State.FAILED, svc.state());
		
		// 서비스는 FAILED지만 시작 중인 실행은 아직 끝나지 않았다.
		Assertions.assertFalse(svc.awaitCleanup(Duration.ofMillis(300)));

		// 실행을 풀어 주면 취소·정리가 끝나고, 그 뒤에는 자식 프로세스가 남아 있지 않아야 한다.
		release.countDown();
		Assertions.assertTrue(svc.awaitCleanup(Duration.ofSeconds(10)));
		Assertions.assertEquals(0, countSleepers("31.8"), "no child may remain once cleanup is reported");
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void awaitCleanup_after_stop_reports_cleanup() throws Exception {
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sleep", "31.9");
		ProgramService svc = ProgramService.create(cfg);
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		
		// 중지를 요청하지 않으면 종료 상태가 되지 않으므로 정리 완료가 아니다.
		Assertions.assertFalse(svc.awaitCleanup(Duration.ofMillis(100)));
		
		svc.stopAsync();
		Assertions.assertTrue(svc.awaitCleanup(Duration.ofSeconds(5)));
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
		Assertions.assertEquals(0, countSleepers("31.9"));
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void awaitCleanup_after_start_failure_returns_true() throws Exception {
		// 실행 파일이 없어 시작에 실패한 경우. 자식 프로세스가 없으므로 정리 완료로 본다.
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/nonexistent/program-service-test");
		ProgramService svc = ProgramService.create(cfg);
		svc.startAsync();
		Assertions.assertTrue(svc.awaitCleanup(Duration.ofSeconds(5)));
		Assertions.assertEquals(Service.State.FAILED, svc.state());
	}

	private static long countSleepers(String arg) {
		return ProcessHandle.current().descendants()
							.filter(ProcessHandle::isAlive)
							.filter(p -> p.info().arguments().map(a -> Arrays.asList(a).contains(arg)).orElse(false))
							.count();
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void start_timeout_does_not_affect_service_that_started_in_time() throws Exception {
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sleep", "30");
		cfg.setStartTimeout(Duration.ofMillis(200));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		try {
			Thread.sleep(500);			// startTimeout이 지나도 RUNNING이어야 한다.
			Assertions.assertEquals(Service.State.RUNNING, svc.state());
		}
		finally {
			svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		}
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void zero_start_timeout_means_no_limit() throws Exception {
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.Executor delaying = task -> new Thread(() -> {
			try {
				release.await();
			}
			catch ( InterruptedException e ) {
				return;
			}
			task.run();
		}).start();

		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sleep", "30");
		cfg.setStartTimeout(Duration.ZERO);
		ProgramService svc = ProgramService.create(cfg);
		svc.setExecutor(delaying);

		svc.startAsync();
		Thread.sleep(300);
		Assertions.assertEquals(Service.State.STARTING, svc.state());
		release.countDown();
		svc.awaitRunning(5, TimeUnit.SECONDS);
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
	}

	// ---------- restart backoff ----------

	@Test
	public void computeRestartDelay_doubles_from_at_least_one_second_up_to_max() {
		Duration base = Duration.ofSeconds(5);
		Duration max = Duration.ofMinutes(1);
		Assertions.assertEquals(base, ProgramService.computeRestartDelay(base, max, 0));
		Assertions.assertEquals(base, ProgramService.computeRestartDelay(base, max, 1));
		Assertions.assertEquals(Duration.ofSeconds(10), ProgramService.computeRestartDelay(base, max, 2));
		Assertions.assertEquals(Duration.ofSeconds(20), ProgramService.computeRestartDelay(base, max, 3));
		Assertions.assertEquals(Duration.ofSeconds(40), ProgramService.computeRestartDelay(base, max, 4));
		Assertions.assertEquals(max, ProgramService.computeRestartDelay(base, max, 5));
		Assertions.assertEquals(max, ProgramService.computeRestartDelay(base, max, 1_000));

		// restartDelay가 0이어도 1초부터 늘어난다.
		Assertions.assertEquals(Duration.ZERO, ProgramService.computeRestartDelay(Duration.ZERO, max, 1));
		Assertions.assertEquals(Duration.ofSeconds(2), ProgramService.computeRestartDelay(Duration.ZERO, max, 2));

		// maxRestartDelay <= restartDelay이면 backoff하지 않는다.
		Assertions.assertEquals(base, ProgramService.computeRestartDelay(base, base, 10));
		Assertions.assertEquals(Duration.ZERO, ProgramService.computeRestartDelay(Duration.ZERO, Duration.ZERO, 10));
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void short_runs_back_off_restarts() throws Exception {
		// 즉시 종료하는 명령: backoff가 없으면 1.5초 동안 수십 번 재시작되지만, backoff(최대 400ms) 적용 시
		// 0 → 400ms → 400ms … 로 몇 번에 그친다.
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, appendingCommand());
		cfg.setMaxRestartDelay(Duration.ofMillis(400));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(1_500);
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		long runs = countLines();
		Assertions.assertTrue(runs >= 2 && runs <= 7, "runs with backoff: " + runs);
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void stable_runs_do_not_back_off() throws Exception {
		// 매 실행이 stableRunTime 이상 지속되면 backoff 없이 restartDelay(50ms)로 재시작한다.
		// (backoff가 잘못 적용되면 두 번째부터 1초 이상 지연되어 2초 동안 2~3회에 그친다.)
		File counter = new File(m_workDir, "count.txt");
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sh", "-c",
										"echo tick >> " + counter.getAbsolutePath() + "; sleep 0.2");
		cfg.setRestartDelay(Duration.ofMillis(50));
		cfg.setMaxRestartDelay(Duration.ofSeconds(5));
		cfg.setStableRunTime(Duration.ofMillis(100));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(2_000);
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		long runs = countLines();
		Assertions.assertTrue(runs >= 5, "runs without backoff: " + runs);
	}

	// ---------- restart limit ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void exceeding_restart_limit_fails_service() throws Exception {
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, appendingCommand());
		cfg.setRestartLimit(3);
		cfg.setRestartLimitInterval(Duration.ofSeconds(30));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitTerminated(5, TimeUnit.SECONDS));
		Assertions.assertEquals(Service.State.FAILED, svc.state());
		Assertions.assertTrue(svc.failureCause() instanceof IllegalStateException
								&& svc.failureCause().getMessage().contains("restart limit exceeded"),
								"cause: " + svc.failureCause());
		Assertions.assertEquals(4, countLines(), "first run + 3 restarts");
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void restart_limit_counts_only_restarts_within_interval() throws Exception {
		// 재시작 간격(200ms)이 제한 구간(150ms)보다 길면, 구간 안의 재시작은 늘 1회 이하라 제한(1회)에 걸리지 않는다.
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, appendingCommand());
		cfg.setRestartDelay(Duration.ofMillis(200));
		cfg.setMaxRestartDelay(Duration.ofMillis(200));
		cfg.setRestartLimit(1);
		cfg.setRestartLimitInterval(Duration.ofMillis(150));
		ProgramService svc = ProgramService.create(cfg);

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		Thread.sleep(1_000);
		Assertions.assertEquals(Service.State.RUNNING, svc.state());
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertTrue(countLines() >= 3, "runs: " + countLines());
	}

	// ---------- 운영 조회 ----------

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void restart_count_and_last_exit_code_are_reported() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sh", "-c", "exit 7"));
		Assertions.assertEquals(0, svc.getRestartCount());
		Assertions.assertTrue(svc.getLastExitCode().isEmpty());

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		long due = System.currentTimeMillis() + 5_000;
		while ( svc.getRestartCount() < 3 && System.currentTimeMillis() < due ) {
			Thread.sleep(20);
		}
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertTrue(svc.getRestartCount() >= 3, "restarts: " + svc.getRestartCount());
		Assertions.assertEquals(7, svc.getLastExitCode().getAsInt());
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void last_exit_code_survives_stop() throws Exception {
		// 스스로 종료한 실행의 코드는, 이후 중지로 종료 코드 없이 끝난 실행이 있어도 유지된다.
		File marker = new File(m_workDir, "ran-once");
		ProgramService svc = ProgramService.create(config(RestartPolicy.ALWAYS, "/bin/sh", "-c",
								"if [ -e " + marker.getAbsolutePath() + " ]; then exec /bin/sleep 30; fi; "
								+ "touch " + marker.getAbsolutePath() + "; exit 3"));
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		long due = System.currentTimeMillis() + 5_000;
		while ( svc.getRestartCount() < 1 && System.currentTimeMillis() < due ) {
			Thread.sleep(20);
		}
		Thread.sleep(200);			// 두 번째 실행(sleep 30)이 시작되도록 기다린다.
		svc.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		Assertions.assertEquals(1, svc.getRestartCount());
		Assertions.assertEquals(3, svc.getLastExitCode().getAsInt());
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void no_restart_policy_reports_zero_restarts() throws Exception {
		ProgramService svc = ProgramService.create(config(RestartPolicy.NO, "/bin/sh", "-c", "exit 4"));
		svc.startAsync();
		Assertions.assertThrows(IllegalStateException.class, () -> svc.awaitTerminated(5, TimeUnit.SECONDS));
		Assertions.assertEquals(0, svc.getRestartCount());
		Assertions.assertEquals(4, svc.getLastExitCode().getAsInt());
	}

	// ---------- 검토 의견 재현 테스트 ----------

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void stop_while_execution_is_finishing_does_not_deadlock() throws Exception {
		// 실행이 자신의 lock을 쥔 채 종료 처리 중일 때 stopAsync()가 들어와도 교착되지 않아야 한다.
		// (예전에는 Guava의 lock을 쥔 doStop()이 실행의 cancel()에서 실행의 lock을, 실행의 종료 콜백은 실행의 lock을
		// 쥔 채 notifyStopped()에서 Guava의 lock을 기다려 교착되었다.)
		ProgramServiceConfig cfg = config(RestartPolicy.NO, "/bin/sh", "-c",
										"while [ ! -e done ]; do sleep 0.02; done");
		ProgramService svc = ProgramService.create(cfg);
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		java.lang.reflect.Field execField = ProgramService.class.getDeclaredField("m_exec");
		execField.setAccessible(true);
		CommandExecution exec = (CommandExecution)execField.get(svc);

		// 실행이 lock을 쥔 채 남기는 "completed:" 로그에서 실행을 붙잡는다.
		java.util.concurrent.CountDownLatch completing = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Logger blocking = (Logger)java.lang.reflect.Proxy.newProxyInstance(Logger.class.getClassLoader(),
								new Class<?>[] { Logger.class }, (proxy, method, args) -> {
			if ( method.getName().equals("debug") && args != null && args.length > 0
				&& String.valueOf(args[0]).startsWith("completed:") ) {
				completing.countDown();
				release.await(10, TimeUnit.SECONDS);
			}
			if ( method.getReturnType() == boolean.class ) return true;
			if ( method.getName().equals("getName") ) return "blocking";
			return null;
		});
		exec.setLogger(blocking);

		Files.writeString(new File(m_workDir, "done").toPath(), "done");
		Assertions.assertTrue(completing.await(5, TimeUnit.SECONDS), "execution did not reach completion");

		Thread stopper = new Thread(svc::stopAsync, "test-service-stopper");
		stopper.setDaemon(true);
		stopper.start();
		stopper.join(2_000);
		Assertions.assertFalse(stopper.isAlive(), "stopAsync() blocked while the execution was finishing");

		release.countDown();
		svc.awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals(Service.State.TERMINATED, svc.state());
		Assertions.assertNull(java.lang.management.ManagementFactory.getThreadMXBean().findDeadlockedThreads());
	}

	@Test
	@Timeout(value = 20_000, unit = TimeUnit.MILLISECONDS)
	public void stop_after_delayed_restart_terminates_after_process_cleanup() throws Exception {
		// 지연 재시작으로 시작된 실행을 중지하면, 프로세스가 정리된 뒤에 서비스가 TERMINATED가 되어야 한다.
		// (예전에는 지연 래퍼가 먼저 CANCELLED가 되어, SIGTERM을 무시하는 프로세스가 살아 있는 채 서비스가 종료되었다.)
		File pidFile = new File(m_workDir, "pid");
		ProgramServiceConfig cfg = config(RestartPolicy.ALWAYS, "/bin/sh", "-c",
								"if [ ! -e first ]; then touch first; exit 0; fi; "
								+ "trap '' TERM; echo $$ > pid; while true; do sleep 0.05; done");
		cfg.setRestartDelay(Duration.ofMillis(100));
		cfg.setMaxRestartDelay(Duration.ofMillis(100));
		ProgramService svc = ProgramService.create(cfg);
		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		long due = System.currentTimeMillis() + 5_000;
		while ( !(pidFile.exists() && pidFile.length() > 0) && System.currentTimeMillis() < due ) {
			Thread.sleep(20);
		}
		long pid = Long.parseLong(Files.readString(pidFile.toPath()).trim());
		try {
			svc.stopAsync().awaitTerminated(10, TimeUnit.SECONDS);
			Assertions.assertEquals(Service.State.TERMINATED, svc.state());
			Assertions.assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
									"process must be cleaned up before the service terminates");
		}
		finally {
			ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
		}
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void config_interpolation_keeps_json_special_characters() throws Exception {
		// 따옴표·역슬래시가 든 값이 JSON 구문을 깨지 않고 그대로 들어가야 한다.
		String key = "programservice.test.value";
		System.setProperty(key, "quoted\"value\\tail");
		try {
			File jsonFile = new File(m_workDir, "config.json");
			Files.writeString(jsonFile.toPath(),
							"{\"commandLine\":[\"/bin/echo\",\"${sys:" + key + "}\"],\"restartPolicy\":\"NO\"}");
			ProgramService svc = ProgramService.create(jsonFile);
			Assertions.assertTrue(svc.toString().contains("quoted\"value\\tail"), svc.toString());
		}
		finally {
			System.clearProperty(key);
		}
	}

	@Test
	@Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
	public void service_uses_a_copy_of_its_config() throws Exception {
		// 서비스 생성 후 원래 설정 객체를 바꿔도 서비스의 동작은 달라지지 않아야 한다.
		ProgramServiceConfig cfg = config(RestartPolicy.NO, "/bin/echo", "original");
		ProgramService svc = ProgramService.create(cfg);
		cfg.setCommandLine(Arrays.asList("/bin/echo", "changed"));

		svc.startAsync().awaitRunning(5, TimeUnit.SECONDS);
		svc.awaitTerminated(5, TimeUnit.SECONDS);
		Assertions.assertEquals("original",
						Files.readString(new File(m_workDir, "application.log").toPath(), StandardCharsets.UTF_8).trim());
	}

	@Test
	public void create_rejects_empty_command_line() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		Assertions.assertThrows(IllegalArgumentException.class, () -> ProgramService.create(cfg));
		cfg.setCommandLine(Arrays.asList());
		Assertions.assertThrows(IllegalArgumentException.class, () -> ProgramService.create(cfg));
	}
}
