package utils.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import utils.InternalException;
import utils.func.Optionals;

/**
 * RESTful 서버의 구조화된 에러 응답을 표현하는 DTO.
 * <p>
 * JSON 형태는 {@code code}와 {@code message} 필드로 구성된다. {@code code}는 일반적으로
 * 서버에서 발생한 {@link Throwable} 구현 클래스의 fully-qualified class name이 담기지만,
 * 클라이언트는 보안상(임의 클래스 로딩 회피) 이를 이용해 예외를 복원하지 않고, {@code code}/{@code message}를
 * 보유한 {@link RESTfulRemoteException}으로 변환한다({@link #toException()} 참조).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public final class RESTfulErrorEntity {
	private final String m_code;
	private final String m_message;
	
	@JsonCreator
	private RESTfulErrorEntity(@JsonProperty("code") String code, @JsonProperty("message") String message) {
		m_code = code;
		m_message = message;
	}

	/**
	 * 에러 코드 또는 예외 클래스 이름을 반환한다.
	 *
	 * @return 에러 코드. 값이 없으면 {@code null}.
	 */
	public String getCode() {
		return m_code;
	}

	/**
	 * 에러 상세 메시지를 반환한다.
	 *
	 * @return 에러 메시지. 값이 없으면 {@code null}.
	 */
	public String getMessage() {
		return m_message;
	}

	/**
	 * 메시지와 원인 예외로부터 에러 엔티티를 생성한다.
	 * <p>
	 * {@code code}에는 원인 예외의 클래스 이름이, {@code message}에는 주어진 메시지와 원인 예외의
	 * 메시지를 합친 문자열이 저장된다.
	 *
	 * @param msg   에러 메시지.
	 * @param cause 원인 예외.
	 * @return 생성된 에러 엔티티.
	 */
	public static RESTfulErrorEntity of(String msg, Throwable cause) {
		String code = cause.getClass().getName();
		String details = String.format("%s, cause=%s", msg, cause.getLocalizedMessage());
		return new RESTfulErrorEntity(code, details);
	}

	/**
	 * 예외 객체로부터 에러 엔티티를 생성한다.
	 * <p>
	 * {@code code}에는 예외 클래스 이름이, {@code message}에는 예외 메시지가 저장된다.
	 *
	 * @param e 변환할 예외.
	 * @return 생성된 에러 엔티티.
	 */
	public static RESTfulErrorEntity of(Throwable e) {
		String msg = Optionals.getOrElse(e.getMessage(), "");
		return new RESTfulErrorEntity(e.getClass().getName(), msg);
	}

	/**
	 * 메시지만 가진 에러 엔티티를 생성한다.
	 *
	 * @param msg 에러 메시지.
	 * @return 생성된 에러 엔티티.
	 */
	public static RESTfulErrorEntity ofMessage(String msg) {
		return new RESTfulErrorEntity(null, msg);
	}

	/**
	 * 이 에러 엔티티를 클라이언트 측 예외 객체로 변환하여 반환한다.
	 * <p>
	 * 원격 응답의 {@code code}로 임의 클래스를 로딩·복원하지 않고, 이 엔티티를 보유한
	 * {@link RESTfulRemoteException}을 반환한다. 예외를 던지지 않는다.
	 *
	 * @return 변환된 예외.
	 */
	public RESTfulRemoteException toException() {
		return new RESTfulRemoteException(this);
	}

	/**
	 * 에러 엔티티를 사람이 읽을 수 있는 문자열로 변환한다.
	 *
	 * @return {@code code}와 {@code message}를 조합한 문자열.
	 * @throws InternalException {@code code}와 {@code message}가 모두 없는 비정상 엔티티인 경우.
	 */
	@Override
	public String toString() {
		if ( m_code != null && m_message != null ) {
			return String.format("%s (%s)", m_code, m_message);
		}
		else if ( m_message != null ) {
			return m_message;
		}
		else if ( m_code != null ) {
			return m_code;
		}
		else {
			throw new InternalException("Both code and message are null");
		}
	}
}
