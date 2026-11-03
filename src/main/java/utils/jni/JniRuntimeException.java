package utils.jni;



/**
 * <code>JniRuntimeException</code>는 JNI를 이용한 모듈을 수행시 JNI와 관련된 예외를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JniRuntimeException extends RuntimeException {
	private static final long serialVersionUID = 4926540670892527294L;

	/**
	 * 주어진 메시지를 갖는 예외 객체를 생성한다.
	 *
	 * @param msg	예외 메시지.
	 */
	public JniRuntimeException(String msg) {
		super(msg);
	}
}
