package utils.async.op;

import static utils.Utilities.checkState;

import java.util.concurrent.CancellationException;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import utils.Throwables;
import utils.async.CancellableWork;
import utils.async.EventDrivenExecution;
import utils.async.Executions;
import utils.async.StartableExecution;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ListenableFutureAsyncExecution<T> extends EventDrivenExecution<T>
														implements StartableExecution<T>, CancellableWork {
	private volatile ListenableFuture<? extends T> m_future;
	
	protected abstract ListenableFuture<? extends T> startExecution();
	
	@Override
	public void start() {
		if ( notifyStarting() ) {
			try {
				m_future = startExecution();
				Futures.addCallback(m_future, m_callback, Executions.getExecutor());
				
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

	private final FutureCallback<T> m_callback = new FutureCallback<T>() {
		@Override
		public void onSuccess(@Nullable T result) {
			notifyCompleted(result);
		}

		@Override
		public void onFailure(Throwable ex) {
			Throwable cause = Throwables.unwrapThrowable(ex);
			if ( cause instanceof CancellationException ) {
				notifyCancelled();
			}
			else {
				notifyFailed(cause);
			}
		}
	};
}