package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplierX<T,X extends Throwable> {
	public T get() throws X;
	
	public default Try<? super T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}

