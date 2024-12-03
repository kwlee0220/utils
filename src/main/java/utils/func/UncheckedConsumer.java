package utils.func;

import java.util.function.Consumer;

import com.google.common.base.Preconditions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedConsumer<T> implements Consumer<T> {
	private final CheckedConsumer<? super T> m_checked;
	private final FailureHandler<? super T> m_handler;
	
	UncheckedConsumer(CheckedConsumer<? super T> checked, FailureHandler<? super T> handler) {
		Preconditions.checkArgument(checked != null, "CheckedConsumer is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public void accept(T data) {
		try {
			m_checked.accept(data);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(data, e));
		}
	}

	public static <T> UncheckedConsumer<T> lift(CheckedConsumer<? super T> checked,
												FailureHandler<? super T> handler) {
		return new UncheckedConsumer<>(checked, handler);
	}

	public static <T> UncheckedConsumer<T> ignore(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	public static <T> UncheckedConsumer<T> sneakyThrow(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}