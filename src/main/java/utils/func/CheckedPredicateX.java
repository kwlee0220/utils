package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedPredicateX<T,X extends Throwable> {
	public boolean test(T data) throws X;
}
