package utils;


/**
 * 
 * @author Kang-Woo Lee
 */
public class NotReadyException extends RuntimeException {
	private static final long serialVersionUID = -2046196643820761224L;

	public NotReadyException(String details) {
		super(details);
	}
}
