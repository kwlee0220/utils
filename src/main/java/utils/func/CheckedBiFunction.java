package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiFunction<S,T,R> {
	public R apply(S input1, T input2) throws Throwable;
	
	public default Try<? extends R> tryApply(S input1, T input2) {
		try {
			return Try.success(apply(input1, input2));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
