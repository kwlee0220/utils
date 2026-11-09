package utils.http;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

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

	/** HTTP 상태 코드를 알 수 없는 경우의 값. (HTTP 응답에서 비롯되지 않은 오류) */
	public static final int UNKNOWN_STATUS_CODE = -1;

	private int m_httpStatusCode = UNKNOWN_STATUS_CODE;
	private @Nullable String m_responseBody;
	// 헤더 이름은 대소문자를 구분하지 않는다.
	private transient Map<String,List<String>> m_responseHeaders = Map.of();

	/**
	 * 이 오류를 발생시킨 HTTP 응답의 상태 코드를 반환한다.
	 * <p>
	 * {@link HttpRESTfulClient}가 에러 응답(비-2xx)을 예외로 변환할 때 채워 넣는다. 네트워크 실패나
	 * 응답 파싱 실패처럼 HTTP 응답에서 비롯되지 않은 오류는 {@link #UNKNOWN_STATUS_CODE}이다.
	 * <p>
	 * 프로토콜상 특정 상태 코드가 의미를 갖는 경우(예: 취소 거부를 뜻하는 {@code 409 Conflict})
	 * 호출부가 이 값으로 분기할 수 있다.
	 *
	 * @return HTTP 상태 코드. 알 수 없으면 {@link #UNKNOWN_STATUS_CODE}.
	 */
	public int getHttpStatusCode() {
		return m_httpStatusCode;
	}

	/**
	 * 이 오류를 발생시킨 HTTP 응답의 상태 코드를 설정한다.
	 * <p>
	 * {@link HttpRESTfulClient}가 예외를 던지기 직전에 한 번 호출한다.
	 *
	 * @param statusCode HTTP 상태 코드.
	 */
	void setHttpStatusCode(int statusCode) {
		m_httpStatusCode = statusCode;
	}

	/**
	 * 이 오류를 발생시킨 HTTP 응답의 본문을 반환한다.
	 * <p>
	 * 에러 엔티티로 변환하면서 버려지는 필드(예: {@code error.details})나 형식을 알 수 없는 본문을 호출부가
	 * 직접 읽을 때 쓴다.
	 *
	 * @return 응답 본문. HTTP 응답에서 비롯되지 않은 오류이거나 본문이 없으면 {@code null}.
	 */
	public @Nullable String getResponseBody() {
		return m_responseBody;
	}

	/**
	 * 이 오류를 발생시킨 HTTP 응답의 헤더 값(첫 번째)을 반환한다. (예: {@code Retry-After})
	 *
	 * @param name	헤더 이름. 대소문자를 구분하지 않는다.
	 * @return 헤더 값. 없거나 HTTP 응답에서 비롯되지 않은 오류이면 {@code null}.
	 */
	public @Nullable String getResponseHeader(String name) {
		List<String> values = m_responseHeaders.get(name);
		return (values == null || values.isEmpty()) ? null : values.get(0);
	}

	/**
	 * 이 오류를 발생시킨 HTTP 응답의 본문과 헤더를 설정한다. {@link HttpRESTfulClient}가 상태 코드와 함께 설정한다.
	 */
	void setHttpResponse(@Nullable String body, Map<String,List<String>> headers) {
		m_responseBody = body;
		Map<String,List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		headers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
		m_responseHeaders = copy;
	}

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
