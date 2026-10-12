package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import utils.Tuple;
import utils.http.HttpRESTfulClient.ResponseBodyDeserializer;


/**
 * {@link HttpRESTfulClient}의 실제 HTTP 호출 경로 통합 테스트. (MockWebServer 사용)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClientCallTest {
	private static final ResponseBodyDeserializer<String> STRING = HttpRESTfulClient.STRING_DESER;

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

	private String url(String path) {
		return m_server.url(path).toString();
	}

	// ----- 성공 응답 -----

	@Test
	public void getReturnsBody() throws Exception {
		m_server.enqueue(new MockResponse().setBody("hello"));

		String body = HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING);

		assertEquals("hello", body);
		RecordedRequest req = m_server.takeRequest();
		assertEquals("GET", req.getMethod());
	}

	@Test
	public void getVoidSucceeds() throws Exception {
		m_server.enqueue(new MockResponse().setBody(""));

		HttpRESTfulClient.newDefaultClient().get(url("/x"));

		assertEquals("GET", m_server.takeRequest().getMethod());
	}

	@Test
	public void postSendsBody() throws Exception {
		m_server.enqueue(new MockResponse().setBody("ok"));

		RequestBody reqBody = RequestBody.create("payload", HttpRESTfulClient.MEDIA_TYPE_TEXT);
		String resp = HttpRESTfulClient.newDefaultClient().post(url("/x"), reqBody, STRING);

		assertEquals("ok", resp);
		RecordedRequest req = m_server.takeRequest();
		assertEquals("POST", req.getMethod());
		assertEquals("payload", req.getBody().readUtf8());
	}

	@Test
	public void newJsonRequestBodySendsApplicationJson() throws Exception {
		m_server.enqueue(new MockResponse().setBody("ok"));

		RequestBody reqBody = HttpRESTfulClient.newJsonRequestBody("{\"a\":1}");
		HttpRESTfulClient.newDefaultClient().post(url("/x"), reqBody, STRING);

		RecordedRequest req = m_server.takeRequest();
		assertEquals("application/json; charset=utf-8", req.getHeader("Content-Type"));
		assertEquals("{\"a\":1}", req.getBody().readUtf8());
	}

	@Test
	public void newTextRequestBodySendsTextPlain() throws Exception {
		m_server.enqueue(new MockResponse().setBody("ok"));

		RequestBody reqBody = HttpRESTfulClient.newTextRequestBody("plain");
		HttpRESTfulClient.newDefaultClient().put(url("/x"), reqBody, STRING);

		RecordedRequest req = m_server.takeRequest();
		assertEquals("PUT", req.getMethod());
		assertEquals("text/plain; charset=utf-8", req.getHeader("Content-Type"));
		assertEquals("plain", req.getBody().readUtf8());
	}

	// ----- 본문을 가질 수 없는 응답 -----

	@Test
	public void noContentReturnsNull() throws Exception {
		m_server.enqueue(new MockResponse().setResponseCode(204));

		assertNull(HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
	}

	@Test
	public void resetContentReturnsNull() throws Exception {
		m_server.enqueue(new MockResponse().setResponseCode(205));

		assertNull(HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
	}

	@Test
	public void notModifiedIsStillAnError() {
		// 304는 성공 응답이 아니므로 본문이 없더라도 에러로 처리된다.
		m_server.enqueue(new MockResponse().setResponseCode(304));

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
		assertTrue(thrown.getMessage().contains("304"), thrown.getMessage());
	}

	@Test
	public void headErrorResponseCarriesStatusCode() {
		// HEAD 응답은 본문이 없으므로 상태 코드가 유일한 단서이다.
		m_server.enqueue(new MockResponse().setResponseCode(403));

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> HttpRESTfulClient.newDefaultClient().head(url("/x")));
		assertTrue(thrown.getMessage().contains("403"), thrown.getMessage());
	}

	// ----- deserializer -----

	@Test
	public void jsonDeserializerUsesConfiguredMapper() throws Exception {
		// JSON_DESER는 전용 매퍼를 쓰므로 builder().jsonMapper() 설정이 반영되지 않지만,
		// jsonDeserializer()는 반영된다.
		m_server.enqueue(new MockResponse().setBody("{'a':1}"));
		m_server.enqueue(new MockResponse().setBody("{'a':1}"));

		JsonMapper mapper = JsonMapper.builder().enable(JsonReadFeature.ALLOW_SINGLE_QUOTES).build();
		HttpRESTfulClient client = HttpRESTfulClient.builder().jsonMapper(mapper).build();

		JsonNode parsed = client.get(url("/x"), client.jsonDeserializer());
		assertEquals(1, parsed.get("a").asInt());

		assertThrows(RESTfulIOException.class, () -> client.get(url("/x"), HttpRESTfulClient.JSON_DESER));
	}

	@Test
	public void defaultHeaderApplied() throws Exception {
		m_server.enqueue(new MockResponse().setBody("ok"));

		HttpRESTfulClient client = HttpRESTfulClient.builder()
													.header("X-Test", "v1")
													.build();
		client.get(url("/x"), STRING);

		assertEquals("v1", m_server.takeRequest().getHeader("X-Test"));
	}

	@Test
	public void callAndGetHeadersReturnsBodyAndHeaders() throws Exception {
		m_server.enqueue(new MockResponse().setBody("body").addHeader("X-Resp", "r1"));

		Request req = new Request.Builder().url(url("/x")).get().build();
		Tuple<Headers,String> result = HttpRESTfulClient.newDefaultClient().callAndGetHeaders(req, STRING);

		assertEquals("body", result._2);
		assertEquals("r1", result._1.get("X-Resp"));
	}

	// ----- 에러 응답 -----

	@Test
	public void errorStatusWithErrorEntityThrowsRemote() {
		m_server.enqueue(new MockResponse().setResponseCode(500)
								.setBody("{\"code\":\"java.io.IOException\",\"message\":\"disk full\"}"));

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
												() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
		assertEquals("disk full", thrown.getRemoteErrorEntity().getMessage());
	}

	@Test
	public void errorStatusOnVoidCallThrows() {
		m_server.enqueue(new MockResponse().setResponseCode(400)
								.setBody("{\"message\":\"bad request\"}"));

		assertThrows(RESTfulRemoteException.class,
					() -> HttpRESTfulClient.newDefaultClient().get(url("/x")));
	}

	@Test
	public void emptyErrorBodyCarriesStatusCode() {
		// 본문 없는 404: 상태 코드 외에 단서가 없으므로 예외 메시지에 상태 코드가 실려야 한다.
		m_server.enqueue(new MockResponse().setResponseCode(404));

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
		assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("/x"), thrown.getMessage());
	}

	// ----- 타임아웃 -----

	@Test
	public void callTimeoutDoesNotLeaveThreadInterrupted() {
		// OkHttp의 call timeout은 InterruptedIOException("timeout")으로 보고되지만 인터럽트가 아니다.
		// 이를 인터럽트로 오인해 플래그를 세우면 호출 스레드의 이후 blocking 연산이 오작동한다.
		m_server.enqueue(new MockResponse().setBody("late").setBodyDelay(2, TimeUnit.SECONDS));

		OkHttpClient httpClient = new OkHttpClient.Builder()
											.callTimeout(Duration.ofMillis(300))
											.build();
		HttpRESTfulClient client = HttpRESTfulClient.builder().httpClient(httpClient).build();

		assertThrows(RESTfulIOException.class, () -> client.get(url("/x"), STRING));
		assertFalse(Thread.currentThread().isInterrupted(), "call timeout이 인터럽트 플래그를 남겼다");
	}

	@Test
	public void readTimeoutIsReportedAsTimeoutNotConnectFailure() {
		// SocketTimeoutException은 connect뿐 아니라 read/write 타임아웃에서도 발생한다.
		// 응답 지연을 연결 실패로 보고하면 안 된다.
		m_server.enqueue(new MockResponse().setBody("late").setHeadersDelay(2, TimeUnit.SECONDS));

		OkHttpClient httpClient = new OkHttpClient.Builder()
											.readTimeout(Duration.ofMillis(300))
											.build();
		HttpRESTfulClient client = HttpRESTfulClient.builder().httpClient(httpClient).build();

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class, () -> client.get(url("/x"), STRING));
		assertTrue(thrown.getMessage().contains("timed out"), thrown.getMessage());
	}

	// ----- deserializer가 던지는 예외 -----

	@Test
	public void deserializerRuntimeExceptionIsWrapped() {
		// deserializer가 unchecked 예외를 던져도 RESTfulException 계층 밖으로 새어나가면 안 된다.
		m_server.enqueue(new MockResponse().setBody("body"));

		ResponseBodyDeserializer<String> bad = (headers, body) -> { throw new IllegalStateException("boom"); };

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
											() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), bad));
		assertTrue(thrown.getMessage().contains("Failed to parse"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("/x"), thrown.getMessage());
		assertTrue(thrown.getCause() instanceof IllegalStateException, String.valueOf(thrown.getCause()));
	}

	@Test
	public void deserializerRESTfulExceptionIsNotDoubleWrapped() {
		m_server.enqueue(new MockResponse().setBody("body"));

		ResponseBodyDeserializer<String> bad = (headers, body) -> {
			throw new RESTfulRemoteException("already typed");
		};

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
											() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), bad));
		assertEquals("already typed", thrown.getMessage());
	}

	@Test
	public void springBootErrorStatusThrowsIOException() {
		m_server.enqueue(new MockResponse().setResponseCode(503)
								.setBody("{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":503,"
										+ "\"error\":\"Service Unavailable\",\"message\":\"down\",\"path\":\"/x\"}"));

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
		assertTrue(thrown.getMessage().contains("Service Unavailable"), thrown.getMessage());
	}
}
