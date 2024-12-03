package utils.func;

import com.google.common.base.Preconditions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedRunnable implements Runnable {
	private final CheckedRunnable m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedRunnable(CheckedRunnable checked, FailureHandler<Void> handler) {
		Preconditions.checkArgument(checked != null, "CheckedRunnable is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}
	
	public FailureHandler<Void> getFailureHandler() {
		return m_handler;
	}

	@Override
	public void run() {
		try {
			m_checked.run();
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
		}
	}

	public static UncheckedRunnable lift(CheckedRunnable checked, FailureHandler<Void> handler) {
		return new UncheckedRunnable(checked, handler);
	}

	public static UncheckedRunnable ignore(CheckedRunnable checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	public static UncheckedRunnable sneakyThrow(CheckedRunnable checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}