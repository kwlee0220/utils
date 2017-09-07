package utils.async;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ActiveCancellableRunnable extends ActiveCancellable
													implements Runnable {
	protected abstract void runTask() throws Exception;

	public void run() {
		try {
			begin();
			
			runTask();
			
			complete();
		}
		catch ( Exception e ) {
			if ( checkCancelled() ) {
				return;
			}
			if ( markFailed(e) ) {
				throw Throwables.toRuntimeException(e);
			}
			
			throw new AssertionError();
		}
	}
}
