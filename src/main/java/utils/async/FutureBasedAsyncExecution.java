package utils.async;

import java.util.concurrent.Future;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class FutureBasedAsyncExecution<T> extends AbstractAsyncExecution<T>
												implements CancellableWork, AsyncExecution<T>{
	private volatile Future<? extends T> m_future;
	
	protected abstract Future<? extends T> getFuture();

	@Override
	public void start() {
		if ( notifyStarted() ) {
			try {
				m_future = getFuture();
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}

	@Override
	public boolean cancelWork() {
		if ( m_future != null ) {
			return m_future.cancel(true);
		}
		else {
			return true;
		}
	}
}