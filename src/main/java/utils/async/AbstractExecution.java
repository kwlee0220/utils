package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Option;
import utils.LoggerSettable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractExecution<T> implements Execution<T>, LoggerSettable {
	protected final EventDrivenExecution<T> m_handle = new EventDrivenExecution<>();
	
	protected AbstractExecution() {
		m_handle.setLogger(LoggerFactory.getLogger(AbstractExecution.class));
	}

	@Override
	public State getState() {
		return m_handle.getState();
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

	@Override
	public Logger getLogger() {
		return m_handle.getLogger();
	}

	@Override
	public void setLogger(Logger logger) {
		m_handle.setLogger(logger);
	}
	
	public void dependsOn(Execution<?> exec, T result) {
		exec.whenStarted(m_handle::notifyStarted);
		exec.whenCompleted(r -> m_handle.notifyCompleted(result));
		exec.whenFailed(m_handle::notifyFailed);
		exec.whenCancelled(m_handle::notifyCancelled);
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
	
	protected final void notifyFailed(Throwable cause) {
		m_handle.notifyFailed(cause);
	}
}
