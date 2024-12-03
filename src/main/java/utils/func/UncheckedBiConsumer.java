package utils.func;

import java.util.function.BiConsumer;

import com.google.common.base.Preconditions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedBiConsumer<S,T> implements BiConsumer<S,T> {
	private final CheckedBiConsumer<? super S, ? super T> m_checked;
	private final FailureHandler<Tuple<? super S, ? super T>> m_handler;
	
	UncheckedBiConsumer(CheckedBiConsumer<? super S, ? super T> checked,
						FailureHandler<Tuple<? super S, ? super T>> handler) {
		Preconditions.checkArgument(checked != null, "CheckedBiConsumer is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public void accept(S input1, T input2) {
		try {
			m_checked.accept(input1, input2);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(Tuple.of(input1, input2), e));
		}
	}

	public static <S,T> UncheckedBiConsumer<S,T> lift(CheckedBiConsumer<? super S, ? super T> checked,
														FailureHandler<Tuple<? super S, ? super T>> handler) {
		return new UncheckedBiConsumer<>(checked, handler);
	}

	public static <S,T> UncheckedBiConsumer<S,T> ignore(CheckedBiConsumer<? super S, ? super T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	public static <S,T> UncheckedBiConsumer<S,T> sneakyThrow(CheckedBiConsumer<? super S, ? super T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}