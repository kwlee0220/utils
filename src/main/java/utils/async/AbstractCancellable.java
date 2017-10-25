package utils.async;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.vavr.CheckedRunnable;
import io.vavr.control.Option;
import utils.Throwables;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractCancellable<T> implements Cancellable<T> {
	protected enum State { IDLE, RUNNING, COMPLETED, FAILED, CANCELLING, CANCELLED };
	
	protected final ReentrantLock m_cancellableLock = new ReentrantLock();
	protected final Condition m_cancellableCond = m_cancellableLock.newCondition();
	protected State m_cancellableState = State.IDLE;
	private Throwable m_cause;
	private volatile Option<CheckedRunnable> m_startAction = Option.none();
	private volatile Option<Runnable> m_completeAction = Option.none();
	private volatile Option<Runnable> m_cancelAction = Option.none();
	private volatile Option<Consumer<Throwable>> m_failureAction = Option.none();
	
	/**
	 * 본 작업이 시작될 때까지 대기한다.
	 * 
	 * @return	작업이 시작된 경우는 {@code true}, 그렇지 않고 대기가 종료된 경우.
	 */
	public boolean waitForStarted() {
		m_cancellableLock.lock();
		try {
			while ( m_cancellableState == State.IDLE ) {
				m_cancellableCond.await();
			}
			
			return true;
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_cancellableLock.unlock();
		}
	}

	/**
	 * 본 작업이 종료될 때까지 대기한다.
	 * 
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public void waitForDone() throws InterruptedException {
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
						return;
				}
			}
		}
		finally {
			m_cancellableLock.unlock();
		}
	}

	/**
	 * 본 작업이 종료될 때까지 제한된 시간 동안만 대기한다.
	 * 
	 * @param timeout	대기시간
	 * @param unit		대기시간 단위
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 * @throws TimeoutException	작업 종료 대기 중 시간제한이 걸린 경우.
	 */
	public void waitForDone(long timeout, TimeUnit unit) throws InterruptedException,
																TimeoutException {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				switch ( m_cancellableState ) {
					case IDLE:
					case RUNNING:
					case CANCELLING:
						if ( !m_cancellableCond.await(timeout, unit) ) {
							throw new TimeoutException("" + timeout + unit);
						}
					default:
						return;
				}
			}
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	/**
	 * 본 작업이 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Result#isSuccess()}가 {@code true},
	 * 			오류가 발생되어 종료된 경우는 {@link Result#isFailure()}가 {@code true},
	 * 			또는 작업이 취소되어 종료된 경우는 {@link Result#isEmpty()}가
	 * 			{@code true}가 됨.
	 */
	public Result<T> getResult() {
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
		catch ( InterruptedException e ) {
			return Result.none();
		}
		finally {
			m_cancellableLock.unlock();
		}
	}
	
	/**
	 * 본 작업이 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @param timeout	제한시간
	 * @param tu		제한시간 단위
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Result#isSuccess()}가 {@code true},
	 * 			오류가 발생되어 종료된 경우는 {@link Result#isFailure()}가 {@code true},
	 * 			또는 작업이 취소되어 종료된 경우거나 시간제한으로 반환되는 경우
	 * 			{@link Result#isEmpty()}가 {@code true}가 됨.
	 */
	public Result<Void> getResult(long timeout, TimeUnit tu) {
		m_cancellableLock.lock();
		try {
			while ( true ) {
				if ( m_cancellableState == State.COMPLETED
					|| m_cancellableState == State.FAILED
					|| m_cancellableState == State.CANCELLED ) {
					break;
				}
				if ( !m_cancellableCond.await(timeout, tu) ) {
					return Result.none();
				}
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
		catch ( InterruptedException e ) {
			return Result.none();
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
			if ( m_startAction.isDefined() ) {
				m_startAction.get().run();
			}
			if ( action != null ) {
				action.run();
			}
			m_cancellableCond.signalAll();
		}
		catch ( Throwable e ) {
			if ( markFailed(e) ) {
				throw Throwables.toRuntimeException(e);
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
					m_completeAction.forEach(Runnable::run);
					m_cancellableCond.signalAll();
					break;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					m_cancelAction.forEach(Runnable::run);
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
				case FAILED:
					return false;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					m_cancelAction.forEach(Runnable::run);
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
					m_failureAction.forEach(a -> a.accept(m_cause));
					m_cancellableCond.signalAll();
					return true;
				case CANCELLING:
					m_cancellableState = State.CANCELLED;
					m_cancelAction.forEach(Runnable::run);
					m_cancellableCond.signalAll();
				case CANCELLED:
					return false;
				case FAILED:
					return true;
				default:
					throw new IllegalStateException("unexpected state: " + m_cancellableState);
			}
		}
		finally { m_cancellableLock.unlock(); }
	}
	
	protected void setStartAction(CheckedRunnable action) {
		m_startAction = Option.of(action);
	}
	
	protected void setCompleteAction(Runnable action) {
		m_completeAction = Option.of(action);
	}
	
	protected void setCancelAction(Runnable action) {
		m_cancelAction = Option.of(action);
	}
	
	protected void setFailureAction(Consumer<Throwable> action) {
		m_failureAction = Option.of(action);
	}
}
