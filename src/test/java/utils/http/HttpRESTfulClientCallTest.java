package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.Headers;
import okhttp3.Request;
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

		String resp = HttpRESTfulClient.newDefaultClient().post(url("/x"), "payload", STRING);

		assertEquals("ok", resp);
		RecordedRequest req = m_server.takeRequest();
		assertEquals("POST", req.getMethod());
		assertEquals("payload", req.getBody().readUtf8());
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
		Tuple<String, Headers> result = HttpRESTfulClient.newDefaultClient().callAndGetHeaders(req, STRING);

		assertEquals("body", result._1);
		assertEquals("r1", result._2.get("X-Resp"));
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
	public void springBootErrorStatusThrowsIOException() {
		m_server.enqueue(new MockResponse().setResponseCode(503)
								.setBody("{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":503,"
										+ "\"error\":\"Service Unavailable\",\"message\":\"down\",\"path\":\"/x\"}"));

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> HttpRESTfulClient.newDefaultClient().get(url("/x"), STRING));
		assertTrue(thrown.getMessage().contains("Service Unavailable"), thrown.getMessage());
	}
}
