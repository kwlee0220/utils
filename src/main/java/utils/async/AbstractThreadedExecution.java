package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractThreadedExecution<T> extends AbstractAsyncExecution<T>
												implements StartableExecution<T> {
	protected abstract T executeWork() throws InterruptedException, CancellationException,
												Exception;
	
	public final T run() throws CancellationException, Exception {
		notifyStarted();
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			if ( notifyCompleted(result) ) {
				return result;
			}
			if ( notifyCancelled() ) {
				throw new CancellationException();
			}
			
			return pollResult().get().get();
		}
		catch ( InterruptedException | CancellationException e ) {
			notifyCancelled();
			throw e;
		}
		catch ( Throwable e ) {
			notifyFailed(Throwables.unwrapThrowable(e));
			throw e;
		}
	}
	
	@Override
	public final void start() {
		notifyStarting();
		
		Runnable work = asRunnable();
		Executor exector = getExecutor();
		if ( exector != null ) {
			exector.execute(work);
		}
		else {
			Thread thread = new Thread(work);
			thread.start();
		}
	}
	
	private Runnable asRunnable() {
		return new Runner();
	}

	private class Runner implements Runnable {
		@Override
		public void run() {
			try {
				notifyStarted();
			}
			catch ( Exception e ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			
			try {
				T result = executeWork();
				if ( notifyCompleted(result) ) {
					return;
				}
				if ( notifyCancelled() ) {
					return;
				}
			}
			catch ( InterruptedException | CancellationException e ) {
				notifyCancelled();
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}
}
