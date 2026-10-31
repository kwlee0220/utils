package utils.thread;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.LongVariableSmoother;
import utils.Preconditions;
import utils.Throwables;
import utils.UnitUtils;


/**
 * 전용 쓰레드에서 {@link RecurringWork}를 주기적으로 수행하는 {@link RecurringSchedule}.
 * <p>
 * 주기 값의 변경({@link #setInterval(long)})은 다음 대기부터 적용되며, 이미 시작한 대기 시간은 바꾸지 않는다.
 *
 * @author Kang-Woo Lee
 */
public class RecurringScheduleThread implements RecurringSchedule, LoggerSettable {
	static final Logger s_logger = LoggerFactory.getLogger(RecurringScheduleThread.class);

	private static final int STATE_NOT_STARTED = 0;
	private static final int STATE_STARTING = 1;
	private static final int STATE_RUNNING_IDLE = 2;
	private static final int STATE_RUNNING_WORKING = 3;
	private static final int STATE_STOPPING = 4;
	private static final int STATE_STOPPED = 5;

	// 내부 상태 → 인터페이스 상태. 시작 전은 STOPPED로 보고한다(RecurringScheduleImpl과 같다; State에 시작 전 상태가 없다).
	// 2026-10-31 이전에는 시작 전을 STARTING, 작업 중(RUNNING_WORKING)을 IDLE로 보고해 WORKING이 나오지 않았다.
	private static final State[] STATE_MAPPING = new State[] {
		State.STOPPED, State.STARTING, State.IDLE, State.WORKING, State.STOPPING, State.STOPPED,
	};

	private final String m_threadName;
	private final RecurringWork m_work;
	private final long m_initialDelay;
	private final boolean m_byRate;

	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();

	private final Worker m_wrapper = new Worker();
	@GuardedBy("m_lock") private int m_state = STATE_NOT_STARTED;
	// onStarted() 호출이 끝났는지 여부. start()는 상태가 아니라 이 값을 기다린다(초기화 중 stop()으로 상태가 STOPPING이
	// 되어도 초기화가 끝날 때까지 반환하지 않는다).
	@GuardedBy("m_lock") private boolean m_startCompleted = false;
	// performNow()로 즉시 수행이 요청되었는지 여부. 대기에서 깨어난 이유를 구분하는 데 쓴다.
	@GuardedBy("m_lock") private boolean m_runNowRequested = false;
	// stop()이 작업 중단(InterruptableWork.interrupt() 또는 쓰레드 인터럽트)을 이미 요청했는지 여부.
	@GuardedBy("m_lock") private boolean m_interruptRequested = false;
	private volatile long m_interval;
	private volatile Throwable m_failureCause;
	// onStarted()가 실패했는지 여부. start()가 시작 실패를 첫 perform() 실패와 구분하는 데 쓴다.
	private volatile boolean m_startFailed = false;
	@GuardedBy("m_lock") private Thread m_performThread;
	private volatile Logger m_logger = s_logger;

	/**
	 * 고정 rate 반복 스케줄을 생성한다.
	 * <p>
	 * 매 {@link RecurringWork#perform()}의 실제 시작 시각부터 {@code interval}이 지나면 다음 수행을 시작한다.
	 * 수행 시간이 주기보다 길면 끝나자마자 다음 수행을 시작하며, 처음 시작 시각을 기준으로 밀린 수행을 몰아서
	 * 따라잡지는 않는다.
	 *
	 * @param threadName	작업 쓰레드 이름. {@code null}이면 기본 이름을 쓴다.
	 * @param work			반복 수행할 작업 (non-null)
	 * @param initialDelay	첫 수행 전 대기 시간 (단위: milli-second, 0 이상)
	 * @param interval		반복 주기 (단위: milli-second, 0보다 커야 한다)
	 * @return	시작되지 않은 스케줄
	 * @throws IllegalArgumentException	{@code work}가 {@code null}이거나 시간 값이 범위를 벗어난 경우
	 */
	public static RecurringScheduleThread newFixedRateSchedule(String threadName, RecurringWork work,
															long initialDelay, long interval) {
		return new RecurringScheduleThread(threadName, work, true, initialDelay, interval);
	}

