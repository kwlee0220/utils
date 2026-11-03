package utils.func;


/**
 * 요청한 값이 존재하지 않을 때 발생하는 예외이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class NoSuchValueException extends RuntimeException {
	private static final long serialVersionUID = -2571121760322447470L;

	/**
	 * 상세 메시지 없이 예외 객체를 생성한다.
	 */
	public NoSuchValueException() {
		super();
	}
	
	/**
	 * 주어진 상세 메시지로 예외 객체를 생성한다.
	 *
	 * @param details	상세 메시지.
	 */
	public NoSuchValueException(String details) {
		super(details);
	}
}
