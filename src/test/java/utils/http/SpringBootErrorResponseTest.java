package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.json.JacksonUtils;


/**
 * {@link SpringBootErrorResponse} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SpringBootErrorResponseTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	@Test
	public void jsonDeserialize() throws IOException {
		SpringBootErrorResponse resp = MAPPER.readValue(
				"{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":500,"
				+ "\"error\":\"Internal Server Error\",\"message\":\"boom\",\"path\":\"/api/x\"}",
				SpringBootErrorResponse.class);

		assertEquals(500, resp.getStatus());
		assertEquals("Internal Server Error", resp.getError());
		assertEquals("boom", resp.getMessage());
		assertEquals("/api/x", resp.getPath());
	}

	@Test
	public void ignoresUnknownFields() throws IOException {
		// server.error.include-stacktrace/include-exception 설정에 따라 trace/exception 같은
		// 부가 필드가 포함되어도 역직렬화가 실패하지 않아야 한다.
		SpringBootErrorResponse resp = MAPPER.readValue(
				"{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":500,"
				+ "\"error\":\"Internal Server Error\",\"message\":\"boom\",\"path\":\"/api/x\","
				+ "\"exception\":\"java.lang.RuntimeException\","
				+ "\"trace\":\"java.lang.RuntimeException: boom\\n\\tat foo.Bar.baz(Bar.java:1)\"}",
				SpringBootErrorResponse.class);

		assertEquals(500, resp.getStatus());
		assertEquals("Internal Server Error", resp.getError());
		assertEquals("boom", resp.getMessage());
		assertEquals("/api/x", resp.getPath());
	}

	@Test
	public void toStringContainsErrorStatusMessagePath() {
		SpringBootErrorResponse resp = new SpringBootErrorResponse();
		resp.setStatus(404);
		resp.setError("Not Found");
		resp.setMessage("missing");
		resp.setPath("/api/y");

		String str = resp.toString();
		assertTrue(str.contains("Not Found"), str);
		assertTrue(str.contains("404"), str);
		assertTrue(str.contains("missing"), str);
		assertTrue(str.contains("/api/y"), str);
	}
}
