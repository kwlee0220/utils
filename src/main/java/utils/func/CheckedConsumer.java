package utils.func;

import java.util.function.Consumer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumer<T> {
	public void accept(T data) throws Throwable;
	
	public default Try<Void> tryAccept(T data) {
		try {
			accept(data);
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	public static <T> CheckedConsumer<T> fromConsumer(final Consumer<T> consumer) {
		return new CheckedConsumer<T>() {
			@Override
			public void accept(T data) {
				consumer.accept(data);
			}
		};
	}
}