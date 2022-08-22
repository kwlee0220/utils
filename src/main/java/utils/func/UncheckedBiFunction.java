package utils.func;

import java.util.function.BiFunction;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedBiFunction<S,T,R> implements BiFunction<S,T,R> {
	private final CheckedBiFunction<? super S, ? super T, ? extends R> m_checked;
	private final FailureHandler<? extends R> m_handler;
	
	UncheckedBiFunction(CheckedBiFunction<? super S, ? super T, ? extends R> checked,
						FailureHandler<? extends R> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedBiFunction is null");
		Utilities.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public R apply(S input1, T input2) {
		try {
			return m_checked.apply(input1, input2);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
			throw new AssertionError("Should not be here");
		}
	}

	public static <S,T,R> UncheckedBiFunction<S,T,R>
	lift(CheckedBiFunction<? super S, ? super T, ? extends R> checked, FailureHandler<? extends R> handler) {
		return new UncheckedBiFunction<>(checked, handler);
	}

	public static <S,T,R> UncheckedBiFunction<S,T,R>
	ignore(CheckedBiFunction<? super S, ? super T, ? extends R> checked) {
		return lift(checked, FailureHandlers.ignoreHandler());
	}

	public static <S,T,R> UncheckedBiFunction<S,T,R>
	sneakyThrow(CheckedBiFunction<? super S, ? super T, ? extends R> checked) {
		return lift(checked, FailureHandlers.sneakyThrowHandler());
	}
}