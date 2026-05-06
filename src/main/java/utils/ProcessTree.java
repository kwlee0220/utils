package utils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Root process와 그 descendant process들을 하나의 process tree로 다루기 위한 helper.
 * <p>
 * {@link ProcessHandle#descendants()}는 호출 시점의 snapshot이므로, 종료/대기 중에는
 * 매번 최신 snapshot을 다시 조회한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ProcessTree {
	private static final Logger s_logger = LoggerFactory.getLogger(ProcessTree.class);
	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);

	private final Process m_root;
	// 동시 호출(예: cancelWork와 executeWork finally가 동시에 terminate를 호출하는 패턴)에서
	// 안전하게 mutate/iterate되도록 thread-safe set을 사용한다.
	private final Set<ProcessHandle> m_descendants = ConcurrentHashMap.newKeySet();

	private ProcessTree(Process root) {
		Utilities.checkNotNullArgument(root, "root process must not be null");

		m_root = root;
	}

	public static ProcessTree of(Process root) {
		return new ProcessTree(root);
	}

	/**
	 * Process tree가 살아있는지 여부를 반환한다.
	 * <p>
	 * root가 살아있는 동안에는 descendants snapshot을 갱신하여 root 사후 reparent 가능성에 대비한다.
	 * root가 이미 죽은 뒤에는 OS의 {@link ProcessHandle#descendants()}가 빈 stream을 반환하므로
	 * snapshot 갱신을 건너뛰고 캐시된 결과로 판정한다.
	 *
	 * @return root 또는 descendant process가 살아있으면 {@code true}.
	 */
	public boolean isAlive() {
		if ( m_root.isAlive() ) {
			refreshDescendants();
			return true;
		}
		return hasLiveDescendant();
	}

	/**
	 * 현재 시점에 root process 아래에 있는 descendants를 내부 snapshot에 추가한다.
	 * <p>
	 * Root process가 먼저 종료되면 descendants가 reparent되어 이후 조회에서 사라질 수 있으므로,
	 * 실행 중 주기적으로 호출해 발견된 process handle을 보존한다.
	 */
	public void refresh() {
		refreshDescendants();
	}

	/**
	 * Root process가 종료될 때까지 대기한다.
	 * <p>
	 * 대기 중 주기적으로 descendants를 snapshot하여 root 종료 후 reparent될 수 있는
	 * child process handle을 보존한다.
	 *
	 * @param refreshInterval	descendant snapshot 갱신 주기.
	 * @throws InterruptedException 대기 중 인터럽트된 경우.
	 */
	public void waitForRootTerminated(Duration refreshInterval) throws InterruptedException {
		Utilities.checkNotNullArgument(refreshInterval, "refreshInterval must not be null");
		Utilities.checkArgument(!refreshInterval.isNegative() && !refreshInterval.isZero(),
								"refreshInterval must be positive: %s", refreshInterval);

		while ( true ) {
			refresh();
			if ( !m_root.isAlive() ) {
				return;
			}
			m_root.waitFor(refreshInterval.toNanos(), TimeUnit.NANOSECONDS);
		}
	}

	/**
	 * Root process가 종료될 때까지 지정된 시간 동안 대기한다.
	 * <p>
	 * 대기 중 주기적으로 descendants를 snapshot하여 root 종료 후 reparent될 수 있는
	 * child process handle을 보존한다.
	 *
	 * @param refreshInterval	descendant snapshot 갱신 주기.
	 * @param timeout			대기 시간.
	 * @return 제한 시간 안에 root가 종료되면 {@code true}, 아니면 {@code false}.
	 * @throws InterruptedException 대기 중 인터럽트된 경우.
	 */
	public boolean waitForRootTerminated(Duration refreshInterval, Duration timeout)
		throws InterruptedException {
		Utilities.checkNotNullArgument(refreshInterval, "refreshInterval must not be null");
		Utilities.checkArgument(!refreshInterval.isNegative() && !refreshInterval.isZero(),
								"refreshInterval must be positive: %s", refreshInterval);
		Utilities.checkNotNullArgument(timeout, "timeout must not be null");
		Utilities.checkArgument(!timeout.isNegative(), "timeout must not be negative: %s", timeout);

		long dueNanos = System.nanoTime() + timeout.toNanos();
		while ( true ) {
			refresh();
			if ( !m_root.isAlive() ) {
				return true;
			}

			long remainingNanos = dueNanos - System.nanoTime();
			if ( remainingNanos <= 0 ) {
				return false;
			}
			long waitNanos = Math.min(refreshInterval.toNanos(), remainingNanos);
			m_root.waitFor(waitNanos, TimeUnit.NANOSECONDS);
		}
	}

	/**
	 * Root process와 descendants에 정상 종료(SIGTERM 등)를 요청한다.
	 * <p>
	 * 본 메소드는 종료 요청만 보내며 실제 종료까지 대기하지 않는다. 종료 대기가 필요하면
	 * {@link #waitForTerminated(Duration)}을 함께 사용하거나, grace 후 강제 종료까지 한 번에
	 * 처리하려면 {@link #terminate(Duration)}을 사용한다.
	 */
	public void destroy() {
		if ( s_logger.isDebugEnabled() ) {
			s_logger.debug("destroy requested: root pid={}, descendants={}",
							m_root.pid(), m_descendants.size());
		}
		destroyDescendants(false);
		if ( m_root.isAlive() ) {
			m_root.destroy();
		}
	}

	/**
	 * Root process와 descendants에 강제 종료(SIGKILL 등)를 요청한다.
	 * <p>
	 * 본 메소드는 강제 종료 요청만 보내며 실제 종료까지 대기하지 않는다. 종료 대기가 필요하면
	 * {@link #waitForTerminated(Duration)}을 함께 사용한다.
	 */
	public void destroyForcibly() {
		if ( s_logger.isDebugEnabled() ) {
			s_logger.debug("destroyForcibly requested: root pid={}, descendants={}",
							m_root.pid(), m_descendants.size());
		}
		destroyDescendants(true);
		if ( m_root.isAlive() ) {
			m_root.destroyForcibly();
		}
	}

	/**
	 * Process tree가 종료될 때까지 지정된 시간 동안 대기한다.
	 *
	 * @param timeout	대기 시간.
	 * @return 제한 시간 안에 모두 종료되면 {@code true}, 아니면 {@code false}.
	 * @throws InterruptedException 대기 중 인터럽트된 경우.
	 */
	public boolean waitForTerminated(Duration timeout) throws InterruptedException {
		Utilities.checkNotNullArgument(timeout, "timeout must not be null");
		Utilities.checkArgument(!timeout.isNegative(), "timeout must not be negative: %s", timeout);

		long dueNanos = System.nanoTime() + timeout.toNanos();
		while ( isAlive() ) {
			long remainingNanos = dueNanos - System.nanoTime();
			if ( remainingNanos <= 0 ) {
				return false;
			}
			TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, DEFAULT_POLL_INTERVAL.toNanos()));
		}
		return true;
	}

	/**
	 * 정상 종료를 요청하고 grace 시간 안에 종료되지 않으면 강제 종료한다.
	 * <p>
	 * 흐름:
	 * <ol>
	 *   <li>{@link #isAlive()}가 {@code false}이면 즉시 반환.</li>
	 *   <li>{@link #destroy()}로 정상 종료 요청.</li>
	 *   <li>{@link #waitForTerminated(Duration)}로 {@code grace} 시간만큼 대기.</li>
	 *   <li>그 안에 종료되지 않으면 {@link #destroyForcibly()}로 강제 종료.</li>
	 * </ol>
	 * <p>
	 * <b>인터럽트 처리</b>: 대기 중 호출 스레드가 인터럽트되면 본 메소드는 best-effort cleanup을 위해
	 * {@link #destroyForcibly()}를 호출한 뒤 인터럽트 플래그를 복원하고 정상 반환한다
	 * ({@link InterruptedException}을 호출자에게 전파하지 않는다). 호출자가 인터럽트 발생을
	 * 감지해야 한다면 본 메소드 호출 후 {@link Thread#isInterrupted()}로 검사해야 한다.
	 *
	 * @param grace	강제 종료 전 대기 시간 (non-null, 음수가 아니어야 함)
	 * @throws IllegalArgumentException	{@code grace}가 {@code null}이거나 음수인 경우
	 */
	public void terminate(Duration grace) {
		Utilities.checkNotNullArgument(grace, "grace must not be null");
		Utilities.checkArgument(!grace.isNegative(), "grace must not be negative: %s", grace);

		if ( !isAlive() ) {
			return;
		}

		s_logger.debug("terminate requested: root pid={}, grace={}", m_root.pid(), grace);
		destroy();
		try {
			if ( !waitForTerminated(grace) ) {
				s_logger.debug("graceful termination did not complete within grace, escalating: root pid={}",
								m_root.pid());
				destroyForcibly();
			}
		}
		catch ( InterruptedException e ) {
			s_logger.debug("interrupted during graceful termination, escalating: root pid={}", m_root.pid());
			destroyForcibly();
			Thread.currentThread().interrupt();
		}
	}

	private boolean hasLiveDescendant() {
		return m_descendants.stream().anyMatch(ProcessHandle::isAlive);
	}

	private void destroyDescendants(boolean forcibly) {
		refreshDescendants();
		for ( ProcessHandle descendant: List.copyOf(m_descendants) ) {
			if ( descendant.isAlive() ) {
				if ( forcibly ) {
					descendant.destroyForcibly();
				}
				else {
					descendant.destroy();
				}
			}
		}
	}

	private void refreshDescendants() {
		m_root.toHandle().descendants().forEach(m_descendants::add);
	}
}
