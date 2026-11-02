package utils.json;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 객체와 {@link JsonNode} 사이를 양방향으로 변환한다.
 * <p>
 * 역방향({@link #deserialize(JsonNode)})은 {@link JsonNodeDeserializer}에서 물려받으므로, 이 타입의 객체는
 * {@link JsonNodeDeserializer}를 받는 곳(예: {@link MultiTypeJsonNodeDeserializer}의 등록)에 그대로 넘길 수 있다.
 * <p>
 * {@code null} 계약: 역방향은 {@link JsonNodeDeserializer}의 계약을 따른다. {@link #serialize(Object)}는 Java
 * {@code null} 객체를 받지 않으며(구현은 {@code IllegalArgumentException}을 권장하고 {@code IOException}도 허용),
 * Java {@code null}을 돌려주지 않는다. JSON {@code null}로 나타내야 하는 값이면 {@code NullNode}를 돌려준다.
 *
 * @param <T>	변환할 객체의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface JsonNodeSerDe<T> extends JsonNodeDeserializer<T> {
	/**
	 * 주어진 객체를 JSON 노드로 변환한다.
	 *
	 * @param obj	변환할 객체. {@code null}이 아니다.
	 * @return	변환된 JSON 노드. {@code null}이 아니다(JSON {@code null}은 {@code NullNode}).
	 * @throws IOException	변환에 실패한 경우.
	 */
	public JsonNode serialize(T obj) throws IOException;
}
