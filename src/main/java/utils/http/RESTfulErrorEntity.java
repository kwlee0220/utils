package utils.http;

import java.lang.reflect.Constructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import utils.InternalException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public class RESTfulErrorEntity {
	public static final TypeReference<RESTfulErrorEntity> TYPE_REF = new TypeReference<RESTfulErrorEntity>(){};
	
	private final String m_code;
	private final String m_message;
	
	@JsonCreator
	private RESTfulErrorEntity(@JsonProperty("code") String code, @JsonProperty("message") String message) {
		m_code = code;
		m_message = message;
	}
	
	public String getCode() {
		return m_code;
	}
	
	public String getMessage() {
		return m_message;
	}
	
	public static RESTfulErrorEntity of(String msg, Throwable cause) {
		return ofMessage(String.format("%s, cause=%s", msg, cause));
	}
	public static RESTfulErrorEntity of(Throwable e) {
		return new RESTfulErrorEntity(e.getClass().getName(), e.getMessage());
	}
	public static RESTfulErrorEntity ofMessage(String msg) {
		return new RESTfulErrorEntity(null, msg);
	}
	
	public Throwable toException() {
		return toJavaException();
	}
	
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
	
	@Override
	public String toString() {
		if ( m_code != null && m_message != null ) {
			return String.format("%s (%s)", m_code, m_message);
		}
		else if ( m_message != null ) {
			return m_message;
		}
		else if ( m_code != null ) {
			return m_message;
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
