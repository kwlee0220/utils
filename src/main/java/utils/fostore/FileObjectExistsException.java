package utils.fostore;

/**
 * @author Kang-Woo Lee
 */
public class FileObjectExistsException extends Exception {
	private static final long serialVersionUID = -137685690952812921L;

	public FileObjectExistsException(String details) {
		super(details);
	}
}
