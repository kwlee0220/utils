package utils.func;

import java.util.function.Function;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedFunction<T,S> implements Function<T,S> {
	private final CheckedFunction<? super T, ? extends S> m_checked;
	private final FailureHandler<S> m_handler;
	
	UncheckedFunction(CheckedFunction<? super T, ? extends S> checked,
						FailureHandler<S> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedFunction is null");
		Utilities.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public S apply(T t) {
		try {
			return m_checked.apply(t);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>((S)null, e));
			throw new AssertionError("Should not be here");
		}
	}

	public static <T,S> UncheckedFunction<T,S> lift(CheckedFunction<? super T, ? extends S> checked,
													FailureHandler<S> handler) {
		return new UncheckedFunction<>(checked, handler);
	}

	public static <T,S> UncheckedFunction<T,S> ignore(CheckedFunction<? super T, ? extends S> checked) {
		return lift(checked, FailureHandlers.ignoreHandler());
	}

	public static <T,S> UncheckedFunction<T,S> sneakyThrow(CheckedFunction<? super T, ? extends S> checked) {
		return lift(checked, FailureHandlers.sneakyThrowHandler());
	}
}