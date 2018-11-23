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
public abstract class ExecutableExecution<T> implements Execution<T>, LoggerSettable, Runnable {
	private final EventDrivenExecution<T> m_handle = new EventDrivenExecution<>();
	protected volatile Thread m_thread = null;
	
	protected abstract T executeWork() throws CancellationException, Throwable;
	
	protected ExecutableExecution() {
		m_handle.setLogger(LoggerFactory.getLogger(ExecutableExecution.class));
		m_handle.setCancelWork(() -> {
			if ( m_thread != null ) {
				m_thread.interrupt();
			}
		});
	}
	
//	public EventDrivenExecution<T> getHandle() {
//		return m_handle;
//	}

	@Override
	public State getState() {
		return m_handle.getState();
	}
	
	@Override
	public void run() {
		if ( !m_handle.notifyStarted() ) {
			// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
			return;
		}
		m_thread = Thread.currentThread();
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			if ( !m_handle.notifyCompleted(result) && !isDone() ) {
				throw new IllegalStateException("unexpected state: " + getState());
			}
		}
		catch ( InterruptedException | CancellationException e ) {
			m_handle.cancel();
		}
		catch ( Throwable e ) {
			m_handle.notifyFailed(Throwables.unwrapThrowable(e));
		}
	}
	
	public Execution<T> start() {
		return Executors.start(this);
	}
	
	@Override
	public boolean cancel() {
		return m_handle.cancel();
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
	public Logger getLogger() {
		return m_handle.getLogger();
	}

	@Override
	public void setLogger(Logger logger) {
		m_handle.setLogger(logger);
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
	
	protected boolean notifyStarting() {
		return m_handle.notifyStarting();
	}
	
	protected boolean notifyStarted() {
		return m_handle.notifyStarted();
	}
	
	protected boolean notifyCompleted(T result) {
		return m_handle.notifyCompleted(result);
	}
	
	protected boolean notifyCancelling() {
		return m_handle.notifyCancelling();
	}
	
	protected boolean notifyCancelled() {
		return m_handle.notifyCancelled();
	}
	
	protected boolean notifyFailed(Throwable cause) {
		return m_handle.notifyFailed(cause);
	}
}
