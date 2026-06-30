package utils.rpc.restful;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
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
		Assertions.assertThrows(IllegalStateException.class,
								() -> new RpcResponseMessage(null, RpcState.COMPLETED, null, null));
	}

	@Test
	public void testFailedWithoutErrorRejected() {
		Assertions.assertThrows(IllegalStateException.class,
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
	public void testNewDeserializer() throws IOException {
		RpcResponseMessage resp = RpcResponseMessage.running("/sessions/1");
		String serialized = MAPPER.writeValueAsString(resp);

		RpcResponseMessage restored
				= RpcResponseMessage.newDeserializer(MAPPER).deserialize(null, serialized);

		Assertions.assertEquals(RpcState.RUNNING, restored.getState());
		Assertions.assertEquals("/sessions/1", restored.getSessionEndpoint());
	}
}
