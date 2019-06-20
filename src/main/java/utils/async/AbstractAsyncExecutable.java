package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Throwables;
import utils.async.Execution.State;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncExecutable<T> implements AsyncExecutable<T>,
															LoggerSettable {
	private Logger m_logger = LoggerFactory.getLogger(AbstractAsyncExecutable.class);
	
	protected abstract T executeWork() throws InterruptedException, CancellationException,
												Exception;
	
	@Override
	public final EventDrivenExecution<T> execute() {
		EventDrivenExecution<T> exec = new EventDrivenExecution<>();
		if ( !exec.notifyStarting() ) {
			String details = String.format("expected=%s, actual=%s",
											State.NOT_STARTED, exec.getState());
			throw new IllegalStateException(details);
		}

		Thread thread = new Thread(new Runner(exec));
		thread.start();
		
		return exec;
	}
	
	@Override
	public final EventDrivenExecution<T> execute(Executor executor) {
		Objects.requireNonNull(executor, "Executor");
		
		EventDrivenExecution<T> exec = new EventDrivenExecution<>();
		if ( !exec.notifyStarting() ) {
			String details = String.format("expected=%s, actual=%s",
											State.NOT_STARTED, exec.getState());
			throw new IllegalStateException(details);
		}

		executor.execute(new Runner(exec));
		return exec;
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	private class Runner implements Runnable {
		private final EventDrivenExecution<T> m_exec;
		
		private Runner(EventDrivenExecution<T> handle) {
			m_exec = handle;
		}
		
		@Override
		public void run() {
			try {
				if ( !m_exec.notifyStarted() ) {
					String details = String.format("expected=%s, actual=%s",
												State.STARTING, m_exec.getState());
					m_exec.notifyFailed(new IllegalStateException(details));
					return;
				}
			}
			catch ( Exception e ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				m_exec.notifyFailed(e);
				return;
			}
			
			try {
				T result = executeWork();
				if ( m_exec.notifyCompleted(result) ) {
					return;
				}
				if ( m_exec.notifyCancelled() ) {
					return;
				}
			}
			catch ( InterruptedException | CancellationException e ) {
				m_exec.notifyCancelled();
			}
			catch ( Throwable e ) {
				m_exec.notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}
}
