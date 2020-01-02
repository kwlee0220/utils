package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiFunctionX<S,T,R,X extends Throwable> {
	public R apply(S input1, T input2) throws X;
}
