package utils;


/**
 * 대상이 아직 준비되지 않아 요청을 지금 처리할 수 없음을 나타내는 unchecked 예외.
 * <p>
 * 예를 들어 {@link Initializable#initialize()}가 초기화를 뒤로 미룰 때 사용한다.
 *
 * @author Kang-Woo Lee
 */
public class NotReadyException extends RuntimeException {
	private static final long serialVersionUID = -2046196643820761224L;

	/**
	 * 상세 메시지로 예외를 생성한다.
	 *
	 * @param details	상세 메시지.
	 */
	public NotReadyException(String details) {
		super(details);
	}
}
