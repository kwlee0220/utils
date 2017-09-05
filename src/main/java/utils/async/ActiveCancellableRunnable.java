package utils.async;

import com.google.common.base.Throwables;

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
				Throwables.throwIfUnchecked(e);
				throw new RuntimeException(e);
			}
			
			throw new AssertionError();
		}
	}
}
