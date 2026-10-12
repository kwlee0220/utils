package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import okhttp3.HttpUrl;

/**
 * {@link HttpRESTfulClient}의 에러 응답 → 예외 변환({@code toRESTfulClientException}) 테스트.
 * <p>
 * 본문 필드 구성으로 포맷을 판별하는 디스패치 로직을 포맷별로 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClientTest {
	private static final HttpUrl URL = HttpUrl.get("http://localhost:8080/svc/items");

	private final HttpRESTfulClient m_client = HttpRESTfulClient.newDefaultClient();

	@Test
	public void restfulErrorEntity_code_becomes_remote_exception_with_entity() {
		// 우리 포맷 {code, message}: 예외를 복원하지 않고, 에러 엔티티를 보유한
		// RESTfulRemoteException으로 변환한다.
		String respBody = "{\"code\":\"java.io.IOException\",\"message\":\"disk full\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw toException(respBody); });

		assertEquals("java.io.IOException", thrown.getRemoteErrorEntity().getCode());
		assertEquals("disk full", thrown.getRemoteErrorEntity().getMessage());
		assertTrue(thrown.getMessage().contains("disk full"), thrown.getMessage());
	}

	@Test
	public void restfulErrorEntity_messageOnly_becomes_remote_exception() {
		// code 없이 message만 있으면 RESTfulRemoteException(message)로 변환된다.
		String respBody = "{\"message\":\"something went wrong\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw toException(respBody); });

		assertEquals("something went wrong", thrown.getMessage());
	}

	@Test
	public void springBootError_with_status_and_path_becomes_io_exception() {
		// Spring Boot 기본 에러 포맷 {timestamp, status, error, message, path} → RESTfulIOException
		String respBody = "{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":500,"
						+ "\"error\":\"Internal Server Error\",\"message\":\"boom\",\"path\":\"/api/x\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw toException(respBody); });

		// 원문을 그대로 echo하는 "Unrecognized ..." 메시지에도 "Internal Server Error"와 "/api/x"가
		// 들어 있으므로, 분기 고유의 SpringBootErrorResponse.toString() 형식으로 확인한다.
		assertTrue(thrown.getMessage().contains("Internal Server Error (500): boom, path=/api/x"),
					thrown.getMessage());
	}

	@Test
	public void nestedErrorObject_is_unwrapped() {
		// 에러 본문을 {"error": {...}}로 감싸 보내는 서버: 한 겹 벗겨 우리 포맷으로 처리한다.
		String respBody = "{\"error\":{\"code\":\"java.lang.IllegalStateException\",\"message\":\"boom\"}}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw toException(respBody); });

		assertTrue(thrown.getMessage().contains("boom"), thrown.getMessage());
	}

	@Test
	public void springExceptionEntity_with_messageType_becomes_remote_exception() {
		// 커스텀 포맷 {messageType, code, text}: 예외를 복원하지 않고 RESTfulRemoteException으로 변환한다.
		String respBody = "{\"messageType\":\"Exception\",\"code\":\"java.io.IOException\","
						+ "\"text\":\"boom\",\"timestamp\":\"2024-01-01T00:00:00.000Z\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw toException(respBody); });

		assertTrue(thrown.getMessage().contains("java.io.IOException"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("boom"), thrown.getMessage());
	}

	@Test
	public void unrecognizedJson_becomes_io_exception() {
		String respBody = "{\"foo\":\"bar\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw toException(respBody); });
		assertTrue(thrown.getMessage().contains("Unrecognized"), thrown.getMessage());
	}

	@Test
	public void nonJsonBody_becomes_io_exception() {
		String respBody = "this is not json {";

		assertThrows(RESTfulIOException.class,
					() -> { throw toException(respBody); });
	}

	@Test
	public void malformedMessageType_becomes_io_exception() {
		// convertValue()는 enum 값이 맞지 않으면 unchecked IllegalArgumentException을 던진다.
		// 그것이 그대로 새어나가지 않고 RESTfulIOException으로 변환되어야 한다.
		String respBody = "{\"messageType\":\"Bogus\",\"code\":\"E1\",\"text\":\"boom\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw toException(respBody); });
		assertTrue(thrown.getMessage().contains("Failed to parse"), thrown.getMessage());
	}

	@Test
	public void malformedSpringBootStatus_becomes_io_exception() {
		// SpringBootErrorResponse.status는 int이므로 문자열이 오면 convertValue()가 실패한다.
		String respBody = "{\"status\":\"abc\",\"path\":\"/api/x\",\"error\":\"Bad\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw toException(respBody); });
		assertTrue(thrown.getMessage().contains("Failed to parse"), thrown.getMessage());
	}

	@Test
	public void emptyBody_becomes_io_exception() {
		// 본문이 없는 에러 응답(예: 본문 없는 404)도 예외로 변환되어야 한다.
		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw toException(""); });
		assertTrue(thrown.getMessage().contains("Empty"), thrown.getMessage());
	}

	@Test
	public void ioExceptionMessageCarriesStatusCodeAndUrl() {
		// 서버가 준 메시지만으로는 어느 요청이 왜 실패했는지 알 수 없으므로,
		// RESTfulIOException 계열 메시지에는 상태 코드와 URL이 함께 실린다.
		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
									() -> { throw m_client.toRESTfulClientException(404, URL, ""); });

		assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("/svc/items"), thrown.getMessage());
	}

	private RuntimeException toException(String respBody) {
		return m_client.toRESTfulClientException(500, URL, respBody);
	}
}
