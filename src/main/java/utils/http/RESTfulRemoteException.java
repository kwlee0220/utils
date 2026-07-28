package utils.http;

/**
 * 원격 RESTful 서버가 반환한 에러를 표현하는 unchecked 예외.
 * <p>
 * 서버가 구조화된 에러 엔티티를 반환한 경우 {@link RESTfulErrorEntity}를 보유하며
 * ({@link #getRemoteErrorEntity()}로 조회), 그렇지 않으면 메시지/원인만 갖는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulRemoteException extends RESTfulException {
	private static final long serialVersionUID = 1L;

	private final RESTfulErrorEntity m_error;

	/**
	 * 구조화된 에러 엔티티로 생성한다. 메시지는 {@code error.toString()}이 된다.
	 *
	 * @param error 원격 에러 엔티티.
	 */
	public RESTfulRemoteException(RESTfulErrorEntity error) {
		super(error.toString());

		m_error = error;
	}

	/**
	 * 상세 메시지로 생성한다. (에러 엔티티 없음)
	 *
	 * @param details 상세 메시지.
	 */
	public RESTfulRemoteException(String details) {
		super(details);

		m_error = null;
	}

	/**
	 * 상세 메시지와 원인 예외로 생성한다. (에러 엔티티 없음)
	 *
	 * @param details 상세 메시지.
	 * @param cause   원인 예외.
	 */
	public RESTfulRemoteException(String details, Throwable cause) {
		super(details, cause);

		m_error = null;
	}

	/**
	 * 보유한 원격 에러 엔티티를 반환한다.
	 *
	 * @return 에러 엔티티. 보유하지 않으면 {@code null}.
	 */
	public RESTfulErrorEntity getRemoteErrorEntity() {
		return m_error;
	}
}
