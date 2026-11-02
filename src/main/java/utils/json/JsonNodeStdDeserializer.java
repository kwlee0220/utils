package utils.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import utils.Preconditions;


/**
 * {@link JsonNodeDeserializer}를 Jackson의 {@link StdDeserializer}로 감싸는 어댑터.
 * <p>
 * 입력을 {@link JsonNode}로 읽은 뒤 주어진 {@link JsonNodeDeserializer}에 넘긴다.
 * <p>
 * 모듈에 등록할 때는 직접 만들어 넘긴다.
 * <pre>{@code
 * new SimpleModule().addDeserializer(Shape.class, new JsonNodeStdDeserializer<>(Shape.class, DESERIALIZER));
 * }</pre>
 * {@code @JsonDeserialize(using = X.class)}는 인자 없는 생성자로 {@code X}를 만들기 때문에, 어노테이션에 쓸 때는
 * 대상 타입마다 짧은 하위 클래스를 만든다.
 * <pre>{@code
 * @SuppressWarnings("serial")
 * public static class Deserializer extends JsonNodeStdDeserializer<ElementReference> {
 *     public Deserializer() {
 *         super(ElementReference.class, DESERIALIZER);
 *     }
 * }
 * }</pre>
 * JSON {@code null}은 Jackson이 {@link #getNullValue(DeserializationContext)}로 처리하므로
 * {@link JsonNodeDeserializer}에 전달되지 않는다.
 * <p>
 * 노드는 현재 역직렬화 컨텍스트로 읽는다({@link DeserializationContext#readTree(JsonParser)}).
 *
 * @param <T>	역직렬화 대상 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@SuppressWarnings("serial")
public class JsonNodeStdDeserializer<T> extends StdDeserializer<T> {
	private final JsonNodeDeserializer<? extends T> m_deserializer;

	/**
	 * 어댑터를 생성한다.
	 *
	 * @param handledType	역직렬화 대상 타입. Jackson 오류 메시지에 사용된다.
	 * @param deserializer	{@link JsonNode}를 대상 객체로 바꾸는 함수.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public JsonNodeStdDeserializer(Class<T> handledType, JsonNodeDeserializer<? extends T> deserializer) {
		super(handledType);

		Preconditions.checkNotNullArgument(handledType, "handledType is null");
		Preconditions.checkNotNullArgument(deserializer, "deserializer is null");
		m_deserializer = deserializer;
	}

	@Override
	public T deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		JsonNode jnode = ctxt.readTree(parser);
		return m_deserializer.deserialize(jnode);
	}
}
