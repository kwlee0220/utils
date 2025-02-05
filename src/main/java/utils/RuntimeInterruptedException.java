package utils;


/**
 * 
 * @author Kang-Woo Lee
 */
public class RuntimeInterruptedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RuntimeInterruptedException(InterruptedException cause) {
		super(cause);
	}
	
	public InterruptedException getCause() {
		return (InterruptedException) super.getCause();
	}
}
