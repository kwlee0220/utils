package utils.http;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulRemoteException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private final RESTfulErrorEntity m_error;

	public RESTfulRemoteException(RESTfulErrorEntity error) {
		super(error.toString());
		
		m_error = error;
	}

	public RESTfulRemoteException(String details) {
		super(details);
		
		m_error = null;
	}
	
	public RESTfulErrorEntity getRemoteErrorEntity() {
		return m_error;
	}
}
