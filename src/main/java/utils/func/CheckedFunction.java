package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunction<T,R> {
	public R apply(T input) throws Throwable;
	
	public default Try<? extends R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