	/**
	 * 첫 수행 전 대기 없이 고정 rate 반복 스케줄을 생성한다.
	 *
	 * @see #newFixedRateSchedule(String, RecurringWork, long, long)
	 */
	public static RecurringScheduleThread newFixedRateSchedule(String threadName, RecurringWork work, long interval) {
		return new RecurringScheduleThread(threadName, work, true, 0, interval);
	}

	/**
	 * 고정 delay 반복 스케줄을 생성한다.
	 * <p>
	 * 매 {@link RecurringWork#perform()}이 끝난 시각부터 {@code interval}이 지나면 다음 수행을 시작한다.
	 *
	 * @param threadName	작업 쓰레드 이름. {@code null}이면 기본 이름을 쓴다.
	 * @param work			반복 수행할 작업 (non-null)
	 * @param initialDelay	첫 수행 전 대기 시간 (단위: milli-second, 0 이상)
	 * @param interval		반복 주기 (단위: milli-second, 0보다 커야 한다)
	 * @return	시작되지 않은 스케줄
	 * @throws IllegalArgumentException	{@code work}가 {@code null}이거나 시간 값이 범위를 벗어난 경우
	 */
	public static RecurringScheduleThread newFixedDelaySchedule(String threadName, RecurringWork work,
															long initialDelay, long interval) {
		return new RecurringScheduleThread(threadName, work, false, initialDelay, interval);
	}

	/**
	 * 첫 수행 전 대기 없이 고정 delay 반복 스케줄을 생성한다.
	 *
	 * @see #newFixedDelaySchedule(String, RecurringWork, long, long)
	 */
	public static RecurringScheduleThread newFixedDelaySchedule(String threadName, RecurringWork work, long interval) {
		return new RecurringScheduleThread(threadName, work, false, 0, interval);
	}

	private RecurringScheduleThread(String threadName, RecurringWork work, boolean byRate,
									long initialDelay, long interval) {
		Preconditions.checkNotNullArgument(work, "work is null");
		Preconditions.checkArgument(initialDelay >= 0, "negative initialDelay: %s", initialDelay);
		checkInterval(interval);

		m_threadName = threadName;
		m_work = work;
		m_byRate = byRate;
		m_initialDelay = initialDelay;
		m_interval = interval;
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}

