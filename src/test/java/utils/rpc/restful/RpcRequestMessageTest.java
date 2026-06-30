package utils.rpc.restful;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.json.JacksonUtils;
import utils.rpc.restful.RpcRequestMessage;


/**
 * {@link RpcRequestMessage} DTO 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RpcRequestMessageTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	@Test
	public void testNullInputsRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new RpcRequestMessage(null));
	}

	@Test
	public void testGetters() throws IOException {
		Map<String,JsonNode> inputs = Map.of("x", json("1"));
		RpcRequestMessage msg = new RpcRequestMessage(inputs);

		Assertions.assertEquals(inputs, msg.getInputs());
	}

	@Test
	public void testInputsDefensivelyCopied() throws IOException {
		Map<String,JsonNode> inputs = new HashMap<>();
		inputs.put("x", json("1"));
		RpcRequestMessage msg = new RpcRequestMessage(inputs);

		// 생성 후 원본 맵을 변경해도 메시지 내부에는 영향이 없어야 한다.
		inputs.put("y", json("2"));

		Assertions.assertEquals(Map.of("x", json("1")), msg.getInputs());
	}

	@Test
	public void testJsonRoundTrip() throws IOException {
		Map<String,JsonNode> inputs = Map.of("x", json("1"), "name", json("\"abc\""));
		RpcRequestMessage msg = new RpcRequestMessage(inputs);

		String serialized = MAPPER.writeValueAsString(msg);
		RpcRequestMessage restored = MAPPER.readValue(serialized, RpcRequestMessage.class);

		Assertions.assertEquals(inputs, restored.getInputs());
	}

	@Test
	public void testExtraFieldsPreservedOnDeserialize() throws IOException {
		// inputs 이외의 필드(startTimeout, async)도 추가 필드로 보존되어야 한다.
		String json = "{\"inputs\":{\"x\":1},\"startTimeout\":\"5s\",\"async\":true}";
		RpcRequestMessage msg = MAPPER.readValue(json, RpcRequestMessage.class);

		Assertions.assertEquals(Map.of("x", json("1")), msg.getInputs());
		Assertions.assertEquals(json("\"5s\""), msg.getExtraFields().get("startTimeout"));
		Assertions.assertEquals(json("true"), msg.getExtraFields().get("async"));
	}

	@Test
	public void testNoExtraFieldsByDefault() throws IOException {
		RpcRequestMessage msg = new RpcRequestMessage(Map.of("x", json("1")));
		Assertions.assertTrue(msg.getExtraFields().isEmpty());
	}

	@Test
	public void testExtraFieldsRoundTrip() throws IOException {
		// 추가 필드는 직렬화 시 다시 최상위 필드로 복원되어 왕복이 보존되어야 한다.
		String json = "{\"inputs\":{\"x\":1},\"startTimeout\":\"5s\"}";
		RpcRequestMessage msg = MAPPER.readValue(json, RpcRequestMessage.class);

		String serialized = MAPPER.writeValueAsString(msg);
		RpcRequestMessage restored = MAPPER.readValue(serialized, RpcRequestMessage.class);

		Assertions.assertEquals(Map.of("x", json("1")), restored.getInputs());
		Assertions.assertEquals(json("\"5s\""), restored.getExtraFields().get("startTimeout"));
	}
}
