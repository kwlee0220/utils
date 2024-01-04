package utils.async;

import static utils.Utilities.checkState;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
abstract class CompletableFutureAsyncExecution<T> extends EventDrivenExecution<T>
											implements StartableExecution<T>, CancellableWork {
	private volatile CompletableFuture<? extends T> m_future;
	
	protected abstract CompletableFuture<? extends T> startExecution();

	@Override
	public void start() {
		if ( notifyStarting() ) {
			try {
				m_future = startExecution();
				m_future.whenComplete((ret,ex) -> {
					if ( ex == null ) {
						notifyCompleted(ret);
					}
					else {
						Throwable cause = Throwables.unwrapThrowable(ex);
						if ( cause instanceof CancellationException ) {
							notifyCancelled();
						}
						else {
							notifyFailed(cause);
						}
					}
				});
				
				boolean done = notifyStarted();
				checkState(done);
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
	
	@Override
	public String toString() {
		if ( m_future == null ) {
			return String.format("un-assigned");
		}
		else {
			return "" + m_future;
		}
	}
}