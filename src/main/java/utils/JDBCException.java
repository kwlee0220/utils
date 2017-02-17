package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JDBCException extends RuntimeException {
	private static final long serialVersionUID = -5253204991351254866L;

	public JDBCException(Throwable cause) {
		super(cause);
	}
	
	public JDBCException(String details) {
		super(details);
	}
}
