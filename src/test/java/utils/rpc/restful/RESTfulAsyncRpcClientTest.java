package utils.rpc.restful;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import utils.async.AsyncResult;
import utils.json.JacksonUtils;
import utils.rpc.restful.RESTfulAsyncRpcClient;
import utils.rpc.restful.RpcRequestMessage;
import utils.rpc.restful.RpcResponseMessage;


/**
 * {@link RESTfulAsyncRpcClient} 통합 테스트. (MockWebServer 사용)
 * <p>
 * 최초 POST로 세션을 얻은 뒤 {@code /status}를 주기적으로 폴링하는 흐름을 enqueue된 응답 순서로 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulAsyncRpcClientTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	private MockWebServer m_server;
	private String m_baseUrl;

	@BeforeEach
	public void setup() throws IOException {
		m_server = new MockWebServer();
		m_server.start();

		// baseUrl은 끝의 '/'를 제거하여 사용한다. (operationUrl/sessionUrl 결합 규칙과 맞춤)
		String url = m_server.url("/").toString();
		m_baseUrl = url.substring(0, url.length() - 1);
	}

	@AfterEach
	public void teardown() throws IOException {
		m_server.shutdown();
	}

	private void enqueue(RpcResponseMessage resp) throws IOException {
		m_server.enqueue(new MockResponse().setBody(MAPPER.writeValueAsString(resp)));
	}

	private RESTfulAsyncRpcClient client() throws IOException {
		return RESTfulAsyncRpcClient.builder()
									.setHttpClient(new OkHttpClient())
									.setBaseUrl(m_baseUrl)
									.setOperationEndpoint("operation")
									.setJsonMapper(MAPPER)
									.setRequestMessage(new RpcRequestMessage(Map.of("x", json("1"))))
									.setPollInterval(Duration.ofMillis(100))
									.build();
	}

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	@Test
	public void testRunningThenCompleted() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", json("{\"v\":7}"));
		enqueue(RpcResponseMessage.running("/sessions/1"));      // POST 응답
		enqueue(RpcResponseMessage.running("/sessions/1"));      // 첫 status 폴링 (아직 진행 중)
		enqueue(RpcResponseMessage.completed("/sessions/1", outputs)); // 두 번째 status 폴링

		RESTfulAsyncRpcClient client = client();
		client.start();
		AsyncResult<Map<String,JsonNode>> result = client.waitForFinished(5, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isCompleted());
		Assertions.assertEquals(outputs, result.getUnchecked());
	}

	@Test
	public void testFailedOnStart() throws Exception {
		// 최초 POST가 FAILED면 폴링을 시작하기 전에 실패로 종료된다.
		enqueue(RpcResponseMessage.failed("/sessions/1", new IllegalStateException("boom")));

		RESTfulAsyncRpcClient client = client();
		client.start();
		AsyncResult<Map<String,JsonNode>> result = client.waitForFinished(5, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertNotNull(result.getFailureCause());
	}

	@Test
	public void testFailedDuringPolling() throws Exception {
		enqueue(RpcResponseMessage.running("/sessions/1"));
		enqueue(RpcResponseMessage.failed("/sessions/1", new IllegalStateException("boom")));

		RESTfulAsyncRpcClient client = client();
		client.start();
		AsyncResult<Map<String,JsonNode>> result = client.waitForFinished(5, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isFailed());
		Assertions.assertNotNull(result.getFailureCause());
	}

	@Test
	public void testCancelledDuringPolling() throws Exception {
		enqueue(RpcResponseMessage.running("/sessions/1"));
		enqueue(RpcResponseMessage.cancelled("/sessions/1"));

		RESTfulAsyncRpcClient client = client();
		client.start();
		AsyncResult<Map<String,JsonNode>> result = client.waitForFinished(5, TimeUnit.SECONDS);

		Assertions.assertTrue(result.isCancelled());
	}

	@Test
	public void testPollsStateEndpoint() throws Exception {
		enqueue(RpcResponseMessage.running("/sessions/1"));
		enqueue(RpcResponseMessage.completed("/sessions/1", Map.of()));

		RESTfulAsyncRpcClient client = client();
		client.start();
		client.waitForFinished(5, TimeUnit.SECONDS);

		// 1) 최초 POST 요청. (요청이 아예 도착하지 않는 회귀에서 suite 전체가 hang되지 않도록
		// timeout을 두고 대기한다.)
		var post = m_server.takeRequest(5, TimeUnit.SECONDS);
		Assertions.assertNotNull(post, "최초 POST 요청이 도착해야 함");
		Assertions.assertEquals("POST", post.getMethod());

		// 2) 세션 state 폴링 요청 (GET .../sessions/1/state)
		var poll = m_server.takeRequest(5, TimeUnit.SECONDS);
		Assertions.assertNotNull(poll, "세션 state 폴링 요청이 도착해야 함");
		Assertions.assertEquals("GET", poll.getMethod());
		Assertions.assertTrue(poll.getPath().endsWith("/sessions/1/state"), poll.getPath());
	}
}
