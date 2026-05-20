package utils.async.command;


import java.io.File;
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

import utils.async.command.ProgramServiceConfig.RestartPolicy;
import org.junit.jupiter.api.Timeout;


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
	 * {@code application.log}는 매 실행마다 덮어쓰여지므로 누적 횟수 측정에 부적합.
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
}
