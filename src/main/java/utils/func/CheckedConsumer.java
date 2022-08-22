package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumer<T> {
	public void accept(T data) throws Throwable;
	
	public default Try<Void> tryAccept(T data) {
		try {
			accept(data);
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}