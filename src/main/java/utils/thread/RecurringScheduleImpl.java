package utils.thread;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Throwables;
import utils.UnitUtils;


/**
 *
 * @author Kang-Woo Lee
 */
public class RecurringScheduleImpl implements RecurringSchedule, LoggerSettable {
	static final Logger s_logger = LoggerFactory.getLogger(RecurringScheduleImpl.class);

	private final RecurringWork m_worker;
	private final long m_initialDelay;
	private final boolean m_byRate;
	
	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();

	private final Task m_wrapper = new Task();
	@GuardedBy("m_lock") private State m_state = State.STOPPED;
	@GuardedBy("m_lock") private Future<?> m_schedule;
	@GuardedBy("m_lock") private long m_interval;
	private final CamusExecutor m_executor;
	private volatile Throwable m_failureCause;
	private volatile Thread m_performThread;
	private volatile Logger m_logger = s_logger;

	RecurringScheduleImpl(RecurringWork work, boolean byRate, long initialDelay,
							long interval, CamusExecutor executor) {
		m_worker = work;
		m_byRate = byRate;
		m_initialDelay = initialDelay;
		m_interval = interval;
		m_executor = executor;
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	@Override
	public State getState() {
		return m_state;
	}

	@Override
	public Throwable getFailureCause() {
		return m_failureCause;
	}

	@Override
	public long getInterval() {
		m_lock.lock();
		try {
			return m_interval;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void setInterval(long millis) {
		m_lock.lock();
		try {
			m_interval = millis;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void performNow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void start() throws ExecutionException, IllegalStateException {
		m_lock.lock();
		try {
			if ( m_state != State.STOPPED ) {
				throw new IllegalStateException("not stopped state");
			}
			m_state = State.STARTING;
		}
		finally {
			m_lock.unlock();
		}

		try {
			m_worker.onStarted(this);
		}
		catch ( Throwable e ) {
			setState(State.STOPPED);

			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}

		m_lock.lock();
		try {
			setState(State.IDLE);
			try {
				m_schedule = m_executor.schedule(m_wrapper, m_initialDelay, TimeUnit.MILLISECONDS);
			}
			catch ( RuntimeException e ) {
				setState(State.STOPPING);

				try {
					m_worker.onStopped();
				}
				catch ( Throwable ignored ) { }

				setState(State.STOPPED);

				throw new ExecutionException(Throwables.unwrapThrowable(e));
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
			// start가 완료되기 전에 stop()이 호출되는 경우를 처리
			while ( m_state == State.STARTING ) {
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					return;
				}
			}

			// 이미 종료된 경우 처리
			if ( m_state == State.STOPPED ) {
				return;
			}

			setState(State.STOPPING);
			if ( m_worker instanceof InterruptableWork ) {
				((InterruptableWork)m_worker).interrupt();
			}
			if ( m_schedule.cancel(mayInterruptIfRunning) ) {
				m_executor.execute(m_wrapper);
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
	
			while ( m_state != State.STOPPED ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean waitForStopped(long timeout) throws InterruptedException {
		m_lock.lock();
		try {
			if ( Thread.currentThread().equals(m_performThread) ) {
				throw new RuntimeException("may cause deadlock!: "
										+ "don't call waitForStopped while perform");
			}
	
			long dueNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
			while ( m_state != State.STOPPED ) {
				long remainsNanos = dueNanos - System.nanoTime();
				if ( remainsNanos <= 0 ) {
					return false;
				}
	
				m_cond.awaitNanos(dueNanos);
			}
			
			return true;
		}
		finally {
			m_lock.unlock();
		}
	}

	private void setState(State state) {
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
		try {
			m_worker.onStopped();
		}
		catch ( Throwable e ) {
			m_logger.warn("ignored exception: fails to call onStopped, cause="
							+ Throwables.unwrapThrowable(e));
		}
		setState(State.STOPPED);

		if ( m_logger.isInfoEnabled() ) {
			String str = UnitUtils.toSecondString(getInterval());
			m_logger.info("stopped: {}[period={}]", getClass().getSimpleName(), str);
		}
	}

	class Task implements Runnable {
		public void run() {
			m_lock.lock();
			try {
				// IDLE 상태 중 스케줄이 중단되었는지 체크한다.
				if ( m_state == State.STOPPING ) {
					shutdown();
					return;
				}
				else if ( m_state != State.IDLE ) {
					return;
				}

				setState(State.WORKING);
			}
			finally {
				m_lock.unlock();
			}

			long startedNanos = System.nanoTime();
			try {
				m_performThread = Thread.currentThread();
				m_worker.perform();
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);

				if ( cause instanceof InterruptedException ) {
					if ( m_logger.isDebugEnabled() ) {
						m_logger.debug("interrupted: RecurringSchedule=" + RecurringScheduleImpl.this);
					}
					shutdown();

					Thread.currentThread().interrupt();
				}
				else {
					m_failureCause = e;
					m_logger.error("fails to perform job, stop schedule..., cause="
									+ Throwables.unwrapThrowable(e));
					shutdown();
				}

				return;
			}
			finally {
				m_performThread = null;
			}

			m_lock.lock();
			try {
				// perform 수행 중 스케줄이 중단되었는지 체크한다.
				if ( m_state == State.STOPPING ) {
					shutdown();
					return;
				}
				setState(State.IDLE);

				long delayNanos = TimeUnit.MILLISECONDS.toNanos(m_interval);
				if ( m_byRate ) {
					long elapsedNanos = System.nanoTime() - startedNanos;

					delayNanos = delayNanos - elapsedNanos;
					if ( delayNanos < 0 ) {
						delayNanos = 0;
						if ( m_logger.isDebugEnabled() ) {
							m_logger.debug("late schedule: expected=" + m_interval
											+ ", elapsed=" + elapsedNanos/1000000);
						}
					}
					else if ( m_logger.isDebugEnabled() ) {
						m_logger.debug("schedule: delay=" + delayNanos/1000000 + ", elapsed="
										+ elapsedNanos/1000000);
					}
				}

				m_schedule = m_executor.schedule(m_wrapper, delayNanos, TimeUnit.NANOSECONDS);
			}
			finally {
				m_lock.unlock();
			}
		}
	}
}
