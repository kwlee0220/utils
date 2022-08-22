package utils.func;

import java.util.function.Consumer;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedConsumer<T> implements Consumer<T> {
	private final CheckedConsumer<? super T> m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedConsumer(CheckedConsumer<? super T> checked, FailureHandler<Void> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedConsumer is null");
		Utilities.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public void accept(T data) {
		try {
			m_checked.accept(data);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<Void>(null, e));
		}
	}

	public static <T> UncheckedConsumer<T> lift(CheckedConsumer<? super T> checked,
												FailureHandler<Void> handler) {
		return new UncheckedConsumer<>(checked, handler);
	}

	public static <T> UncheckedConsumer<T> ignore(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.ignoreHandler());
	}

	public static <T> UncheckedConsumer<T> sneakyThrow(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.sneakyThrowHandler());
	}
}