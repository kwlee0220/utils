package utils.rpc.restful;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import utils.Preconditions;
import utils.stream.FStream;

/**
 * 원격 연산(operation) 호출 요청 메시지를 표현하는 DTO.
 * <p>
 * 연산에 전달할 입력 인자들을 {@code inputs} 필드(입력 이름 → 값)로 담으며, 출력 변수의 위치 등을
 * 지정해야 하는 연산을 위해 선택적으로 {@code outputs} 필드(출력 이름 → 값)도 담을 수 있다.
 * {@code inputs}/{@code outputs} 이외의 필드는 {@link #getExtraFields() 추가 필드}로 보존되어
 * JSON 왕복(round-trip) 시 원래 위치(최상위)로 복원된다({@link JsonAnySetter}/{@link JsonAnyGetter}).
 * JSON 직렬화 시 {@code null} 필드는 생략된다({@link JsonInclude}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcRequestMessage {
	@JsonProperty("inputs") private final Map<String,JsonNode> m_inputs;
	@JsonProperty("outputs") private final Map<String,JsonNode> m_outputs;
	@JsonIgnore private final Map<String,JsonNode> m_extraFields = new HashMap<>();

	/**
	 * 요청 메시지를 생성한다.
	 *
	 * @param inputs 연산 입력 인자. (입력 이름 → 값, {@code null} 불가) 방어적 복사되어 보관된다.
	 * @param outputs 연산 출력 지정. (출력 이름 → 값, {@code null} 허용) {@code null}이 아니면 방어적
	 * 					복사되어 보관된다.
	 * @throws IllegalArgumentException {@code inputs}가 {@code null}인 경우.
	 */
	@JsonCreator
	public RpcRequestMessage(@JsonProperty("inputs") Map<String,JsonNode> inputs,
								@JsonProperty("outputs") @Nullable Map<String,JsonNode> outputs) {
		Preconditions.checkNotNullArgument(inputs, "'inputs' is null");

		m_inputs = new HashMap<>(inputs);
		m_outputs = outputs != null ? new HashMap<>(outputs) : null;
	}
	
	/**
	 * 출력 인자 없이 요청 메시지를 생성한다.
	 *
	 * @param inputs 연산 입력 인자. (입력 이름 → 값, {@code null} 불가) 방어적 복사되어 보관된다.
	 * @throws IllegalArgumentException {@code inputs}가 {@code null}인 경우.
	 */
	public RpcRequestMessage(Map<String,JsonNode> inputs) {
		this(inputs, null);
	}

	/**
	 * 연산 입력 인자를 반환한다.
	 *
	 * @return 연산 입력 인자. (입력 이름 → 값)
	 */
	public Map<String,JsonNode> getInputs() {
		return m_inputs;
	}

	/**
	 * 연산 출력 지정을 반환한다.
	 *
	 * @return 연산 출력 지정. (출력 이름 → 값) 지정되지 않았으면 {@code null}.
	 */
	public Map<String,JsonNode> getOutputs() {
		return m_outputs;
	}

	/**
	 * {@code inputs} 이외의 추가 필드를 반환한다.
	 * <p>
	 * 역직렬화 시 {@code inputs}로 바인딩되지 않은 최상위 필드들이 여기에 모이며, 직렬화 시 다시 최상위
	 * 필드로 펼쳐진다.
	 *
	 * @return 추가 필드. (필드 이름 → 값)
	 */
	@JsonAnyGetter
	public Map<String,JsonNode> getExtraFields() {
		return m_extraFields;
	}

	/**
	 * {@code inputs} 이외의 추가 필드를 등록한다. (Jackson 역직렬화 전용)
	 *
	 * @param name	필드 이름.
	 * @param value	필드 값.
	 */
	@JsonAnySetter
	public void putExtraField(String name, JsonNode value) {
		m_extraFields.put(name, value);
	}

	@Override
	public String toString() {
		String inputNames = FStream.from(m_inputs.keySet()).join(", ");

		return inputNames;
	}
}
