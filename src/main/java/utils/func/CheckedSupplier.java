package utils.func;

import java.util.function.Supplier;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
	public T get() throws Exception;
	
	public default Try<? extends T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	public default Supplier<T> toSneakyThrowSupplier() {
		return () -> {
			try {
				return get();
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here (CheckedSupplier.sneakyThrow)");
			}
		};
	}
}

