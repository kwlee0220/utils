package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedPredicate<T> {
	public boolean test(T data) throws Throwable;
	
	public default Try<Boolean> tryTest(T input) {
		try {
			return Try.success(test(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
