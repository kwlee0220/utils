package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunction<T,R> {
	public R apply(T input) throws Throwable;
}
