package utils.unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumer<T> {
	public void accept(T data) throws Throwable;
}