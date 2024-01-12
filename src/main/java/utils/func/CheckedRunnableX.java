package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnableX<X extends Throwable> {
	public void run() throws X;
	
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
