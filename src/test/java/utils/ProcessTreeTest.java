package utils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link ProcessTree}의 라이프사이클과 인자 검증 동작을 검증한다.
 * <p>
 * 실제 sub-process를 띄우는 통합 테스트이므로 {@code sleep} 명령어가 있는 환경(Linux/Unix)에서
 * 동작한다. Windows에서는 PASS 하지 않을 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProcessTreeTest {
	private Process m_proc;

	@AfterEach
	public void tearDown() {
		// 테스트 도중 process가 살아있으면 누수 방지를 위해 강제 종료한다.
		if ( m_proc != null && m_proc.isAlive() ) {
			m_proc.destroyForcibly();
		}
	}

	private Process startSleep(double seconds) throws Exception {
		m_proc = new ProcessBuilder("sleep", String.valueOf(seconds))
									.redirectErrorStream(true)
									.start();
		return m_proc;
	}

	private Process startTrue() throws Exception {
		// 즉시 종료하는 명령. exit 0.
		m_proc = new ProcessBuilder("true").redirectErrorStream(true).start();
		return m_proc;
	}

	// ---- of / 생성자 ----

	@Test
	public void of_null_root_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ProcessTree.of(null);
		});
	}

	@Test
	public void of_returns_non_null_for_running_process() throws Exception {
		Process p = startSleep(2);
		Assertions.assertNotNull(ProcessTree.of(p));
	}

	// ---- isAlive ----

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void isAlive_returns_true_for_running_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		Assertions.assertTrue(pt.isAlive());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void isAlive_returns_false_after_process_terminates() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		Assertions.assertFalse(pt.isAlive());
	}

	// ---- waitForRootTerminated(Duration) 인자 검증 ----

	@Test
	public void waitForRootTerminated_null_refresh_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(null);
		});
	}

	@Test
	public void waitForRootTerminated_zero_refresh_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(Duration.ZERO);
		});
	}

	@Test
	public void waitForRootTerminated_negative_refresh_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(-1));
		});
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void waitForRootTerminated_returns_when_process_exits() throws Exception {
		// 매우 짧게 종료하는 process — 폴링이 빨리 발견해야 한다.
		Process p = startSleep(0.1);
		ProcessTree pt = ProcessTree.of(p);

		pt.waitForRootTerminated(Duration.ofMillis(20));   // exception 없이 반환

		Assertions.assertFalse(p.isAlive());
	}

	// ---- waitForRootTerminated(Duration, Duration) 인자 검증 ----

	@Test
	public void waitForRootTerminated_with_timeout_null_refresh_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(null, Duration.ofMillis(100));
		});
	}

	@Test
	public void waitForRootTerminated_with_timeout_null_timeout_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(20), null);
		});
	}

	@Test
	public void waitForRootTerminated_with_timeout_negative_timeout_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(20), Duration.ofMillis(-1));
		});
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void waitForRootTerminated_returns_true_within_timeout() throws Exception {
		Process p = startSleep(0.1);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForRootTerminated(Duration.ofMillis(20), Duration.ofSeconds(2));

		Assertions.assertTrue(result);
		Assertions.assertFalse(p.isAlive());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void waitForRootTerminated_returns_false_on_timeout() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForRootTerminated(Duration.ofMillis(20), Duration.ofMillis(150));

		Assertions.assertFalse(result);
		Assertions.assertTrue(p.isAlive());   // 아직 sleep 중
	}

	// ---- waitForTerminated 인자 검증 ----

	@Test
	public void waitForTerminated_null_timeout_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForTerminated(null);
		});
	}

	@Test
	public void waitForTerminated_negative_timeout_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).waitForTerminated(Duration.ofMillis(-1));
		});
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void waitForTerminated_returns_true_when_already_terminated() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		Assertions.assertTrue(pt.waitForTerminated(Duration.ofMillis(100)));
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void waitForTerminated_returns_false_on_timeout_for_alive_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForTerminated(Duration.ofMillis(150));

		Assertions.assertFalse(result);
		Assertions.assertTrue(p.isAlive());
	}

	// ---- destroy / destroyForcibly ----

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void destroy_terminates_running_process() throws Exception {
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.destroy();
		Assertions.assertTrue(pt.waitForTerminated(Duration.ofSeconds(2)));
		Assertions.assertFalse(p.isAlive());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void destroyForcibly_terminates_running_process() throws Exception {
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.destroyForcibly();
		Assertions.assertTrue(pt.waitForTerminated(Duration.ofSeconds(2)));
		Assertions.assertFalse(p.isAlive());
	}

	// ---- terminate ----

	@Test
	public void terminate_null_grace_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).terminate(null);
		});
	}

	@Test
	public void terminate_negative_grace_rejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Process p = startSleep(2);
			ProcessTree.of(p).terminate(Duration.ofMillis(-1));
		});
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void terminate_kills_running_process_within_grace() throws Exception {
		// SIGTERM을 정상 처리하는 sleep — grace 안에 종료되어야 한다.
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.terminate(Duration.ofSeconds(2));

		Assertions.assertFalse(p.isAlive());
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void terminate_is_noop_when_already_dead() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		// 이미 죽은 프로세스에 대한 terminate은 즉시 반환해야 한다.
		long before = System.currentTimeMillis();
		pt.terminate(Duration.ofSeconds(5));
		long elapsed = System.currentTimeMillis() - before;

		Assertions.assertTrue(elapsed < 200,
							"이미 죽은 프로세스의 terminate는 즉시 반환해야 함 (실제: " + elapsed + "ms)");
	}

	// ---- refresh ----

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void refresh_does_not_throw_on_running_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		pt.refresh();   // 예외 없이 통과해야 함
		pt.refresh();   // 여러 번 호출해도 안전해야 함
	}

	@Test
	@Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS)
	public void refresh_does_not_throw_on_dead_process() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		pt.refresh();   // 예외 없이 통과해야 함
	}
}
