package utils;

import java.time.Duration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

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

	@After
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

	@Test(expected = IllegalArgumentException.class)
	public void of_null_root_rejected() {
		ProcessTree.of(null);
	}

	@Test
	public void of_returns_non_null_for_running_process() throws Exception {
		Process p = startSleep(2);
		Assert.assertNotNull(ProcessTree.of(p));
	}

	// ---- isAlive ----

	@Test(timeout = 5_000)
	public void isAlive_returns_true_for_running_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		Assert.assertTrue(pt.isAlive());
	}

	@Test(timeout = 5_000)
	public void isAlive_returns_false_after_process_terminates() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		Assert.assertFalse(pt.isAlive());
	}

	// ---- waitForRootTerminated(Duration) 인자 검증 ----

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_null_refresh_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_zero_refresh_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(Duration.ZERO);
	}

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_negative_refresh_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(-1));
	}

	@Test(timeout = 5_000)
	public void waitForRootTerminated_returns_when_process_exits() throws Exception {
		// 매우 짧게 종료하는 process — 폴링이 빨리 발견해야 한다.
		Process p = startSleep(0.1);
		ProcessTree pt = ProcessTree.of(p);

		pt.waitForRootTerminated(Duration.ofMillis(20));   // exception 없이 반환

		Assert.assertFalse(p.isAlive());
	}

	// ---- waitForRootTerminated(Duration, Duration) 인자 검증 ----

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_with_timeout_null_refresh_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(null, Duration.ofMillis(100));
	}

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_with_timeout_null_timeout_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(20), null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void waitForRootTerminated_with_timeout_negative_timeout_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForRootTerminated(Duration.ofMillis(20), Duration.ofMillis(-1));
	}

	@Test(timeout = 5_000)
	public void waitForRootTerminated_returns_true_within_timeout() throws Exception {
		Process p = startSleep(0.1);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForRootTerminated(Duration.ofMillis(20), Duration.ofSeconds(2));

		Assert.assertTrue(result);
		Assert.assertFalse(p.isAlive());
	}

	@Test(timeout = 5_000)
	public void waitForRootTerminated_returns_false_on_timeout() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForRootTerminated(Duration.ofMillis(20), Duration.ofMillis(150));

		Assert.assertFalse(result);
		Assert.assertTrue(p.isAlive());   // 아직 sleep 중
	}

	// ---- waitForTerminated 인자 검증 ----

	@Test(expected = IllegalArgumentException.class)
	public void waitForTerminated_null_timeout_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForTerminated(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void waitForTerminated_negative_timeout_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).waitForTerminated(Duration.ofMillis(-1));
	}

	@Test(timeout = 5_000)
	public void waitForTerminated_returns_true_when_already_terminated() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		Assert.assertTrue(pt.waitForTerminated(Duration.ofMillis(100)));
	}

	@Test(timeout = 5_000)
	public void waitForTerminated_returns_false_on_timeout_for_alive_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		boolean result = pt.waitForTerminated(Duration.ofMillis(150));

		Assert.assertFalse(result);
		Assert.assertTrue(p.isAlive());
	}

	// ---- destroy / destroyForcibly ----

	@Test(timeout = 5_000)
	public void destroy_terminates_running_process() throws Exception {
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.destroy();
		Assert.assertTrue(pt.waitForTerminated(Duration.ofSeconds(2)));
		Assert.assertFalse(p.isAlive());
	}

	@Test(timeout = 5_000)
	public void destroyForcibly_terminates_running_process() throws Exception {
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.destroyForcibly();
		Assert.assertTrue(pt.waitForTerminated(Duration.ofSeconds(2)));
		Assert.assertFalse(p.isAlive());
	}

	// ---- terminate ----

	@Test(expected = IllegalArgumentException.class)
	public void terminate_null_grace_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).terminate(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void terminate_negative_grace_rejected() throws Exception {
		Process p = startSleep(2);
		ProcessTree.of(p).terminate(Duration.ofMillis(-1));
	}

	@Test(timeout = 5_000)
	public void terminate_kills_running_process_within_grace() throws Exception {
		// SIGTERM을 정상 처리하는 sleep — grace 안에 종료되어야 한다.
		Process p = startSleep(10);
		ProcessTree pt = ProcessTree.of(p);

		pt.terminate(Duration.ofSeconds(2));

		Assert.assertFalse(p.isAlive());
	}

	@Test(timeout = 5_000)
	public void terminate_is_noop_when_already_dead() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		// 이미 죽은 프로세스에 대한 terminate은 즉시 반환해야 한다.
		long before = System.currentTimeMillis();
		pt.terminate(Duration.ofSeconds(5));
		long elapsed = System.currentTimeMillis() - before;

		Assert.assertTrue("이미 죽은 프로세스의 terminate는 즉시 반환해야 함 (실제: " + elapsed + "ms)",
							elapsed < 200);
	}

	// ---- refresh ----

	@Test(timeout = 5_000)
	public void refresh_does_not_throw_on_running_process() throws Exception {
		Process p = startSleep(2);
		ProcessTree pt = ProcessTree.of(p);

		pt.refresh();   // 예외 없이 통과해야 함
		pt.refresh();   // 여러 번 호출해도 안전해야 함
	}

	@Test(timeout = 5_000)
	public void refresh_does_not_throw_on_dead_process() throws Exception {
		Process p = startTrue();
		p.waitFor();
		ProcessTree pt = ProcessTree.of(p);

		pt.refresh();   // 예외 없이 통과해야 함
	}
}
