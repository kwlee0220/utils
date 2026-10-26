package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.InternalException;
import utils.json.JacksonUtils;


/**
 * {@link RESTfulErrorEntity} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulErrorEntityTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	// ----- 팩토리 -----

	@Test
	public void ofThrowable() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.of(new IllegalStateException("boom"));

		assertEquals("java.lang.IllegalStateException", entity.getCode());
		assertEquals("boom", entity.getMessage());
	}

	@Test
	public void ofThrowableWithNullMessage() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.of(new IllegalStateException());

		assertEquals("java.lang.IllegalStateException", entity.getCode());
		assertEquals("", entity.getMessage());
	}

	@Test
	public void ofMessageAndCause() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.of("failed", new IOException("disk full"));

		assertEquals("java.io.IOException", entity.getCode());
		assertTrue(entity.getMessage().contains("failed"), entity.getMessage());
		assertTrue(entity.getMessage().contains("disk full"), entity.getMessage());
	}

	@Test
	public void ofMessageOnly() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.ofMessage("just a message");

		assertNull(entity.getCode());
		assertEquals("just a message", entity.getMessage());
	}

	// ----- toException -----

	@Test
	public void toExceptionCarriesEntity() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.of(new IOException("disk full"));

		Throwable ex = entity.toRemoteException();

		RESTfulRemoteException remote = assertInstanceOf(RESTfulRemoteException.class, ex);
		assertSame(entity, remote.getRemoteErrorEntity());
	}

	// ----- toString -----

	@Test
	public void toStringWithCodeAndMessage() {
		// code + message 모두 존재 → "code (message)" 형식
		String str = RESTfulErrorEntity.of(new MyError("M")).toString();

		assertTrue(str.contains(MyError.class.getName()), str);
		assertTrue(str.contains("(M)"), str);
	}

	@Test
	public void toStringMessageOnly() {
		assertEquals("only", RESTfulErrorEntity.ofMessage("only").toString());
	}

	@Test
	public void toStringBothNullThrows() {
		RESTfulErrorEntity entity = RESTfulErrorEntity.ofMessage(null);
		assertThrows(InternalException.class, entity::toString);
	}

	// ----- JSON -----

	@Test
	public void jsonDeserialize() throws IOException {
		RESTfulErrorEntity entity = MAPPER.readValue(
				"{\"code\":\"java.io.IOException\",\"message\":\"disk full\"}", RESTfulErrorEntity.class);

		assertEquals("java.io.IOException", entity.getCode());
		assertEquals("disk full", entity.getMessage());
	}

	@Test
	public void jsonDeserializeIgnoresUnknownFields() throws IOException {
		// 서버가 code/message 외의 부가 정보를 함께 실어 보내도 엔티티로 읽혀야 한다.
		// (예: 자원 점유 거부에 점유자와 예상 잔여 시간을 싣는 서버)
		RESTfulErrorEntity entity = MAPPER.readValue(
				"{\"code\":\"RESOURCE_BUSY\",\"message\":\"resource 'arm' is occupied\","
				+ "\"resource\":\"arm\",\"estimated_remaining_ms\":1200}", RESTfulErrorEntity.class);

		assertEquals("RESOURCE_BUSY", entity.getCode());
		assertEquals("resource 'arm' is occupied", entity.getMessage());
	}

	@SuppressWarnings("serial")
	private static class MyError extends RuntimeException {
		MyError(String msg) { super(msg); }
	}
}
