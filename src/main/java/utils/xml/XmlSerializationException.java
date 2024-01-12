package utils.xml;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class XmlSerializationException extends RuntimeException {
	private static final long serialVersionUID = 3764460034212865303L;

	public XmlSerializationException(String details) {
		super(details);
	}

	public XmlSerializationException(String details, Throwable cause) {
		super(details, cause);
	}
}
