package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnable {
	public void run() throws Throwable;
	
	public default Try<Void> tryRun() {
		try {
			run();
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
