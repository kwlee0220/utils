package utils.http;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulIOException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RESTfulIOException(String details, Throwable cause) {
		super(String.format("%s, cause=%s", details, cause), cause);
	}
	
	public RESTfulIOException(String details) {
		super(details);
	}
	
	public RESTfulIOException(Throwable cause) {
		super(cause);
	}
}
