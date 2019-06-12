package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedPredicate<T> {
	public boolean test(T data) throws Throwable;
}
