package utils.async;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.vavr.CheckedRunnable;
import utils.ExceptionUtils;
import utils.Unchecked;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractCancellable implements Cancellable {
	protected enum State { IDLE, RUNNING, COMPLETED, FAILED, CANCELLING, CANCELLED };
	
	protected final ReentrantLock m_cancellableLock = new ReentrantLock();
	protected final Condition m_cancellableCond = m_cancellableLock.newCondition();
	protected State m_cancellableState = State.IDLE;
	private Throwable m_cause;
	private volatile CheckedRunnable m_startAction;
	private volatile Runnable m_completeAction;
	private volatile Runnable m_cancelAction;
	private volatile Consumer<Throwable> m_failureAction;
	
	public void waitForStarted() throws InterruptedException {
		m_cancellableLock.lock();
		try {
			while ( m_cancellableState == State.IDLE ) {
				m_cancellableCond.await();
			}
		}
		finally { m_cancellableLock.unlock(); }
	}
	
	public boolean waitForFinished() {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				switch ( m_cancellableState ) {
					case IDLE:
					case RUNNING:
					case CANCELLING:
						m_cancellableCond.await();
						break;
					default:
						return true;
				}
			}
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	public boolean waitForFinished(long timeout, TimeUnit tu) {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				switch ( m_cancellableState ) {
					case IDLE:
					case RUNNING:
					case CANCELLING:
						return m_cancellableCond.await(timeout, tu);
					default:
						return true;
				}
			}
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	public Result<Void> getResult() throws InterruptedException {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				if ( m_cancellableState == State.COMPLETED
					|| m_cancellableState == State.FAILED
					|| m_cancellableState == State.CANCELLED ) {
					break;
				}
				m_cancellableCond.await();
			}
			
			switch ( m_cancellableState ) {
				case COMPLETED:
					return Result.some(null);
				case FAILED:
					return Result.failure(m_cause);
				case CANCELLED:
					return Result.none();
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	public Result<Void> getResult(long timeout, TimeUnit tu)
		throws InterruptedException, TimeoutException {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				if ( m_cancellableState == State.COMPLETED
					|| m_cancellableState == State.FAILED
					|| m_cancellableState == State.CANCELLED ) {
					break;
				}
				m_cancellableCond.await(timeout, tu);
			}
			
			switch ( m_cancellableState ) {
				case COMPLETED:
					return Result.some(null);
				case FAILED:
					return Result.failure(m_cause);
				case CANCELLED:
					return Result.none();
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_cancellableLock.unlock();
		}
	}

	@Override
	public final boolean isDone() {
		m_cancellableLock.lock();
		try {
			return m_cancellableState == State.COMPLETED || m_cancellableState == State.FAILED
					|| m_cancellableState == State.CANCELLED;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}

	@Override
	public final boolean isCompleted() {
		m_cancellableLock.lock();
		try {
			return m_cancellableState == State.COMPLETED;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	@Override
	public final boolean isCancelled() {
		m_cancellableLock.lock();
		try {
			return m_cancellableState == State.CANCELLED;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	public final boolean isFailed() {
		m_cancellableLock.lock();
		try {
			return m_cancellableState == State.FAILED;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	@Override
	public boolean cancel() {
		m_cancellableLock.lock();
		try {
			while ( m_cancellableState == State.IDLE ) {
				m_cancellableCond.await();
			}
			
			switch ( m_cancellableState ) {
				case RUNNING:
					m_cancellableState = State.CANCELLING;
					m_cancellableCond.signalAll();
				case CANCELLING:
					while ( m_cancellableState == State.CANCELLING ) {
						m_cancellableCond.await();
					}
					return m_cancellableState == State.CANCELLED;
				case CANCELLED:
					return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					throw new AssertionError("unexpected state: state=" + m_cancellableState);
			}
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_cancellableState);
	}
	
	protected final ReentrantLock getLock() {
		return m_cancellableLock;
	}
	
	protected void begin(CheckedRunnable action) {
		m_cancellableLock.lock();
		try {
			if ( m_cancellableState != State.IDLE ) {
				throw new IllegalStateException("already started: " + m_cancellableState);
			}
			
			m_cancellableState = State.RUNNING;
			if ( m_startAction != null ) {
				m_startAction.run();
			}
			if ( action != null ) {
				action.run();
			}
			m_cancellableCond.signalAll();
		}
		catch ( Throwable e ) {
			if ( markFailed(e) ) {
				throw ExceptionUtils.toRuntimeException(e);
			}
			else {
				return;
			}
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	protected void begin() {
		begin(null);
	}
	
	protected void complete() throws IllegalStateException {
		m_cancellableLock.lock();
		try {
			switch ( m_cancellableState ) {
				case RUNNING:
					m_cancellableState = State.COMPLETED;
					if ( m_completeAction != null ) {
						Unchecked.runIE(()->m_completeAction.run());
					}
					m_cancellableCond.signalAll();
					break;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					if ( m_cancelAction != null ) {
						Unchecked.runIE(()->m_cancelAction.run());
					}
					m_cancellableCond.signalAll();
				case CANCELLED:
					break;
				default:
					throw new IllegalStateException("unexpected state: " + m_cancellableState);
			}
			
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	protected boolean checkCancelled() {
		m_cancellableLock.lock();
		try {
			switch ( m_cancellableState ) {
				case RUNNING:
					return false;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					if ( m_cancelAction != null ) {
						Unchecked.runIE(()->m_cancelAction.run());
					}
					m_cancellableCond.signalAll();
				case CANCELLED:
					return true;
				default:
					throw new IllegalStateException("unexpected state: " + m_cancellableState);
			}
		}
		finally { m_cancellableLock.unlock(); }
	}
	
	protected boolean markFailed(Throwable cause) {
		m_cancellableLock.lock();
		try {
			switch ( m_cancellableState ) {
				case IDLE:
				case RUNNING:
					m_cancellableState = State.FAILED;
					m_cause = cause;
					if ( m_failureAction != null ) {
						Unchecked.runIE(()->m_failureAction.accept(m_cause));
					}
					m_cancellableCond.signalAll();
					return true;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					if ( m_cancelAction != null ) {
						Unchecked.runIE(()->m_cancelAction.run());
					}
					m_cancellableCond.signalAll();
				case CANCELLED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_cancellableState);
			}
		}
		finally { m_cancellableLock.unlock(); }
	}
	
	protected void setStartAction(CheckedRunnable action) {
		m_startAction = action;
	}
	
	protected void setCompleteAction(Runnable action) {
		m_completeAction = action;
	}
	
	protected void setCancelAction(Runnable action) {
		m_cancelAction = action;
	}
	
	protected void setFailureAction(Consumer<Throwable> action) {
		m_failureAction = action;
	}
}
