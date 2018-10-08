package utils.fostore;

/**
 * @author Kang-Woo Lee
 */
public class FileObjectNotFoundException extends RuntimeException {
	private static final long serialVersionUID = -686343539741833180L;

	public FileObjectNotFoundException(String details) {
		super(details);
	}
}
