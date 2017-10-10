package utils.stream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreamException extends RuntimeException {
	private static final long serialVersionUID = 1336895421748344085L;

	public FStreamException(Throwable cause) {
		super(cause);
	}

	public FStreamException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
