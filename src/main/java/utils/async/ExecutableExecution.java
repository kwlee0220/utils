package utils.async;

import java.util.concurrent.CancellationException;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ExecutableExecution<T> extends AbstractExecution<T> implements AsyncExecution<T> {
	protected abstract T executeWork() throws InterruptedException, CancellationException, Exception;
	
	public final T execute() throws CancellationException, Exception {
		m_handle.notifyStarted();
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			if ( m_handle.notifyCompleted(result) ) {
				return result;
			}
			if ( m_handle.notifyCancelled() ) {
				throw new CancellationException();
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
	
	public Runnable asRunnable() {
		return new Runner();
	}

	private class Runner implements Runnable {
		@Override
		public void run() {
			try {
				m_handle.notifyStarted();
			}
			catch ( Exception e ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			
			try {
				T result = executeWork();
				if ( m_handle.notifyCompleted(result) ) {
					return;
				}
				if ( m_handle.notifyCancelled() ) {
					return;
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
