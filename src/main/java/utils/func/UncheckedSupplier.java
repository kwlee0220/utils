package utils.func;

import java.util.function.Supplier;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedSupplier<T> implements Supplier<T> {
	private final CheckedSupplier<? extends T> m_checked;
	private final FailureHandler<? extends T> m_handler;
	
	UncheckedSupplier(CheckedSupplier<? extends T> checked, FailureHandler<? extends T> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedSupplier is null");
		Utilities.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public T get() {
		try {
			return m_checked.get();
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
			throw new AssertionError("Should not be here");
		}
	}

	public static <T> UncheckedSupplier<T> lift(CheckedSupplier<? extends T> checked,
												FailureHandler<? extends T> handler) {
		return new UncheckedSupplier<>(checked, handler);
	}

	public static <T> UncheckedSupplier<T> ignore(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.ignoreHandler());
	}

	public static <T> UncheckedSupplier<T> sneakyThrow(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.sneakyThrowHandler());
	}
}