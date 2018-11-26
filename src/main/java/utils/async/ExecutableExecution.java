package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Option;
import utils.LoggerSettable;
import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ExecutableExecution<T> implements Execution<T>, LoggerSettable {
	private final EventDrivenExecution<T> m_handle = new EventDrivenExecution<>();
	protected volatile Thread m_thread = null;

	protected abstract T executeWork() throws InterruptedException, CancellationException, Exception;
	
	protected ExecutableExecution() {
		m_handle.setLogger(LoggerFactory.getLogger(ExecutableExecution.class));
	}

	@Override
	public State getState() {
		return m_handle.getState();
	}
	
	public final T execute() throws CancellationException, Exception {
		if ( !m_handle.notifyStarted() ) {
			String msg = String.format("unexpected state: current[%s], event=[%s]",
										getState(), State.RUNNING);
			throw new IllegalStateException(msg);
		}
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			if ( m_handle.notifyCompleted(result) ) {
				return result;
			}
			
			// DONE인 상태 또는 CANCELLING 상태 가능
			if ( isCancelRequested() ) {
				m_handle.notifyCancelled();
			}
			
			return m_handle.pollResult().get().get();
		}
		catch ( InterruptedException | CancellationException e ) {
			m_handle.notifyCancelled();
			throw e;
		}
		catch ( Throwable e ) {
			m_handle.notifyFailed(Throwables.unwrapThrowable(e));
			throw e;
		}
	}
	
	public final void start() {
		m_handle.notifyStarting();

		Thread thread = new Thread(asRunnable());
		thread.start();
	}

	@Override
	public final boolean cancel() {
		if ( !m_handle.notifyCancelling() ) {
			return false;
		}
		
		if ( this instanceof CancellableWork ) {
			try {
				((CancellableWork)this).cancelWork();
			}
			catch ( Exception e ) {
				getLogger().warn("fails to cancel work: {}, cause={}", this, e.toString());
				m_handle.notifyFailed(e);
				
				return false;
			}
		}
		else {
			if ( m_thread != null ) {
				m_thread.interrupt();
			}
		}
		
		return m_handle.notifyCancelled();
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		return m_handle.get();
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		return m_handle.get(timeout, unit);
	}

	@Override
	public Option<Result<T>> pollResult() {
		return m_handle.pollResult();
	}

	@Override
	public Result<T> waitForResult() throws InterruptedException {
		return m_handle.waitForResult();
	}

	@Override
	public Result<T> waitForResult(long timeout, TimeUnit unit)
		throws InterruptedException, TimeoutException {
		return m_handle.waitForResult(timeout, unit);
	}

	@Override
	public void waitForStarted() throws InterruptedException {
		m_handle.waitForStarted();
	}

	@Override
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException {
		return m_handle.waitForStarted(timeout, unit);
	}

	@Override
	public void waitForDone() throws InterruptedException {
		m_handle.waitForDone();
	}

	@Override
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException {
		return m_handle.waitForDone(timeout, unit);
	}

	@Override
	public void whenStarted(Runnable listener) {
		m_handle.whenStarted(listener);
	}

	@Override
	public void whenDone(Runnable listener) {
		m_handle.whenDone(listener);
	}
	
	public Runnable asRunnable() {
		return new Runner();
	}

	@Override
	public Logger getLogger() {
		return m_handle.getLogger();
	}

	@Override
	public void setLogger(Logger logger) {
		m_handle.setLogger(logger);
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), getState());
	}
	
	protected final void checkCancelled() {
		State state = getState();
		if ( state == State.CANCELLING || state == State.CANCELLED ) {
			throw new CancellationException();
		}
	}
	
	protected final boolean isCancelRequested() {
		return m_handle.getState() == State.CANCELLING;
	}
	
	protected final boolean notifyCancelled() {
		return m_handle.notifyCancelled();
	}
	
	protected final boolean notifyFailed(Throwable cause) {
		return m_handle.notifyFailed(cause);
	}

	private class Runner implements Runnable {
		@Override
		public void run() {
			if ( !m_handle.notifyStarted() ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			m_thread = Thread.currentThread();
			
			try {
				T result = executeWork();
				if (!m_handle.notifyCompleted(result) && !m_handle.isDone() ) {
					m_handle.notifyFailed(new IllegalStateException("unexpected state: " + getState()));
				}
			}
			catch ( InterruptedException | CancellationException e ) {
				m_handle.notifyCancelled();
			}
			catch ( Throwable e ) {
				m_handle.notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}
}
