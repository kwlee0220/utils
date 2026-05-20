package utils.func;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnable {
	public void run() throws Exception;
	
	public default Try<Void> tryRun() {
		try {
			run();
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	public default Runnable toRunnable() {
		return () -> {
			try {
				run();
			}
			catch ( Throwable e ) {
				throw Throwables.toRuntimeException(e);
			}
		};
	}
}
