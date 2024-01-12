package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedPredicateX<T,X extends Throwable> {
	public boolean test(T data) throws X;
	
	public default Try<? super T> tryTest(T input) {
		try {
			return Try.success(test(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
