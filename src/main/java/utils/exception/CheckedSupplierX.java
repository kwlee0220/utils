package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplierX<T,X extends Throwable> {
	public T get() throws X;
}

