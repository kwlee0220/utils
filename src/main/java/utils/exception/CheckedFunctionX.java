package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunctionX<T,R,X extends Throwable> {
	public R apply(T input) throws X;
}
