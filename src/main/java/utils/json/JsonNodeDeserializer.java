package utils.json;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@link JsonNode}를 객체로 바꾸는 함수.
 * <p>
 * {@link MultiTypeJsonNodeDeserializer}에 타입 이름별로 등록할 때는 타입 이름 필드(예: {@code @type})를 포함한
 * 노드 전체를 받는다. 보통 구체 타입의 정적 메소드를 메소드 참조로 등록한다
 * (예: {@code ReferenceArgument::deserializeFields}). 양방향 변환이 필요하면 이 인터페이스를 상속한
 * {@link JsonNodeSerDe}를 구현한다.
 * <p>
 * <b>{@code null} 계약</b>
 * <ul>
 *   <li>Java {@code null}: 호출자는 넘기지 않는다. 구현은 이를 거부하며, {@code IllegalArgumentException}을
 *       권장하고 {@code IOException}도 허용한다.</li>
 *   <li>JSON {@code null}({@code NullNode}): 받아들일지는 구현이 정하고 그 구현의 문서에 적는다. 거부하는 것을
 *       권장한다. {@link MultiTypeJsonNodeDeserializer}는 타입 이름 필드가 없는 것으로 보고 거부한다.
 *       {@link JsonNodeStdDeserializer}를 거쳐 Jackson이 부를 때는 JSON {@code null}이 이 함수까지 오지 않는다.</li>
 *   <li>반환값: 받아들인 입력에 대해 Java {@code null}을 돌려주지 않는다. 다만 JSON {@code null}을 받아들이는
 *       구현은 그 입력에 대해 {@code null}을 돌려줄 수 있다.</li>
 * </ul>
 *
 * @param <T>	만드는 객체의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface JsonNodeDeserializer<T> {
	/**
	 * 주어진 노드를 객체로 바꾼다.
	 *
	 * @param node	변환할 JSON 노드. {@code null}이 아니다.
	 * @return	만든 객체. 받아들인 JSON {@code null} 입력이 아니면 {@code null}이 아니다.
	 * @throws IOException	필드가 없거나 값이 잘못되어 객체를 만들 수 없는 경우.
	 */
	public T deserialize(JsonNode node) throws IOException;
}
