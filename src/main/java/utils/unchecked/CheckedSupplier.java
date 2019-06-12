package utils.unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
	public T get() throws Throwable;
}

