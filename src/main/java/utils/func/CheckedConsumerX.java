package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumerX<T,X extends Throwable> {
	public void accept(T data) throws X;
	
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