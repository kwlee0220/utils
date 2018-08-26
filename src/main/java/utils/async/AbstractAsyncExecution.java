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
import io.vavr.control.Try;
import utils.Lambdas;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncExecution<V> implements AsyncExecution<V> {
	protected enum ImplState { NOT_STARTED, STARTING, RUNNING, COMPLETED, FAILED,
								CANCELLING, CANCELLED };
	
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	protected final Condition m_aopCond = m_aopLock.newCondition();
	protected ImplState m_aopState = ImplState.NOT_STARTED;
	private Result<V> m_result;
	private final List<Runnable> m_startListeners = Lists.newCopyOnWriteArrayList();
	private final List<Consumer<Result<V>>> m_finishListeners = Lists.newCopyOnWriteArrayList();
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( m_aopState == ImplState.NOT_STARTED ) {
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
			while ( m_aopState == ImplState.NOT_STARTED ) {
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
	public Result<V> getResult() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( true ) {
				if ( m_aopState == ImplState.COMPLETED
					|| m_aopState == ImplState.FAILED
					|| m_aopState == ImplState.CANCELLED ) {
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
	public Result<V> getResult(long timeout, TimeUnit unit) throws InterruptedException,
																	TimeoutException {
		m_aopLock.lock();
		try {
			while ( true ) {
				if ( m_aopState == ImplState.COMPLETED
					|| m_aopState == ImplState.FAILED
					|| m_aopState == ImplState.CANCELLED ) {
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
	public State getState() {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					return State.NOT_STARTED;
				case RUNNING:
					return State.RUNNING;
				case COMPLETED:
					return State.COMPLETED;
				case FAILED:
					return State.FAILED;
				case CANCELLED:
				case CANCELLING:
					return State.CANCELLED;
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
			return m_aopState != ImplState.NOT_STARTED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isDone() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.COMPLETED || m_aopState == ImplState.FAILED
					|| m_aopState == ImplState.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isCompleted() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.COMPLETED;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public final boolean isCancelled() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isFailed() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.FAILED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public V get() throws InterruptedException, ExecutionException, CancellationException {
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
	public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
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
		cancel();
		Try.run(this::waitForDone);
		
		return isCancelled();
	}

	public boolean notifyStarted() {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = ImplState.RUNNING;
					m_aopCond.signalAll();
					notifyStartListeners();
			    	return true;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}

	public boolean notifyCompleted(V result) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.some(result);
					m_aopState = ImplState.COMPLETED;
					m_aopCond.signalAll();
			    	notifyFinishListeners();
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
	
    public boolean notifyFailed(Throwable cause) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:	// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_result = Result.failure(cause);
			    	m_aopState = ImplState.FAILED;
			    	m_aopCond.signalAll();
			    	notifyFinishListeners();
			    	
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
	
    public boolean notifyCancelled() {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
					return false;
				case RUNNING:
				case CANCELLING:
					m_result = Result.none();
					m_aopState = ImplState.CANCELLED;
					m_aopCond.signalAll();
			    	notifyFinishListeners();
				case CANCELLED:
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
	public void whenStarted(Runnable listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_startListeners.add(listener);
					break;
				default:
					CompletableFuture.runAsync(listener::run);
					break;
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}

	@Override
	public void whenDone(Consumer<Result<V>> listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					CompletableFuture.runAsync(() -> listener.accept(m_result));
					break;
				default:
					m_finishListeners.add(listener);
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
		Lambdas.guradedRun(m_aopLock, action);
	}
	
	protected void start(CheckedRunnable starter) {
		Objects.requireNonNull(starter, "starter is null");
		
		m_aopLock.lock();
		try {
			Preconditions.checkState(m_aopState == ImplState.NOT_STARTED,
									"AsyncExecution has been started already, state=" + m_aopState);
			m_aopState = ImplState.STARTING;
			m_aopCond.signalAll();
		}
		finally {
			m_aopLock.unlock();
		}
		
		try {
			starter.run();
			
			Lambdas.guradedRun(m_aopLock, () -> {
				m_aopState = ImplState.RUNNING;
				m_aopCond.signalAll();
			});
			
			notifyStartListeners();
		}
		catch ( Throwable e ) {
			Lambdas.guradedRun(m_aopLock, () -> {
				m_aopState = ImplState.FAILED;
				m_aopCond.signalAll();
			});
			
			notifyFailed(e);
		}
	}
	
	protected void cancel(CheckedRunnable canceller) throws IllegalStateException {
		Objects.requireNonNull(canceller, "canceller is null");
		
		m_aopLock.lock();
		try {
			while ( m_aopState == ImplState.CANCELLING
				|| m_aopState == ImplState.STARTING ) {
				m_aopCond.await();
			}
			
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = ImplState.CANCELLED;
					m_aopCond.signalAll();
					return;
				case CANCELLED:
					return;
				case COMPLETED:
				case FAILED:
					throw new IllegalStateException("AsyncExecution has been finished, "
													+ "state=" + m_aopState);
				case RUNNING:
					m_aopState = ImplState.CANCELLING;
					m_aopCond.signalAll();
				default:
					break;
			}
		}
		catch ( InterruptedException e ) {
			throw new ThreadInterruptedException(e);
		}
		finally { m_aopLock.unlock(); }
		
		try {
			canceller.run();
			
			notifyCancelled();
		}
		catch ( Throwable e ) {
			Lambdas.guradedRun(m_aopLock, () -> {
				if ( m_aopState == ImplState.CANCELLING ) {
					m_aopState = ImplState.RUNNING;
					m_aopCond.signalAll();
				}
			});
			
			throw new RuntimeException("AsyncExecution cancellation is failed");
		}
	}
	
	private void notifyStartListeners() {
		CompletableFuture.runAsync(() -> m_startListeners.forEach(Runnable::run));
	}
	
	private void notifyFinishListeners() {
		CompletableFuture.runAsync(() -> m_finishListeners.forEach(c -> c.accept(m_result)));
	}
}
