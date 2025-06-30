package utils.async;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LengthOneOverridingQueue<T> {
	private final Guard m_guard = Guard.create();
	private T m_data;
	
	public void add(T item) {
		m_guard.run(() -> {
			m_data = item;
		});
	}
	
	public T poll() throws InterruptedException {
		return m_guard.awaitCondition(() -> m_data != null)
					.andGet(this::getInGuard);
	}
	
	public T poll(Duration timeout) throws InterruptedException, TimeoutException {
		return m_guard.awaitCondition(() -> m_data != null, timeout)
					.andGet(this::getInGuard);
	}
	
	public T peek() {
		return m_guard.get(this::getInGuard);
	}
	
	private T getInGuard() {
		T data = m_data;
		if ( data != null ) {
			m_data = null; // Clear the data after polling
		}
        return data;
	}
}
