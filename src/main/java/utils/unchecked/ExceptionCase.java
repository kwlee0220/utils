package utils.unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class ExceptionCase<T> {
	private final T m_data;
	private final Throwable m_cause;
	
	public ExceptionCase(T data, Throwable cause) {
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