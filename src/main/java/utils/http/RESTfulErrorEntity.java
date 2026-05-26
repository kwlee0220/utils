package utils.http;

import java.lang.reflect.Constructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import utils.InternalException;
import utils.func.Optionals;

/**
 * RESTful 서버의 구조화된 에러 응답을 표현하는 DTO.
 * <p>
 * JSON 형태는 {@code code}와 {@code message} 필드로 구성된다. {@code code}는 일반적으로
 * 서버에서 발생한 {@link Throwable} 구현 클래스의 fully-qualified class name으로 사용되며,
 * 클라이언트는 이를 바탕으로 가능한 경우 같은 타입의 예외 인스턴스를 복원한다.
 * {@code code}가 없고 {@code message}만 있는 경우에는 원격 서버가 반환한 일반 오류 메시지로
 * 간주하여 {@link RESTfulRemoteException}으로 변환된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public class RESTfulErrorEntity {
	/**
	 * Jackson에서 {@code RESTfulErrorEntity} 타입 정보를 보존해 deserialize할 때 사용하는
	 * {@link TypeReference}.
	 */
	public static final TypeReference<RESTfulErrorEntity> TYPE_REF = new TypeReference<RESTfulErrorEntity>(){};
	
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
	 * 주어진 메시지와 원인 예외 정보를 하나의 메시지 문자열로 합쳐 에러 엔티티를 생성한다.
	 * <p>
	 * 이 factory는 {@code code}를 설정하지 않으므로 {@link #toException()} 호출 시
	 * {@link RESTfulRemoteException}으로 변환된다.
	 *
	 * @param msg   에러 메시지.
	 * @param cause 원인 예외.
	 * @return 생성된 에러 엔티티.
	 */
	public static RESTfulErrorEntity of(String msg, Throwable cause) {
		return ofMessage(String.format("%s, cause=%s", msg, cause));
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
	 * 이 에러 엔티티를 클라이언트 측 예외 객체로 변환한다.
	 *
	 * @return 변환된 예외.
	 * @throws RESTfulRemoteException {@code code}가 없고 {@code message}만 있는 경우.
	 * @throws RESTfulIOException     예외 클래스 복원에 실패하거나 대체 예외가 필요한 경우.
	 */
	public Throwable toException() {
		return toJavaException();
	}

	/**
	 * {@code code}에 기록된 예외 클래스와 {@code message}를 이용해 Java 예외 객체를 복원한다.
	 * <p>
	 * {@code code} 클래스에 {@code String} 생성자 또는 기본 생성자가 있으면 이를 사용한다.
	 * 해당 클래스를 찾을 수 없거나 적절한 생성자가 없으면 {@link RESTfulIOException} 또는
	 * {@link RESTfulRemoteException}으로 대체한다.
	 *
	 * @return 복원되거나 대체된 예외.
	 * @throws RESTfulRemoteException {@code code}가 없고 {@code message}만 있는 경우.
	 * @throws RESTfulIOException     예외 인스턴스 생성 중 오류가 발생한 경우.
	 */
	public Throwable toJavaException() {
		if ( m_code == null ) {
			throw new RESTfulRemoteException(m_message);
		}
		
		Class<? extends Throwable> cls = loadThrowableClass();
		try {
			if ( m_message != null ) {
				Constructor<? extends Throwable> ctor = getSingleStringContructor(cls);
				if ( ctor != null ) {
					return ctor.newInstance(m_message);
				}
				else {
					return new RESTfulIOException(m_message);
				}
			}
			else {
				Constructor<? extends Throwable> ctor = getNoArgContructor(cls);
				if ( ctor != null ) {
					return ctor.newInstance();
				}
				else {
					return new RESTfulIOException("code=" + m_code);
				}
			}
		}
		catch ( Exception e ) {
			if ( m_message != null ) {
				throw new RESTfulIOException("code=" + m_code + ", details=" + m_message);
			}
			else {
				throw new RESTfulIOException("code=" + m_code);
			}
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
	
	private Constructor<? extends Throwable>
	getNoArgContructor(Class<? extends Throwable> cls) {
		try {
			return cls.getDeclaredConstructor();
		}
		catch ( Throwable e ) {
			return null;
		}
	}
	
	private Constructor<? extends Throwable>
	getSingleStringContructor(Class<? extends Throwable> cls) {
		try {
			return cls.getDeclaredConstructor(String.class);
		}
		catch ( Throwable e ) {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private Class<? extends Throwable> loadThrowableClass() {
		try {
			return (Class<? extends Throwable>)Class.forName(m_code);
		}
		catch ( ClassNotFoundException e ) {
			return RESTfulIOException.class;
		}
	}
}
