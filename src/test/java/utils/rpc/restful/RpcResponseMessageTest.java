package utils.rpc.restful;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.json.JacksonUtils;
import utils.rpc.restful.RpcResponseMessage;
import utils.rpc.restful.RpcState;


/**
 * {@link RpcResponseMessage} DTO 및 팩토리/빌더 검증 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RpcResponseMessageTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	// ----- 팩토리 메소드 -----

	@Test
	public void testRunning() {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");

		Assertions.assertEquals(RpcState.RUNNING, resp.getState());
		Assertions.assertEquals("/sessions/1", resp.getSessionEndpoint());
		Assertions.assertNull(resp.getError());
	}

	@Test
	public void testCompleted() throws IOException {
		Map<String,JsonNode> outputs = Map.of("r", json("7"));
		RpcResponseMessage resp = RpcResponseMessage.completed("/sessions/1", outputs);

		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(outputs, resp.getOutputs());
	}

	@Test
	public void testFailed() {
		RpcResponseMessage resp = RpcResponseMessage.failed("/sessions/1",
																		new IOException("disk full"));

		Assertions.assertEquals(RpcState.FAILED, resp.getState());
		Assertions.assertNotNull(resp.getError());

		Throwable restored = resp.getJavaException();
		Assertions.assertNotNull(restored);
	}

	@Test
	public void testCancelled() {
		RpcResponseMessage resp = RpcResponseMessage.cancelled("/sessions/1");

		Assertions.assertEquals(RpcState.CANCELLED, resp.getState());
	}

	// ----- 상태/필드 일관성 검증 -----

	@Test
	public void testNullStatusRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new RpcResponseMessage(null, null, null, null));
	}

	@Test
	public void testCompletedWithoutOutputsRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new RpcResponseMessage(null, RpcState.COMPLETED, null, null));
	}

	@Test
	public void testFailedWithoutErrorRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new RpcResponseMessage(null, RpcState.FAILED, null, null));
	}

	@Test
	public void testGetJavaExceptionRejectsNonFailed() {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");
		Assertions.assertThrows(IllegalStateException.class, resp::getJavaException);
	}

	// ----- JSON 직렬화/역직렬화 -----

	@Test
	public void testCompletedJsonRoundTrip() throws IOException {
		Map<String,JsonNode> outputs = Map.of("r", json("{\"v\":7}"));
		RpcResponseMessage resp = RpcResponseMessage.completed("/sessions/1", outputs);

		String serialized = MAPPER.writeValueAsString(resp);
		RpcResponseMessage restored = MAPPER.readValue(serialized, RpcResponseMessage.class);

		Assertions.assertEquals(RpcState.COMPLETED, restored.getState());
		Assertions.assertEquals("/sessions/1", restored.getSessionEndpoint());
		Assertions.assertEquals(outputs, restored.getOutputs());
	}

	@Test
	public void testOutputsNeverNull() {
		// COMPLETED가 아닌 상태에서도 outputs는 null이 아니라 빈 Map이다.
		Assertions.assertEquals(Map.of(), RpcResponseMessage.running("/sessions/1").getOutputs());
		Assertions.assertEquals(Map.of(), RpcResponseMessage.cancelled("/sessions/1").getOutputs());
		Assertions.assertEquals(Map.of(),
								RpcResponseMessage.failed("/sessions/1", new IOException("boom"))
													.getOutputs());
	}

	@Test
	public void testOutputsNeverNullWhenDeserialized() throws IOException {
		// JSON에 outputs 키가 아예 없는 경우에도 마찬가지다.
		String json = "{\"session_endpoint\":\"/sessions/1\",\"status\":\"RUNNING\"}";
		RpcResponseMessage restored = MAPPER.readValue(json, RpcResponseMessage.class);

		Assertions.assertEquals(Map.of(), restored.getOutputs());
	}

	@Test
	public void testOutputsAlwaysSerialized() {
		// outputs는 상태와 무관하게 항상 직렬화된다. (빈 경우는 빈 객체)
		JsonNode running = MAPPER.valueToTree(RpcResponseMessage.running("/sessions/1"));
		Assertions.assertTrue(running.path("outputs").isObject());
		Assertions.assertEquals(0, running.path("outputs").size());

		JsonNode cancelled = MAPPER.valueToTree(RpcResponseMessage.cancelled("/sessions/1"));
		Assertions.assertTrue(cancelled.path("outputs").isObject());
		Assertions.assertEquals(0, cancelled.path("outputs").size());
	}

	@Test
	public void testStatusFieldName() throws IOException {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");

		JsonNode serialized = MAPPER.valueToTree(resp);
		Assertions.assertEquals("RUNNING", serialized.path("status").asText());
		Assertions.assertTrue(serialized.path("state").isMissingNode());
	}

	@Test
	public void testSessionEndpointFieldName() throws IOException {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");

		JsonNode serialized = MAPPER.valueToTree(resp);
		Assertions.assertEquals("/sessions/1", serialized.path("session_endpoint").asText());
		Assertions.assertTrue(serialized.path("sessionEndpoint").isMissingNode());
	}

	@Test
	public void testLegacyStateFieldRejected() {
		// 이름 변경 이전의 구버전 'state' 필드는 지원하지 않는다. 알려지지 않은 필드로 무시되어
		// 'status'가 없는 것과 같아지므로, 조용히 통과하지 않고 역직렬화가 실패해야 한다.
		String legacy = "{\"session_endpoint\":\"/sessions/1\",\"state\":\"RUNNING\"}";

		Assertions.assertThrows(ValueInstantiationException.class,
								() -> MAPPER.readValue(legacy, RpcResponseMessage.class));
	}

	@Test
	public void testUnknownFieldIgnored() throws IOException {
		// 서버가 필드를 추가하더라도 구버전 클라이언트가 깨지지 않아야 한다.
		String json = "{\"session_endpoint\":\"/sessions/1\",\"status\":\"RUNNING\",\"progress\":0.5}";
		RpcResponseMessage restored = MAPPER.readValue(json, RpcResponseMessage.class);

		Assertions.assertEquals(RpcState.RUNNING, restored.getState());
		Assertions.assertEquals("/sessions/1", restored.getSessionEndpoint());
	}

	@Test
	public void testNewDeserializer() throws IOException {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");
		String serialized = MAPPER.writeValueAsString(resp);

		RpcResponseMessage restored
				= RpcResponseMessage.newDeserializer(MAPPER).deserialize(null, serialized);

		Assertions.assertEquals(RpcState.RUNNING, restored.getState());
		Assertions.assertEquals("/sessions/1", restored.getSessionEndpoint());
	}
}
