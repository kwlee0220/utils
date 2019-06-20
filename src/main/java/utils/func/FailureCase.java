package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class FailureCase<T> {
	private final T m_data;
	private final Throwable m_cause;
	
	public FailureCase(T data, Throwable cause) {
		m_data = data;
		m_cause = cause;
	}
	
	public T getData() {
		return m_data;
	}
	
	public Throwable getCause() {
		return m_cause;
	}
}