	@Override
	public State getState() {
		m_lock.lock();
		try {
			return STATE_MAPPING[m_state];
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public Throwable getFailureCause() {
		return m_failureCause;
	}

	@Override
	public long getInterval() {
		return m_interval;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 다음 대기부터 적용되며, 이미 시작한 대기 시간은 바꾸지 않는다.
	 *
	 * @throws IllegalArgumentException	{@code millis}가 0 이하인 경우
	 */
	@Override
	public void setInterval(long millis) {
		checkInterval(millis);
		m_interval = millis;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 다음 수행을 기다리는 중({@link State#IDLE})일 때만 요청을 받는다. {@code perform()} 수행 중이거나 시작 전·후에는
	 * 요청을 버리고 {@code false}를 반환한다.
	 */
	@Override
	public boolean performNow() {
		m_lock.lock();
		try {
			if ( m_state == STATE_RUNNING_IDLE ) {
				m_runNowRequested = true;
				m_cond.signalAll();
				return true;
			}
			else {
				return false;
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void start() throws ExecutionException, IllegalStateException, InterruptedException {
		m_lock.lock();
		try {
			if ( m_state != STATE_NOT_STARTED ) {
				throw new IllegalStateException("not stopped state");
			}

			m_state = STATE_STARTING;

			if ( m_threadName != null ) {
				m_performThread = new Thread(m_wrapper, m_threadName);
			}
			else {
				m_performThread = new Thread(m_wrapper);
			}
			m_performThread.start();
			while ( !m_startCompleted ) {
				m_cond.await();
			}

			// onStarted()가 실패하면 작업 스레드가 곧바로 STOPPED로 바꾼다. 인터페이스 계약대로 예외로 알린다
			// (2026-10-31 이전에는 정상 반환해 호출자가 getFailureCause()를 따로 확인해야 했다).
			// m_failureCause만 보면, 첫 perform()이 곧바로 실패해 start()가 깨기 전에 STOPPED가 된 경우를 시작 실패로
			// 잘못 볼 수 있다. onStarted() 실패는 m_startFailed로 따로 구분한다.
			if ( m_startFailed ) {
				throw new ExecutionException(m_failureCause);
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 이 메소드는 기다리지 않는다. {@link RecurringWork#onStarted(RecurringSchedule)} 수행 중에 호출되면 중지를 요청해
	 * 두고 반환하며, 초기화가 끝나면 {@code perform()}을 수행하지 않고 종료한다(초기화가 성공했으면
	 * {@link RecurringWork#onStopped()}를 호출한다). 작업이 {@link InterruptableWork}이면 {@code mayInterruptIfRunning}과
	 * 관계없이 {@link InterruptableWork#interrupt()}를 호출한다. 작업 중단 요청은 lock 밖에서 한 번만 하며, 작업 쓰레드
	 * 자신이 호출한 경우({@code onStarted()}·{@code perform()} 안에서의 자기 중지)에는 하지 않는다.
	 */
	@Override
	public void stop(boolean mayInterruptIfRunning) {
		boolean interruptWork = false;
		Thread threadToInterrupt = null;

		m_lock.lock();
		try {
			// 이미 종료된 경우 바로 반환한다.
			if ( m_state == STATE_STOPPED ) {
				return;
			}
			// 시작 전이면 작업 스레드가 없으므로 바로 종료 상태로 만든다(onStarted()를 부르지 않았으므로 onStopped()도
			// 부르지 않는다). 2026-10-31 이전에는 STOPPING에 영원히 머물거나 m_performThread가 null이라 NPE가 났다.
			if ( m_state == STATE_NOT_STARTED ) {
				_setState(STATE_STOPPED);
				return;
			}

			// STARTING 상태에서도 기다리지 않고 중지를 표시만 한다. 초기화가 끝나면 작업 쓰레드가 이 표시를 보고 종료한다.
			// 2026-10-31 이전에는 onStarted()가 끝날 때까지 기다려, onStarted() 안에서 stop()을 부르면 교착되었다.
			if ( m_state != STATE_STOPPING ) {
				_setState(STATE_STOPPING);
			}

			if ( !m_interruptRequested && Thread.currentThread() != m_performThread ) {
				if ( m_work instanceof InterruptableWork ) {
					interruptWork = true;
				}
				else if ( mayInterruptIfRunning ) {
					threadToInterrupt = m_performThread;
				}
				m_interruptRequested = interruptWork || threadToInterrupt != null;
			}
		}
		finally {
			m_lock.unlock();
		}

		// 작업 중단은 lock 밖에서 요청한다. InterruptableWork.interrupt()가 작업이 끝나기를 기다리는 동안 작업 쓰레드가
		// 이 lock을 기다리면 교착되기 때문이다(2026-10-31 이전에는 lock을 쥔 채 호출했다).
		if ( interruptWork ) {
			((InterruptableWork)m_work).interrupt();
		}
		else if ( threadToInterrupt != null ) {
			threadToInterrupt.interrupt();
		}
	}

	@Override
	public void waitForStopped() throws InterruptedException {
		m_lock.lock();
		try {
			if ( Thread.currentThread().equals(m_performThread) ) {
				throw new RuntimeException("may cause deadlock!: "
										+ "don't call waitForStopped while perform");
			}

			while ( m_state != STATE_STOPPED ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean waitForStopped(long timeout) throws InterruptedException {
		long dueNano = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);

		m_lock.lock();
		try {
			// 검사는 try 안에서 한다. 2026-10-31 이전에는 lock을 잡은 뒤 try 밖에서 던져 lock이 풀리지 않았고,
			// 작업 스레드가 lock을 쥔 채 남아 stop()·getState() 등이 모두 멈췄다.
			if ( Thread.currentThread().equals(m_performThread) ) {
				throw new RuntimeException("may cause deadlock!: "
										+ "don't call waitForStopped while perform");
			}

			while ( m_state != STATE_STOPPED ) {
				long remainsNano = dueNano - System.nanoTime();
				if ( remainsNano <= 0 ) {
					return false;
				}

				m_cond.await(remainsNano, TimeUnit.NANOSECONDS);
			}

			return true;
		}
		finally {
			m_lock.unlock();
		}
	}

	private static void checkInterval(long interval) {
		Preconditions.checkArgument(interval > 0, "interval should be positive: %s", interval);
	}

	private void _setState(int state) {
		m_lock.lock();
		try {
			m_state = state;
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}

	/**
	 * {@link RecurringWork#onStarted(RecurringSchedule)}가 성공한 뒤의 종료 처리.
	 */
	private void shutdown() {
		try {
			m_work.onStopped();
		}
		catch ( Throwable e ) {
			m_logger.warn("ignored exception: fails to call onStopped, cause="
							+ Throwables.unwrapThrowable(e));
		}

		_setState(STATE_STOPPED);
		if ( m_logger.isInfoEnabled() ) {
			String str = UnitUtils.toSecondString(getInterval());
			m_logger.info("stopped: {}[period={}]", getClass().getSimpleName(), str);
		}
	}

	class Worker implements Runnable {
		private LongVariableSmoother m_overhead = new LongVariableSmoother(7, 1.2, 0);

		public void run() {
			try {
				m_failureCause = null;
				m_work.onStarted(RecurringScheduleThread.this);
			}
			catch ( Throwable e ) {
				m_failureCause = Throwables.unwrapThrowable(e);
				m_startFailed = true;
			}

			m_lock.lock();
			try {
				m_startCompleted = true;
				if ( m_startFailed ) {
					// 초기화에 실패했으므로 onStopped()를 호출하지 않고 바로 종료한다.
					_setState(STATE_STOPPED);
					return;
				}
				if ( m_state == STATE_STOPPING ) {
					// 초기화 중에 중지가 요청되었다. perform()을 수행하지 않고 종료한다.
					m_cond.signalAll();
				}
				else {
					_setState(STATE_RUNNING_IDLE);
					if ( m_logger.isInfoEnabled() ) {
						String str = UnitUtils.toSecondString(getInterval());
						m_logger.info("started: {}[period={}]",
										getClass().getSimpleName(), str);
					}
				}
			}
			finally {
				m_lock.unlock();
			}

			// 다음 수행을 시작할 기한. 첫 수행은 initialDelay 뒤에 시작한다.
			long dueNano = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(m_initialDelay);
			// 고정 rate에서 지연 보정 전의 목표 시각과, 이번 대기를 지연 보정 표본으로 쓸지 여부.
			long targetNano = dueNano;
			boolean sampleOverhead = false;
			while ( true ) {
				boolean stopped = false;
				m_lock.lock();
				try {
					// 기한 도달, 즉시 수행 요청, 중지 요청 중 하나가 될 때까지 기다린다. 다른 이유로 깨어나면
					// 남은 시간을 다시 기다린다.
					boolean waited = false;
					while ( m_state == STATE_RUNNING_IDLE && !m_runNowRequested ) {
						long remainsNano = dueNano - System.nanoTime();
						if ( remainsNano <= 0 ) {
							break;
						}
						m_cond.awaitNanos(remainsNano);
						waited = true;
					}

					// IDLE 상태 중 스케줄이 중단되었는지 체크한다.
					if ( m_state == STATE_STOPPING ) {
						stopped = true;
					}
					else if ( m_state != STATE_RUNNING_IDLE ) {
						String msg = "internal error: invalid state=" + m_state
									+ ", should be RUNNING.IDLE";
						m_logger.error(msg);
						m_failureCause = new RuntimeException(msg);

						stopped = true;
					}
					else {
						if ( m_runNowRequested ) {
							// 즉시 수행 요청으로 깨어난 경우는 지연 보정 표본으로 쓰지 않는다.
							m_runNowRequested = false;
						}
						else if ( sampleOverhead && waited ) {
							// 2026-10-31 이전에는 깨어난 이유와 관계없이 표본을 기록해, performNow()로 일찍 깨우면 큰 음수가
							// 쌓이고 다음 대기가 그만큼 길어졌다.
							m_overhead.observe(System.nanoTime() - targetNano);
						}
						_setState(STATE_RUNNING_WORKING);
					}
				}
				catch ( InterruptedException e ) {
					if ( m_state != STATE_STOPPING ) {
						m_failureCause = e;
					}
					stopped = true;
				}
				finally {
					m_lock.unlock();
				}
				if ( stopped ) {
					shutdown();
					return;
				}

				long startedNano = System.nanoTime();
				try {
					m_work.perform();
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);

					if ( cause instanceof InterruptedException ) {
						if ( m_logger.isDebugEnabled() ) {
							m_logger.debug("interrupted: RecurringSchedule="
											+ RecurringScheduleThread.this);
						}
						shutdown();

						Thread.currentThread().interrupt();
					}
					else {
						m_failureCause = cause;
						m_logger.error("fails to perform job, stop schedule..., cause="
										+ cause);
						shutdown();
					}

					return;
				}

				m_lock.lock();
				try {
					// perform 수행 중 스케줄이 중단되었는지 체크한다.
					if ( m_state == STATE_STOPPING ) {
						stopped = true;
					}
					else {
						_setState(STATE_RUNNING_IDLE);

						// 넘침 없이 변환한다(TimeUnit은 넘치면 Long.MAX_VALUE로 포화한다). 시각 비교는 모두 차이로 하므로
						// 기한 계산이 넘쳐도 결과는 맞다.
						long intervalNanos = TimeUnit.MILLISECONDS.toNanos(m_interval);
						long nowNano = System.nanoTime();
						if ( m_byRate ) {
							// 고정 rate 반복 작업인 경우는 perform 시작 시각에서 interval이 지난 시각까지 대기한다.
							// 실제 대기는 평균 지연만큼 일찍 끝내도록 한다.
							long elapsedNanos = nowNano - startedNano;
							targetNano = startedNano + intervalNanos;
							if ( targetNano - nowNano <= 0 ) {
								if ( m_logger.isDebugEnabled() ) {
									m_logger.debug("late schedule: expected=" + m_interval
													+ "ms, elapsed=" + elapsedNanos/1000000 + "ms");
								}
								dueNano = nowNano;
								sampleOverhead = false;
							}
							else {
								if ( m_logger.isDebugEnabled() ) {
									m_logger.debug("wait: " + (targetNano - nowNano)/1000000 + "ms, elapsed="
											+ elapsedNanos/1000000 + "ms");
								}
								dueNano = targetNano - m_overhead.getSmoothed();
								sampleOverhead = true;
							}
						}
						else {
							dueNano = nowNano + intervalNanos;
							sampleOverhead = false;
						}
					}
				}
				finally {
					m_lock.unlock();
				}
				if ( stopped ) {
					shutdown();
					return;
				}
			}
		}
	}
}
