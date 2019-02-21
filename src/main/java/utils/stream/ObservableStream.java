package utils.stream;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import utils.Utilities;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ObservableStream<T> implements FStream<T> {
	private final static int DEFAULT_LENGTH = 128;
	
	private final Observable<? extends T> m_ob;
	private final Disposable m_subscription;
	private final SuppliableFStream<T> m_output;
	
	ObservableStream(Observable<? extends T> ob, int queueLength) {
		Utilities.checkArgument(queueLength > 0, "queueLength > 0, but: " + queueLength);
		
		m_ob = ob;
		m_output = new SuppliableFStream<>(queueLength);
		m_subscription = ob.subscribe(m_output::supply, m_output::endOfSupply,
										m_output::endOfSupply);
	}
	
	ObservableStream(Observable<? extends T> ob) {
		this(ob, DEFAULT_LENGTH);
	}

	@Override
	public void close() throws Exception {
		m_subscription.dispose();
		m_output.close();
	}

	@Override
	public FOption<T> next() {
		return m_output.next();
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_ob);
	}
}
