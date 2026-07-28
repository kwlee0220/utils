package utils.http;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.jetbrains.annotations.Nullable;


/**
 * 메시지 유형({@link MessageTypeEnum})으로 분류되는 서버 에러 메시지를 표현하는 커스텀 포맷 DTO.
 * <p>
 * JSON 형태는 {@code messageType}, {@code code}, {@code text}, {@code timestamp} 필드로 구성된다.
 * Spring Boot 기본 에러 응답({@code timestamp}/{@code status}/{@code error}/{@code message}/{@code path})은
 * 이 클래스가 아니라 {@link SpringBootErrorResponse}가 표현한다.
 * <p>
 * {@code code}에는 일반적으로 서버 예외 클래스 이름이 담기지만, 클라이언트는 보안상(임의 클래스 로딩 회피)
 * 이를 이용해 예외를 복원하지 않고 {@code code}/{@code text}를 담은 {@link RESTfulRemoteException}으로
 * 변환한다({@link #toException()} 참조).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({"messageType", "text", "code", "timestamp"})
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypedServerErrorMessage {
	@JsonProperty("messageType")
	private MessageTypeEnum m_messageType;
	@Nullable @JsonProperty("code")
	private String m_code;
	@Nullable @JsonProperty("text")
	private String m_text;
	@Nullable @JsonProperty("timestamp")
	private String m_timestamp;

	/** 메시지 유형. */
	public enum MessageTypeEnum { Info, Warning, Error, Exception }

	/**
	 * 예외 객체로부터 {@code Exception} 유형의 메시지를 생성한다.
	 *
	 * @param e 변환할 예외.
	 * @return 생성된 메시지.
	 */
	public static TypedServerErrorMessage from(Throwable e) {
		TypedServerErrorMessage entity = new TypedServerErrorMessage();
		entity.m_messageType = MessageTypeEnum.Exception;
		entity.m_text = e.getMessage();
		entity.m_code = e.getClass().getName();

		ZonedDateTime zdt = Instant.now().atZone(ZoneOffset.systemDefault());
		entity.m_timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(zdt);

		return entity;
	}

	public TypedServerErrorMessage() { }
	public TypedServerErrorMessage(String code, String text) {
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

	/**
	 * 이 에러 메시지를 클라이언트 측 예외 객체로 변환하여 반환한다.
	 * <p>
	 * 원격 응답의 {@code code}로 임의 클래스를 로딩·복원하지 않고, {@code code}/{@code text}를 메시지에
	 * 담은 {@link RESTfulRemoteException}을 반환한다. ({@link RESTfulErrorEntity#toRemoteException()}과 동일한
	 * 정책 — 임의 클래스 로딩 회피, 항상 예외를 '반환') 어떤 경우에도 예외를 던지지 않는다.
	 *
	 * @return 변환된 예외.
	 */
	public Throwable toException() {
		if ( m_text != null ) {
			return new RESTfulRemoteException("code=" + m_code + ", details=" + m_text);
		}
		else {
			return new RESTfulRemoteException("code=" + m_code);
		}
	}
}
