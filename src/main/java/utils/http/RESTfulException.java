package utils.http;

/**
 * RESTful 호출 과정에서 발생하는 모든 오류의 공통 상위 unchecked 예외.
 * <p>
 * 원격 서버가 반환한 에러는 {@link RESTfulRemoteException}으로, 네트워크 실패나 응답 파싱 실패 등
 * 입출력 계열 오류는 {@link RESTfulIOException}으로 표현된다. 두 오류를 구분 없이 처리하려는
 * 호출부는 본 클래스를 catch하면 된다.
 * <p>
 * {@link RuntimeException}을 상속하여 호출부에 checked 예외를 강제하지 않는다
 * (레포의 {@code Runtime*Exception} 래핑 컨벤션).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 상세 메시지로 생성한다.
	 *
	 * @param details 상세 메시지.
	 */
	public RESTfulException(String details) {
		super(details);
	}

	/**
	 * 상세 메시지와 원인 예외로 생성한다.
	 *
	 * @param details 상세 메시지.
	 * @param cause   원인 예외.
	 */
	public RESTfulException(String details, Throwable cause) {
		super(details, cause);
	}

	/**
	 * 원인 예외로 생성한다.
	 *
	 * @param cause 원인 예외.
	 */
	public RESTfulException(Throwable cause) {
		super(cause);
	}
}
