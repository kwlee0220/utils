package utils.stream;


/**
 * {@link FStream} 처리 중 발생한 오류를 나타내는 비검사 예외.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreamException extends RuntimeException {
	private static final long serialVersionUID = 1336895421748344085L;

	/**
	 * 주어진 메시지로 예외를 생성한다.
	 *
	 * @param msg	예외 메시지.
	 */
	public FStreamException(String msg) {
		super(msg);
	}

	/**
	 * 주어진 원인 예외를 감싸는 예외를 생성한다.
	 *
	 * @param cause	원인 예외.
	 */
	public FStreamException(Throwable cause) {
		super(cause);
	}

	/**
	 * 주어진 메시지와 원인 예외로 예외를 생성한다.
	 *
	 * @param msg	예외 메시지.
	 * @param cause	원인 예외.
	 */
	public FStreamException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
