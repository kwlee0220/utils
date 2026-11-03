package utils.xml;


/**
 * 객체를 XML로 serialize하거나 XML로부터 객체를 적재하는 과정에서 발생하는 예외.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class XmlSerializationException extends RuntimeException {
	private static final long serialVersionUID = 3764460034212865303L;

	/**
	 * 주어진 메시지를 갖는 예외 객체를 생성한다.
	 *
	 * @param details	예외 메시지.
	 */
	public XmlSerializationException(String details) {
		super(details);
	}

	/**
	 * 주어진 메시지와 원인 예외를 갖는 예외 객체를 생성한다.
	 *
	 * @param details	예외 메시지.
	 * @param cause		원인 예외.
	 */
	public XmlSerializationException(String details, Throwable cause) {
		super(details, cause);
	}
}
