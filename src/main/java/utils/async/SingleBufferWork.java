package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.GuardedBy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SingleBufferWork<T,R> {
	private static final Logger s_logger = LoggerFactory.getLogger(SingleBufferWork.class);
	public enum State { PENDING, IGNORED, SELECTED, COMPLETED, FAILED };

	private final T m_job;
	
	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private State m_state;
	@GuardedBy("m_lock") private Result<R> m_result;
	
	public SingleBufferWork(T job) {
		m_job = job;
		m_result = null;
		m_state = State.PENDING;
	}
	
	public T getJob() {
		return m_job;
	}
	
	public Result<R> waitForDone() throws InterruptedException {
		m_lock.lock();
		try {
			while ( true ) {
				switch ( m_state ) {
					case PENDING:
					case SELECTED:
						m_cond.await();
						break;
					case IGNORED:
					case COMPLETED:
					case FAILED:
						return m_result;
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public R get() throws InterruptedException, ExecutionException {
		m_lock.lock();
		try {
			while ( true ) {
				switch ( m_state ) {
					case PENDING:
					case SELECTED:
						m_cond.await();
						break;
					case IGNORED:
						throw new CancellationException("ignored");
					case COMPLETED:
						return m_result.get();
					case FAILED:
						throw new ExecutionException(m_result.getCause());
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	void notifySelected() {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case PENDING:
					m_state = State.SELECTED;
					m_cond.signalAll();
					break;
				default:
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	void notifyIgnored() {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case PENDING:
					m_state = State.IGNORED;
					m_cond.signalAll();
					
					s_logger.info("job ignored: {}", m_job);
					break;
				default:
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	void notifyCompleted(R result) {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case SELECTED:
					m_result = Result.completed(result);
					m_state = State.COMPLETED;
					m_cond.signalAll();

					s_logger.info("job completed: {}, result={}", m_job, m_result);
					break;
				default:
					throw new IllegalStateException("invalid state: current=" + m_state);
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	void notifyFailed(Throwable cause) {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case SELECTED:
					m_result = Result.failed(cause);
					m_state = State.FAILED;
					m_cond.signalAll();
					
					s_logger.info("job failed: {}, cause={}", m_job, cause);
					break;
				default:
					throw new IllegalStateException("invalid state: current=" + m_state);
			}
		}
		finally {
			m_lock.unlock();
		}
	}
}
