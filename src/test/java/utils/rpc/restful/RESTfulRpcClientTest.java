package utils.rpc.restful;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import utils.json.JacksonUtils;
import utils.rpc.restful.RESTfulRpcClient;
import utils.rpc.restful.RpcResponseMessage;


/**
 * {@link RESTfulRpcClient} 통합 테스트. (MockWebServer 사용)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulRpcClientTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	private MockWebServer m_server;

	@BeforeEach
	public void setup() throws IOException {
		m_server = new MockWebServer();
		m_server.start();
	}

	@AfterEach
	public void teardown() throws IOException {
		m_server.shutdown();
	}

	private void enqueue(RpcResponseMessage resp) throws IOException {
		m_server.enqueue(new MockResponse().setBody(MAPPER.writeValueAsString(resp)));
	}

	private RESTfulRpcClient client(Map<String,JsonNode> inputs) {
		return RESTfulRpcClient.builder()
									.setHttpClient(new OkHttpClient())
									.setEndpoint(m_server.url("/operation").toString())
									.setJsonMapper(MAPPER)
									.setInputs(inputs)
									.build();
	}

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	@Test
	public void testCompletedReturnsOutputs() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", json("{\"v\":7}"));
		enqueue(RpcResponseMessage.completed("/sessions/1", outputs));

		Map<String,JsonNode> result = client(Map.of("x", json("1"))).call();

		Assertions.assertEquals(outputs, result);
	}

	@Test
	public void testFailedThrowsExecutionException() throws Exception {
		enqueue(RpcResponseMessage.failed("/sessions/1", new IllegalStateException("boom")));

		ExecutionException thrown = Assertions.assertThrows(ExecutionException.class,
															() -> client(Map.of()).call());
		Assertions.assertNotNull(thrown.getCause());
	}

	@Test
	public void testCancelledThrowsCancellationException() throws Exception {
		enqueue(RpcResponseMessage.cancelled("/sessions/1"));

		Assertions.assertThrows(CancellationException.class, () -> client(Map.of()).call());
	}

	@Test
	public void testRunningStatusThrowsIllegalState() throws Exception {
		// 동기 클라이언트는 RUNNING 응답을 처리하지 못하므로 IllegalStateException을 던진다.
		enqueue(RpcResponseMessage.running("/sessions/1"));

		Assertions.assertThrows(IllegalStateException.class, () -> client(Map.of()).call());
	}

	@Test
	public void testRequestBodyContainsInputs() throws Exception {
		enqueue(RpcResponseMessage.completed("/sessions/1", Map.of()));

		client(Map.of("x", json("42"))).call();

		var recorded = m_server.takeRequest();
		Assertions.assertEquals("POST", recorded.getMethod());
		String body = recorded.getBody().readUtf8();
		Assertions.assertTrue(body.contains("\"x\""), body);
		Assertions.assertTrue(body.contains("42"), body);
	}
}
