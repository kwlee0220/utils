package utils.async;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.vavr.CheckedRunnable;
import utils.Lambdas;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncExecution<T> implements AsyncExecution<T> {
	protected enum State { NOT_STARTED, RUNNING, COMPLETED, FAILED, CANCELLING, CANCELLED };
	
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	protected final Condition m_aopCond = m_aopLock.newCondition();
	protected State m_aopState = State.NOT_STARTED;
	private Result<T> m_result;
	private final List<Consumer<AsyncExecution<T>>> m_startListeners = Lists.newArrayList();
	private final List<Consumer<AsyncExecution<T>>> m_listeners = Lists.newArrayList();
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( m_aopState == State.NOT_STARTED ) {
				m_aopCond.await();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		m_aopLock.lock();
		try {
			while ( m_aopState == State.NOT_STARTED ) {
				if ( !m_aopCond.awaitUntil(due) ) {
					return false;
				}
			}
			
			return true;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public void waitForDone() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( true ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
					case RUNNING:
					case CANCELLING:
						m_aopCond.await();
						break;
					default:
						return;
				}
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		
		m_aopLock.lock();
		try {
			while ( true ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
					case RUNNING:
					case CANCELLING:
						if ( !m_aopCond.awaitUntil(due) ) {
							return false;
						}
						break;
					default:
						return true;
				}
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public Result<T> getResult() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( true ) {
				if ( m_aopState == State.COMPLETED
					|| m_aopState == State.FAILED
					|| m_aopState == State.CANCELLED ) {
					break;
				}
				m_aopCond.await();
			}
			
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return m_result;
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public Result<T> getResult(long timeout, TimeUnit unit) throws InterruptedException,
																	TimeoutException {
		m_aopLock.lock();
		try {
			while ( true ) {
				if ( m_aopState == State.COMPLETED
					|| m_aopState == State.FAILED
					|| m_aopState == State.CANCELLED ) {
					break;
				}
				if ( !m_aopCond.await(timeout, unit) ) {
					throw new TimeoutException("" + timeout + unit);
				}
			}
			
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return m_result;
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isStarted() {
		m_aopLock.lock();
		try {
			return m_aopState != State.NOT_STARTED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isDone() {
		m_aopLock.lock();
		try {
			return m_aopState == State.COMPLETED || m_aopState == State.FAILED
					|| m_aopState == State.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isCompleted() {
		m_aopLock.lock();
		try {
			return m_aopState == State.COMPLETED;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public final boolean isCancelled() {
		m_aopLock.lock();
		try {
			return m_aopState == State.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	public final boolean isFailed() {
		m_aopLock.lock();
		try {
			return m_aopState == State.FAILED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		waitForDone();
		
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case COMPLETED:
					return m_result.get();
				case FAILED:
					throw new ExecutionException(m_result.getCause());
				case CANCELLED:
					throw new CancellationException();
				default:
					throw new IllegalStateException();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		if ( !waitForDone(timeout, unit) ) {
			throw new TimeoutException("time=" + timeout + unit);
		}
		
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case COMPLETED:
					return m_result.get();
				case FAILED:
					throw new ExecutionException(m_result.getCause());
				case CANCELLED:
					throw new CancellationException();
				default:
					throw new IllegalStateException();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return cancel();
	}

	public boolean onCompleted(T value) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.some(value);
					m_aopState = State.COMPLETED;
					m_aopCond.signalAll();
			    	notifyListeners(m_listeners);
			    	return true;
				case COMPLETED:
				case CANCELLED:
				case FAILED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
    public boolean onFailed(Throwable cause) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.failure(cause);
			    	m_aopState = State.FAILED;
			    	m_aopCond.signalAll();
			    	notifyListeners(m_listeners);
			    	
			    	return true;
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }
	
    public boolean onCancelled() {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
					return false;
				case RUNNING:
				case CANCELLING:
					m_result = Result.none();
					m_aopState = State.CANCELLED;
					m_aopCond.signalAll();
				case CANCELLED:
			    	notifyListeners(m_listeners);
					return true;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }

	@Override
	public void whenStarted(Consumer<AsyncExecution<T>> listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_startListeners.add(listener);
					break;
				default:
					CompletableFuture.runAsync(() -> listener.accept(this));
					break;
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}

	@Override
	public void whenDone(Consumer<AsyncExecution<T>> listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					CompletableFuture.runAsync(() -> listener.accept(this));
					break;
				default:
					m_listeners.add(listener);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}

	@Override
	public void close() throws Exception {
		cancel();
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
	}
	
	protected final ReentrantLock getLock() {
		return m_aopLock;
	}
	
	protected final void guarded(Runnable action) {
		Lambdas.guraded(m_aopLock, action);
	}
	
	protected boolean start(CheckedRunnable starter) {
		Preconditions.checkNotNull(starter, "starter is null");
		
		m_aopLock.lock();
		try {
			if ( m_aopState != State.NOT_STARTED ) {
				return false;
			}
			
			starter.run();

			m_aopState = State.RUNNING;
			m_aopCond.signalAll();
			notifyListeners(m_startListeners);
			
			return true;
		}
		catch ( Throwable e ) {
			onFailed(e);
			return false;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	protected boolean cancel(CheckedRunnable canceller) {
		Preconditions.checkNotNull(canceller, "canceller is null");
		
		m_aopLock.lock();
		try {
			while ( m_aopState == State.CANCELLING ) {
				m_aopCond.await();
			}
			
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = State.CANCELLED;
					m_aopCond.signalAll();
					return true;
				case CANCELLED:
				case COMPLETED:
				case FAILED:
					return false;
				case RUNNING:
					m_aopState = State.CANCELLING;
					m_aopCond.signalAll();
				default:
					break;
			}
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally { m_aopLock.unlock(); }
		
		try {
			canceller.run();
			
			return onCancelled();
		}
		catch ( Throwable e ) {
			Lambdas.guraded(m_aopLock, () -> {
				if ( m_aopState == State.CANCELLING ) {
					m_aopState = State.RUNNING;
					m_aopCond.signalAll();
				}
			});
			return false;
		}
	}
	
	private void notifyListeners(List<Consumer<AsyncExecution<T>>> listeners) {
		List<Consumer<AsyncExecution<T>>> copieds = Lists.newArrayList();
		Lambdas.guraded(m_aopLock, () -> copieds.addAll(listeners));
		
		CompletableFuture.runAsync(() -> copieds.forEach(c -> c.accept(this)));
	}
}
