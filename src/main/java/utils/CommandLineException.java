package utils;


/**
 * 
 * @author Kang-Woo Lee
 */
public class CommandLineException extends RuntimeException {
	private static final long serialVersionUID = -1877880491110715578L;

	public CommandLineException(String details) {
		super(details);
	}
}
