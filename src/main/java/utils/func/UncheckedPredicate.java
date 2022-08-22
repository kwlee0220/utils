package utils.func;

import java.util.function.Predicate;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedPredicate<T> implements Predicate<T> {
	private final CheckedPredicate<? super T> m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedPredicate(CheckedPredicate<? super T> checked, FailureHandler<Void> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedPredicate is null");
		Utilities.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public boolean test(T input) {
		try {
			return m_checked.test(input);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
			throw new AssertionError("Should not be here");
		}
	}

	public static <T> UncheckedPredicate<T> lift(CheckedPredicate<? super T> checked,
												FailureHandler<Void> handler) {
		return new UncheckedPredicate<>(checked, handler);
	}

	public static <T> UncheckedPredicate<T> ignore(CheckedPredicate<? super T> checked) {
		return lift(checked, FailureHandlers.ignoreHandler());
	}

	public static <T> UncheckedPredicate<T> sneakyThrow(CheckedPredicate<? super T> checked) {
		return lift(checked, FailureHandlers.sneakyThrowHandler());
	}
}