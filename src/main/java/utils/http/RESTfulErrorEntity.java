package utils.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import utils.InternalException;
import utils.ReflectionUtils;
import utils.Throwables;
import utils.func.Optionals;

/**
 * RESTful 서버의 구조화된 에러 응답을 표현하는 DTO.
 * <p>
 * JSON 형태는 {@code code}와 {@code message} 필드로 구성된다. {@code code}는 일반적으로
 * 서버에서 발생한 {@link Throwable} 구현 클래스의 fully-qualified class name이 담기지만,
 * 클라이언트는 보안상(임의 클래스 로딩 회피) 이를 이용해 예외를 복원하지 않고, {@code code}/{@code message}를
 * 보유한 {@link RESTfulRemoteException}으로 변환한다({@link #toRemoteException()} 참조).
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
	public RESTfulRemoteException toRemoteException() {
		return new RESTfulRemoteException(this);
	}
	
	/**
	 * {@code code}를 예외 클래스 이름으로 간주하여 원격 예외를 클라이언트 측에서 복원한다.
	 * <p>
	 * {@code code}가 가리키는 클래스를 로드하여 다음과 같이 인스턴스를 생성한다.
	 * <ul>
	 *   <li>{@code message}가 있으면 {@code String} 인자 하나를 받는 생성자에 {@code message}를 전달한다.</li>
	 *   <li>{@code message}가 {@code null}이면 no-arg 생성자를 사용한다.</li>
	 * </ul>
	 * 따라서 {@code code}는 {@link RuntimeException}의 하위 타입이면서 해당 생성자를 갖는
	 * 클래스 이름이어야 한다.
	 * <p>
	 * 복원된 예외는 <b>던져지지 않고 반환</b>되며, 던지는 것은 호출자의 몫이다. 반면 복원에
	 * <b>실패한 경우에는 반환 대신 예외가 던져진다</b>. 실패 원인은
	 * {@link Throwables#unwrapThrowable(Throwable)}로 감싸진 계층을 벗겨낸 뒤
	 * {@link Throwables#toRuntimeException(Throwable)}로 변환되어 전파된다.
	 * 대표적인 실패 사유는 다음과 같다.
	 * <ul>
	 *   <li>{@code code}가 {@code null}이거나 해당 클래스를 찾을 수 없는 경우.</li>
	 *   <li>로드된 클래스가 {@link RuntimeException}의 하위 타입이 아닌 경우.</li>
	 *   <li>필요한 생성자가 없거나 그 생성자 본문에서 예외가 발생한 경우
	 *       (이 경우 생성자가 던진 예외 자체가 전파된다).</li>
	 * </ul>
	 * <p>
	 * <b>주의:</b> 이 메소드는 원격 응답이 지정한 클래스 이름을 실제로 로딩하므로,
	 * 신뢰할 수 있는 서버의 응답에 대해서만 사용해야 한다. 임의 클래스 로딩을 피하려면
	 * {@link #toRemoteException()}을 사용한다.
	 *
	 * @return {@code code}로부터 복원된 예외 객체.
	 * @throws RuntimeException 예외 복원에 실패한 경우.
	 * @throws Error 복원 실패의 원인이 {@link Error}인 경우 그대로 전파된다.
	 * @see #toRemoteException()
	 */
	public RuntimeException toClientException() {
		try {
			return m_message != null
						? ReflectionUtils.newInstance(m_code, RuntimeException.class, m_message)
						: ReflectionUtils.newInstance(m_code, RuntimeException.class);
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw Throwables.toRuntimeException(cause);
		}
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
