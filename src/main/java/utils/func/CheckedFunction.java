package utils.func;

import java.util.function.Function;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunction<T,R> {
	public R apply(T input) throws Exception;
	
	public default Try<? extends R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	public default Function<T,R> toSneakyThrowFunction() {
		return (in) -> {
			try {
				return apply(in);
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here (CheckedFunction.sneakyThrow)");
			}
		};
	}
}
