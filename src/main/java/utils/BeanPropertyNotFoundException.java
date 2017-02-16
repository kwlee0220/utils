package utils;

/**
 * @author Kang-Woo Lee
 */
public class BeanPropertyNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 5349010506558039400L;

	public BeanPropertyNotFoundException(String details) {
		super(details);
	}
}
