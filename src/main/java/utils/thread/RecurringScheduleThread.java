package utils.thread;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;
import utils.LongVariableSmoother;
import utils.Utilities;


/**
 *
 * @author Kang-Woo Lee
 */
public class RecurringScheduleThread implements RecurringSchedule {
	static final Logger s_logger = Logger.getLogger("ETRI.THREAD.SCHEDULE");
	
	private static final int STATE_NOT_STARTED = 0;
	private static final int STATE_STARTING = 1;
	private static final int STATE_RUNNING_IDLE = 2;
	private static final int STATE_RUNNING_WORKING = 3;
	private static final int STATE_STOPPING = 4;
	private static final int STATE_STOPPED = 5;
	
	private static final State[] STATE_MAPPING = new State[] {
		State.STARTING, State.STARTING, State.IDLE, State.IDLE, State.STOPPING, State.STOPPED,
	};

	private final String m_threadName;
	private final RecurringWork m_work;
	private final long m_initialDelay;
	private final boolean m_byRate;

	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	
	private final Worker m_wrapper = new Worker();
	@GuardedBy("m_lock") private int m_state = STATE_NOT_STARTED;
	private volatile long m_interval;
	private volatile Throwable m_failureCause;
	@GuardedBy("m_lock") private Thread m_performThread;
	private volatile Logger m_logger = s_logger;
	
	public static RecurringScheduleThread newFixedRateSchedule(String threadName, RecurringWork work,
															long initialDelay, long interval) {
		return new RecurringScheduleThread(threadName, work, true, initialDelay, interval);
	}
	
	public static RecurringScheduleThread newFixedRateSchedule(String threadName, RecurringWork work, long interval) {
		return new RecurringScheduleThread(threadName, work, true, 0, interval);
	}
	
	public static RecurringScheduleThread newFixedDelaySchedule(String threadName, RecurringWork work,
															long initialDelay, long interval) {
		return new RecurringScheduleThread(threadName, work, false, initialDelay, interval);
	}
	
	public static RecurringScheduleThread newFixedDelaySchedule(String threadName, RecurringWork work, long interval) {
		return new RecurringScheduleThread(threadName, work, false, 0, interval);
	}

	private RecurringScheduleThread(String threadName, RecurringWork work, boolean byRate,
									long initialDelay, long interval) {
		m_threadName = threadName;
		m_work = work;
		m_byRate = byRate;
		m_initialDelay = initialDelay;
		m_interval = interval;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
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

	@Override
	public void setInterval(long millis) {
		m_interval = millis;
	}
	
	public void performNow() {
		m_lock.lock();
		try {
			if ( m_state == STATE_RUNNING_IDLE ) {
				m_cond.signalAll();
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
			while ( m_state == STATE_STARTING ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void stop(boolean mayInterruptIfRunning) {
		m_lock.lock();
		try {
			// start가 호출되어 RecurringWork.onStarted()가 수행 중인 경우는
			// 해당 메소드가 종료될 때까지 대기한다.
			while ( m_state == STATE_STARTING ) {
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					return;
				}
			}

			// 이미 종료된 경우 바로 반환한다.
			if ( m_state == STATE_STOPPED ) {
				return;
			}

			_setState(STATE_STOPPING);
			if ( m_work instanceof InterruptableWork ) {
				((InterruptableWork)m_work).interrupt();
			}
			else if ( mayInterruptIfRunning ) {
				m_performThread.interrupt();
			}
		}
		finally {
			m_lock.unlock();
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
		long dueNano = System.nanoTime() + timeout * 1000000;
		
		m_lock.lock();
		if ( Thread.currentThread().equals(m_performThread) ) {
			throw new RuntimeException("may cause deadlock!: "
									+ "don't call waitForStopped while perform");
		}

		try {
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

	private void shutdown() {
		m_lock.lock();
		try {
			if ( m_state == STATE_STARTING ) {
				// STARTING 상태에서 m_work.onStarted() 메소드 호출 중 오류로 shutdown되는 경우
				m_state = STATE_STOPPED;
				m_cond.signalAll();
				
				return;
			}
		}
		finally {
			m_lock.unlock();
		}
		
		try {
			m_work.onStopped();
		}
		catch ( Throwable e ) {
			m_logger.warn("ignored exception: fails to call onStopped, cause="
							+ ExceptionUtils.unwrapThrowable(e));
		}
		
		_setState(STATE_STOPPED);
		if ( m_logger.isInfoEnabled() ) {
			m_logger.info("stopped: " + getClass().getSimpleName()
							+ "[period=" + getInterval() + "ms]");
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
				m_failureCause = ExceptionUtils.unwrapThrowable(e);
			}
			
			boolean stopped = false;
			m_lock.lock();
			try {
				if ( m_failureCause == null ) {
					_setState(STATE_RUNNING_IDLE);
					if ( m_logger.isInfoEnabled() ) {
						m_logger.info("started: " + getClass().getSimpleName()
										+ "[period=" + getInterval() + "ms]");
					}
					
					if ( m_initialDelay > 0 ) {
						try {
							m_cond.await(m_initialDelay, TimeUnit.MILLISECONDS);
						}
						catch ( InterruptedException e ) {
							m_failureCause = e;
							stopped = true;
						}
					}
				}
				else {
					stopped = true;
				}
			}
			finally {
				m_lock.unlock();
			}
			
			if ( stopped ) {
				shutdown();
				return;
			}
			
			while ( true ) {
				long intervalNanos = m_interval * 1000000;
				long startedNano = System.nanoTime();
				
				stopped = false;
				m_lock.lock();
				try {
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
						_setState(STATE_RUNNING_WORKING);
					}
				}
				finally {
					m_lock.unlock();
				}
				if ( stopped ) {
					shutdown();
					return;
				}
	
				try {
					m_work.perform();
				}
				catch ( Throwable e ) {
					Throwable cause = ExceptionUtils.unwrapThrowable(e);
	
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

				stopped = false;
				m_lock.lock();
				try {
					// perform 수행 중 스케줄이 중단되었는지 체크한다.
					if ( m_state == STATE_STOPPING ) {
						stopped = true;
					}
					else {
						_setState(STATE_RUNNING_IDLE);
		
						long sleepNanos = intervalNanos;
						if ( m_byRate ) {
							// 고정 rate 반복 작업인 경우는 interval 기간에서 perform 수행 시간을
							// 제외한 시간이 0보다 큰 경우는 해당 시간만큼 sleep하도록 한다.
							//
							long elapsedNanos = System.nanoTime() - startedNano;
		
							sleepNanos = intervalNanos - elapsedNanos;
							if ( sleepNanos <= 0 ) {
								if ( m_logger.isDebugEnabled() ) {
									m_logger.debug("late schedule: expected=" + m_interval
													+ "ms, elapsed=" + elapsedNanos/1000000 + "ms");
								}
							}
							else {
								if ( m_logger.isDebugEnabled() ) {
									m_logger.debug("wait: " + sleepNanos/1000000 + "ms, elapsed="
											+ elapsedNanos/1000000 + "ms");
								}
								
								sleepNanos -= m_overhead.getSmoothed();
							}
						}
		
						if ( sleepNanos > 0 ) {
							try {
								m_cond.await(sleepNanos, TimeUnit.NANOSECONDS);
								
								long overhead = System.nanoTime() - startedNano - intervalNanos;
								m_overhead.observe(overhead);
							}
							catch ( InterruptedException e ) {
								stopped = true;
							}
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
