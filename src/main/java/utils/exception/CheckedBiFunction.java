package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiFunction<S,T,R> {
	public R apply(S input1, T input2) throws Throwable;
}
