package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ValidationException extends RuntimeException {
	private static final long serialVersionUID = -4801638918653018162L;
	
	private final ValidationErrorMessages m_msgs;

	public ValidationException(ValidationErrorMessages msgs) {
		m_msgs = msgs;
	}
	
	public ValidationErrorMessages getMessages() {
		return m_msgs;
	}
}
