package utils.json;

import java.io.Closeable;

import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * JSON 노드를 객체로 바꾸는 중에 필드가 없거나, 타입이 맞지 않거나, 등록되지 않은 타입이어서 실패했음을 알린다.
 * <p>
 * {@link java.io.IOException}의 하위 타입인 Jackson {@link JsonMappingException}을 상속한 검사 예외다.
 * 그래서 Jackson deserializer 안에서 던져도 {@code ObjectMapper.readValue}·{@code treeToValue}가 다른 예외로
 * 감싸지 않고 그대로 전달한다(일반 {@code IOException}은 {@code treeToValue}가 {@code IllegalArgumentException}으로
 * 감싼다). 원인 예외가 있으면 {@link #getCause()}로 얻는다.
 * <p>
 * 2026-11-01 이전에는 {@code RuntimeException}을 상속한 비검사 예외였고, 필수 필드 누락은 일반
 * {@code IOException}으로 따로 보고했다. 또 {@link #getMessage()}를 재정의해 원인을 메시지에 다시 붙였으므로,
 * 원인만 받는 생성자에서는 원인이 메시지에 두 번 나왔다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@SuppressWarnings("serial")
public class JacksonDeserializationException extends JsonMappingException {
	public JacksonDeserializationException(String details) {
		super((Closeable)null, details);
	}

	public JacksonDeserializationException(Throwable cause) {
		super((Closeable)null, String.valueOf(cause), cause);
	}

	public JacksonDeserializationException(String details, Throwable cause) {
		super((Closeable)null, details, cause);
	}
}
