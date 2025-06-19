package utils.http;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({"messageType", "text", "code", "timestamp"})
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpringExceptionEntity {
	@JsonProperty("messageType") 
	private MessageTypeEnum m_messageType;
	@Nullable @JsonProperty("code")
	private String m_code;
	@Nullable @JsonProperty("text") 
	private String m_text;
	@Nullable @JsonProperty("timestamp")
	private String m_timestamp;
	
	public enum MessageTypeEnum { Info, Warning, Error, Exception }
	
	public static SpringExceptionEntity from(Throwable e) {
		SpringExceptionEntity entity = new SpringExceptionEntity();
		entity.m_messageType = MessageTypeEnum.Exception;
		entity.m_text = e.getMessage();
		entity.m_code = e.getClass().getName();

		ZonedDateTime zdt = Instant.now().atZone(ZoneOffset.systemDefault());
		entity.m_timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(zdt);
		
		return entity;
	}
	
	public SpringExceptionEntity() { }
	public SpringExceptionEntity(String code, String text) {
		m_messageType = MessageTypeEnum.Exception;
		m_code = code;
		m_text = text;
		
		ZonedDateTime zdt = Instant.now().atZone(ZoneOffset.systemDefault());
		m_timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(zdt);
	}

	@JsonProperty("messageType") 
	public MessageTypeEnum getMessageType() {
		return m_messageType;
	}
	@JsonProperty("messageType")
	public void setMessageType(MessageTypeEnum messageType) {
		m_messageType = messageType;
	}
	
	public String getCode() {
		return m_code;
	}
	public void setCode(String code) {
		m_code = code;
	}
	
	public String getText() {
		return m_text;
	}
	public void setText(String text) {
		m_text = text;
	}
	
	@Nullable @JsonProperty("timestamp")
	public String getTimestamp() {
		return m_timestamp;
	}
	@JsonProperty("timestamp")
	public void setTimestamp(@Nullable String ts) {
		m_timestamp = ts;
	}
	
	public RESTfulRemoteException toClientException() {
		if ( m_text != null ) {
			throw new RESTfulRemoteException("code=" + m_code + ", details=" + m_text);
		}
		else {
			throw new RESTfulRemoteException("code=" + m_code);
		}
	}
	
	public Throwable toException() {
		Class<? extends Throwable> cls = loadThrowableClass();
		try {
			if ( m_text != null ) {
				Constructor<? extends Throwable> ctor = getSingleStringContructor(cls);
				if ( ctor != null ) {
					return ctor.newInstance(m_text);
				}
				else {
					return new RESTfulRemoteException(m_text);
				}
			}
			else {
				Constructor<? extends Throwable> ctor = getNoArgContructor(cls);
				if ( ctor != null ) {
					return ctor.newInstance();
				}
				else {
					return new RESTfulRemoteException("code=" + m_code);
				}
			}
		}
		catch ( Exception e ) {
			if ( m_text != null ) {
				throw new RESTfulRemoteException("code=" + m_code + ", details=" + m_text);
			}
			else {
				throw new RESTfulRemoteException("code=" + m_code);
			}
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
			return RESTfulRemoteException.class;
		}
	}
}
