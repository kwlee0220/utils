package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunctionX<T,R,X extends Throwable> {
	public R apply(T input) throws X;
	
	public default Try<? extends R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
