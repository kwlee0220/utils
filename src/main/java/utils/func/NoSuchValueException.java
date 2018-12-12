package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class NoSuchValueException extends RuntimeException {
	private static final long serialVersionUID = -2571121760322447470L;

	public NoSuchValueException() {
		super();
	}
	
	public NoSuchValueException(String details) {
		super(details);
	}
}
