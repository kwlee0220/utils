package utils.func;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedSupplier<T> implements Supplier<T> {
	private final CheckedSupplier<? extends T> m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedSupplier(CheckedSupplier<? extends T> checked, FailureHandler<Void> handler) {
		Preconditions.checkArgument(checked != null, "CheckedSupplier is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
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
												FailureHandler<Void> handler) {
		return new UncheckedSupplier<>(checked, handler);
	}

	public static <T> UncheckedSupplier<T> ignore(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	public static <T> UncheckedSupplier<T> sneakyThrow(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}