package utils.thread;

import java.util.Date;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.Execution;


/**
 * {@link Execution}의 제한 시간을 관리하는 타이머.
 * <p>
 * {@link #setTimer(Execution, long, TimeUnit)}로 예약한 시간이 지나도록 실행이 끝나지 않으면
 * {@link Execution#cancel(boolean) cancel(true)}을 부른다. {@link Execution#setTimeout(long, TimeUnit)}이
 * {@code utils.async.Executions.getTimer()}로 얻은 공용 인스턴스를 통해 이 클래스를 쓴다.
 * <ul>
 *   <li>예약 수에 제한이 없다. (2026-10-31 이전 구현은 64개까지만 보관하고, 넘치면 가장 늦은 기한의 예약을
 *       알림 없이 버렸다.)</li>
 *   <li>실행이 먼저 끝나면 예약을 곧바로 해제하므로, 끝난 실행이 자리를 차지하지 않는다. (이전 구현은 끝난
 *       실행을 기한이 될 때까지 큐에 남겨 두었다.)</li>
 *   <li>같은 실행에 다시 예약하면 기존 예약을 바꾸지 않고 <b>새 예약을 더한다</b>. 기한을 늘리려면 기존 예약을
 *       {@link TimeoutHandle#cancel()}로 해제한 뒤 다시 예약한다.</li>
 *   <li>시간은 시계 변경의 영향을 받지 않는 단조 시계로 잰다. 단, {@link #setTimer(Execution, Date)}는 기한을
 *       등록 시점의 남은 시간으로 바꿔 예약한다.</li>
 *   <li>{@code cancel(true)}는 시간만 재는 스케줄러 스레드가 아니라 별도의 취소 스레드에서 부른다. 취소가 오래
 *       걸리거나(상태 정리, 원격 중지 명령 등) 실행의 lock을 기다려도 타이머 쪽 lock을 쥔 채 기다리는 일은 없다.
 *       (이전 구현은 타이머 lock을 쥔 채 {@code cancel(true)}를 불렀다.)</li>
 *   <li>취소 스레드는 최대 {@value #MAX_CANCELLATION_THREADS}개다. 그만큼의 {@code cancel(true)}가 동시에 막혀
 *       있으면, 그 뒤에 만료된 예약의 취소 요청은 앞의 것이 끝날 때까지 대기열에서 기다린다(스레드를 무한히 늘리지
 *       않는다). 쉬는 스레드는 일정 시간 뒤 정리된다.</li>
 * </ul>
 * 모든 스레드는 데몬 스레드다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Timer {
	private static final Logger s_logger = LoggerFactory.getLogger(Timer.class);

	/** 동시에 {@code cancel(true)}를 부를 수 있는 최대 스레드 수. */
	public static final int MAX_CANCELLATION_THREADS = 4;
	private static final long CANCELLATION_THREAD_KEEP_ALIVE_SECONDS = 30;

	/** 시간만 재는 스레드. 만료된 예약은 바로 {@link #m_canceller}로 넘긴다. */
	private final ScheduledThreadPoolExecutor m_scheduler;
	/** 만료된 실행에 {@code cancel(true)}를 부르는 스레드들. 최대 {@link #MAX_CANCELLATION_THREADS}개. */
	private final ThreadPoolExecutor m_canceller;

	/**
	 * {@link Timer#setTimer}로 등록한 예약의 핸들.
	 * <p>
	 * 이 핸들은 <b>예약</b>만 나타낸다. 다음 세 가지를 구분한다.
	 * <ul>
	 *   <li><b>예약 해제</b> — {@link #cancel()}. 기한 전에 부르면 그 예약은 실행을 취소하지 않는다. 대상
	 *       실행을 취소하는 기능이 아니다.</li>
	 *   <li><b>취소 요청 접수</b> — 기한이 되어 취소 스레드에 {@code cancel(true)} 요청을 넘긴 상태
	 *       ({@link #isExpired()}). 이후에는 이 핸들로 요청을 철회할 수 없다. 요청이 처리되었다는 뜻은 아니다.</li>
	 *   <li><b>대상 실행 종료</b> — 대상 실행에서 확인한다({@link Execution#waitForFinished()} 등). 취소 요청이
	 *       정상 종료와 경합하면 실행이 취소되지 않고 정상 종료할 수도 있다.</li>
	 * </ul>
	 * 대상 실행이 끝나면 예약은 자동으로 해제된다. {@link Timer#shutdown()}도 남은 예약을 모두 해제한다.
	 */
	public static final class TimeoutHandle {
		private final Future<?> m_future;

		private TimeoutHandle(Future<?> future) {
			m_future = future;
		}

		/**
		 * 예약을 해제한다. 대상 실행은 취소하지 않는다.
		 *
		 * @return	기한 전에 해제했으면 {@code true}. 이미 만료되었거나 해제된 경우 {@code false}.
		 */
		public boolean cancel() {
			return m_future.cancel(false);
		}

		/**
		 * 예약이 아직 남아 있는지(만료되지도 해제되지도 않았는지) 여부를 반환한다.
		 *
		 * @return	예약이 남아 있으면 {@code true}.
		 */
		public boolean isPending() {
			return !m_future.isDone();
		}

		/**
		 * 예약이 해제되었는지(기한 전에 {@link #cancel()}, 대상 실행 종료 또는 타이머 종료로) 여부를 반환한다.
		 *
		 * @return	해제되었으면 {@code true}.
		 */
		public boolean isCancelled() {
			return m_future.isCancelled();
		}

		/**
		 * 기한이 되어 취소 요청을 취소 스레드에 넘겼는지 여부를 반환한다. 요청이 처리되었는지나 대상 실행이
		 * 끝났는지는 뜻하지 않는다.
		 *
		 * @return	만료되었으면 {@code true}.
		 */
		public boolean isExpired() {
			return m_future.isDone() && !m_future.isCancelled();
		}

		@Override
		public String toString() {
			String state = isCancelled() ? "cancelled" : (isExpired() ? "expired" : "pending");
			return "TimeoutHandle[" + state + "]";
		}
	}

	public Timer() {
		m_scheduler = new ScheduledThreadPoolExecutor(1, daemonThreads("execution-timer"));
		// 예약이 해제되면(실행이 먼저 끝난 경우) 기한까지 기다리지 않고 큐에서 바로 뺀다.
		m_scheduler.setRemoveOnCancelPolicy(true);

		// 스레드 수를 제한하고, 넘치는 취소 요청은 대기열에서 기다리게 한다. CallerRunsPolicy 등으로 스케줄러
		// 스레드에서 직접 취소하면 시간 재기가 다시 막히므로 쓰지 않는다(대기열은 제한이 없어 거부되지 않는다).
		m_canceller = new ThreadPoolExecutor(MAX_CANCELLATION_THREADS, MAX_CANCELLATION_THREADS,
											CANCELLATION_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
											new LinkedBlockingQueue<>(), daemonThreads("execution-timer-cancel"));
		m_canceller.allowCoreThreadTimeOut(true);
	}

	/**
	 * 타이머를 멈춘다. 아직 만료되지 않은 예약은 모두 해제하며({@link TimeoutHandle#isCancelled()}가
	 * {@code true}가 된다), 이후의 {@code setTimer} 호출은 거부한다. 이미 취소 스레드에 넘긴 취소 요청은 처리된다.
	 */
	public void shutdown() {
		// shutdownNow()는 대기 중인 예약을 큐에서 빼서 돌려줄 뿐 그 future를 완료시키지 않는다. 핸들이 미완료로
		// 남지 않도록 직접 해제한다. 동시에 들어온 setTimer는 이 목록에 들거나 거부되므로, 접수된 핸들은 모두 끝난다.
		for ( Runnable pending: m_scheduler.shutdownNow() ) {
			if ( pending instanceof Future<?> future ) {
				future.cancel(false);
			}
		}
		m_canceller.shutdown();
	}

	/**
	 * 주어진 시각까지 실행이 끝나지 않으면 {@code cancel(true)}를 부르도록 예약한다.
	 * <p>
	 * 기한은 등록 시점의 남은 시간으로 바뀌어 단조 시계로 잰다. 이미 지난 시각이면 곧바로 취소를 요청한다.
	 *
	 * @param execution	제한 시간을 걸 실행 (non-null)
	 * @param due		기한 (non-null)
	 * @return	예약 핸들. 실행이 끝나면 자동으로 해제되며, 직접 해제해 제한 시간을 없앨 수도 있다.
	 * @throws IllegalStateException	{@link #shutdown()} 뒤에 호출한 경우
	 */
	public TimeoutHandle setTimer(Execution<?> execution, Date due) {
		Preconditions.checkNotNullArgument(due, "due is null");

		// 과거 시각을 먼저 걸러낸다. 그렇지 않으면 아주 이른 시각(예: Long.MIN_VALUE)에서 뺄셈이 넘쳐 먼 미래가 된다.
		long dueMillis = due.getTime();
		long nowMillis = System.currentTimeMillis();
		long delayMillis = (dueMillis <= nowMillis) ? 0 : dueMillis - nowMillis;
		return schedule(execution, delayMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * 주어진 시간 안에 실행이 끝나지 않으면 {@code cancel(true)}를 부르도록 예약한다.
	 *
	 * @param execution	제한 시간을 걸 실행 (non-null)
	 * @param timeout	제한 시간. 0 이하이면 곧바로 취소를 요청한다.
	 * @param unit		{@code timeout}의 단위 (non-null)
	 * @return	예약 핸들. 실행이 끝나면 자동으로 해제되며, 직접 해제해 제한 시간을 없앨 수도 있다.
	 * @throws IllegalStateException	{@link #shutdown()} 뒤에 호출한 경우
	 */
	public TimeoutHandle setTimer(Execution<?> execution, long timeout, TimeUnit unit) {
		Preconditions.checkNotNullArgument(unit, "unit is null");
		return schedule(execution, timeout, unit);
	}

	private TimeoutHandle schedule(Execution<?> execution, long delay, TimeUnit unit) {
		Preconditions.checkNotNullArgument(execution, "execution is null");

		ScheduledFuture<?> handle;
		try {
			handle = m_scheduler.schedule(() -> requestCancellation(execution), delay, unit);
		}
		catch ( RejectedExecutionException e ) {
			throw new IllegalStateException("Timer has been shut down", e);
		}
		// 실행이 먼저 끝나면 예약을 바로 해제한다. 이미 끝난 실행이면 등록 즉시 해제된다.
		// ScheduledFuture.cancel(false)는 대기·콜백 없이 큐에서 빼기만 하므로, 실행의 lock 안에서 불려도 안전하다.
		execution.whenFinished(result -> handle.cancel(false));
		return new TimeoutHandle(handle);
	}

	/**
	 * 만료된 예약의 취소 요청을 취소 스레드에 넘긴다. 스케줄러 스레드에서 불린다.
	 */
	private void requestCancellation(Execution<?> execution) {
		try {
			m_canceller.execute(() -> cancelOnTimeout(execution));
		}
		catch ( RejectedExecutionException e ) {
			// 타이머를 멈추는 중이다.
			s_logger.debug("timer is shutting down; timeout is not applied: {}", execution);
		}
	}

	private static void cancelOnTimeout(Execution<?> execution) {
		try {
			if ( execution.isDone() ) {
				return;
			}
			s_logger.debug("timeout expired; cancelling: {}", execution);
			if ( !execution.cancel(true) ) {
				// 정상 종료와 경합했을 수 있으므로 오류로 보지 않는다. 진단을 위해 대상 상태와 함께 남긴다.
				s_logger.debug("timeout cancellation was not accepted: {}, state={}", execution, execution.getState());
			}
		}
		catch ( Throwable e ) {
			s_logger.warn("failed to cancel a timed-out execution: {}, cause={}", execution, ""+e);
		}
	}

	private static ThreadFactory daemonThreads(String prefix) {
		AtomicInteger seq = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, prefix + "-" + seq.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
