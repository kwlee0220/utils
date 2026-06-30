package utils.http;


/**
 * RESTful 호출의 입출력 계열 오류(네트워크 실패, 응답 파싱 실패, 알 수 없는 서버 에러 등)를 표현하는
 * unchecked 예외.
 * <p>
 * 이름에 {@code IOException}이 들어가지만 {@link RuntimeException}을 상속하여 호출부에 checked 예외를
 * 강제하지 않는다(레포의 {@code Runtime*Exception} 래핑 컨벤션).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulIOException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 상세 메시지와 원인 예외로 생성한다. (원인 정보가 메시지에 함께 합쳐진다)
	 *
	 * @param details 상세 메시지.
	 * @param cause   원인 예외.
	 */
	public RESTfulIOException(String details, Throwable cause) {
		super(String.format("%s, cause=%s", details, cause), cause);
	}

	/**
	 * 상세 메시지로 생성한다.
	 *
	 * @param details 상세 메시지.
	 */
	public RESTfulIOException(String details) {
		super(details);
	}

	/**
	 * 원인 예외로 생성한다.
	 *
	 * @param cause 원인 예외.
	 */
	public RESTfulIOException(Throwable cause) {
		super(cause);
	}
}
