package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link HttpRESTfulClient}의 에러 응답 → 예외 변환({@code toRESTfulClientException}) 테스트.
 * <p>
 * 본문 필드 구성으로 포맷을 판별하는 디스패치 로직을 포맷별로 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClientTest {
	private final HttpRESTfulClient m_client = HttpRESTfulClient.newDefaultClient();

	@Test
	public void restfulErrorEntity_code_becomes_remote_exception_with_entity() {
		// 우리 포맷 {code, message}: 예외를 복원하지 않고, 에러 엔티티를 보유한
		// RESTfulRemoteException으로 변환한다.
		String respBody = "{\"code\":\"java.io.IOException\",\"message\":\"disk full\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw m_client.toRESTfulClientException(respBody); });

		assertEquals("java.io.IOException", thrown.getRemoteErrorEntity().getCode());
		assertEquals("disk full", thrown.getRemoteErrorEntity().getMessage());
		assertTrue(thrown.getMessage().contains("disk full"), thrown.getMessage());
	}

	@Test
	public void restfulErrorEntity_messageOnly_becomes_remote_exception() {
		// code 없이 message만 있으면 RESTfulRemoteException(message)로 변환된다.
		String respBody = "{\"message\":\"something went wrong\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw m_client.toRESTfulClientException(respBody); });

		assertEquals("something went wrong", thrown.getMessage());
	}

	@Test
	public void springBootError_with_status_and_path_becomes_io_exception() {
		// Spring Boot 기본 에러 포맷 {timestamp, status, error, message, path} → RESTfulIOException
		String respBody = "{\"timestamp\":\"2024-01-01T00:00:00.000Z\",\"status\":500,"
						+ "\"error\":\"Internal Server Error\",\"message\":\"boom\",\"path\":\"/api/x\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw m_client.toRESTfulClientException(respBody); });

		assertTrue(thrown.getMessage().contains("Internal Server Error"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("/api/x"), thrown.getMessage());
	}

	@Test
	public void springExceptionEntity_with_messageType_becomes_remote_exception() {
		// 커스텀 포맷 {messageType, code, text}: 예외를 복원하지 않고 RESTfulRemoteException으로 변환한다.
		String respBody = "{\"messageType\":\"Exception\",\"code\":\"java.io.IOException\","
						+ "\"text\":\"boom\",\"timestamp\":\"2024-01-01T00:00:00.000Z\"}";

		RESTfulRemoteException thrown = assertThrows(RESTfulRemoteException.class,
													() -> { throw m_client.toRESTfulClientException(respBody); });

		assertTrue(thrown.getMessage().contains("java.io.IOException"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("boom"), thrown.getMessage());
	}

	@Test
	public void unrecognizedJson_becomes_io_exception() {
		String respBody = "{\"foo\":\"bar\"}";

		RESTfulIOException thrown = assertThrows(RESTfulIOException.class,
												() -> { throw m_client.toRESTfulClientException(respBody); });
		assertTrue(thrown.getMessage().contains("Unrecognized"), thrown.getMessage());
	}

	@Test
	public void nonJsonBody_becomes_io_exception() {
		String respBody = "this is not json {";

		assertThrows(RESTfulIOException.class,
					() -> { throw m_client.toRESTfulClientException(respBody); });
	}
}
