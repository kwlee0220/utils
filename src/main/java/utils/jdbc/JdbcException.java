package utils.jdbc;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcException extends RuntimeException {
	private static final long serialVersionUID = -5253204991351254866L;

	public JdbcException(Throwable cause) {
		super(cause);
	}
	
	public JdbcException(String details) {
		super(details);
	}
}
