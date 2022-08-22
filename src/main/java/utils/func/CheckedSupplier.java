package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
	public T get() throws Throwable;
	
	public default Try<? extends T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}

