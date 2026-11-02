package utils.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@link JacksonUtils}의 필드 조회 타입 검사와 {@link JacksonDeserializationException}의 메시지를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonUtilsTest {
	private static JsonNode json(String text) throws IOException {
		return JacksonUtils.MAPPER.readTree(text);
	}

	// --- 문자열 필드 ---

	@Test
	public void stringField_readsScalarsAsText() throws IOException {
		JsonNode node = json("{\"s\":\"abc\",\"n\":19051,\"b\":true,\"nul\":null}");
		assertEquals("abc", JacksonUtils.getStringField(node, "s"));
		// 숫자·불리언도 텍스트로 읽는다(기존 설정 파일과의 호환).
		assertEquals("19051", JacksonUtils.getStringField(node, "n"));
		assertEquals("true", JacksonUtils.getStringFieldOrNull(node, "b"));
		// 필드가 없거나 JSON null이면 기본값.
		assertNull(JacksonUtils.getStringFieldOrNull(node, "none"));
		assertNull(JacksonUtils.getStringFieldOrNull(node, "nul"));
		assertEquals("dflt", JacksonUtils.getStringFieldOrDefault(node, "none", "dflt"));
		assertThrows(IOException.class, () -> JacksonUtils.getStringField(node, "none"));
	}

	@Test
	public void stringField_rejectsContainers() throws IOException {
		JsonNode node = json("{\"obj\":{\"x\":1},\"arr\":[1,2]}");
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getStringFieldOrNull(node, "obj"));
		assertEquals("invalid field 'obj': string expected, but object", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getStringField(node, "arr"));
		assertThrows(JacksonDeserializationException.class,
					() -> JacksonUtils.getStringFieldOrDefault(node, "arr", "dflt"));
	}

	// --- 엄격한 문자열(Text) 필드 ---

	@Test
	public void textField_acceptsOnlyJsonStrings() throws IOException {
		JsonNode node = json("{\"s\":\"1\",\"n\":1,\"b\":true,\"o\":{},\"nul\":null}");
		assertEquals("1", JacksonUtils.getTextField(node, "s"));
		assertNull(JacksonUtils.getTextFieldOrNull(node, "nul"));
		assertEquals("d", JacksonUtils.getTextFieldOrDefault(node, "none", "d"));
		// getStringField와 달리 숫자·불리언을 텍스트로 바꾸지 않는다.
		assertEquals("1", JacksonUtils.getStringField(node, "n"));
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getTextField(node, "n"));
		assertEquals("invalid field 'n': string expected, but number", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getTextFieldOrNull(node, "b"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getTextFieldOrDefault(node, "o", "d"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getTextField(node, "none"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getTextField(json("[1]"), "s"));
	}

	// --- 불리언 필드 ---

	@Test
	public void booleanField_acceptsBooleansAndBooleanText() throws IOException {
		JsonNode node = json("{\"t\":true,\"f\":false,\"ts\":\"TRUE\",\"fs\":\"false\"}");
		assertEquals(true, JacksonUtils.getBooleanFieldOrNull(node, "t"));
		assertEquals(false, JacksonUtils.getBooleanFieldOrNull(node, "f"));
		assertEquals(true, JacksonUtils.getBooleanFieldOrNull(node, "ts"));
		assertEquals(false, JacksonUtils.getBooleanFieldOrNull(node, "fs"));
		assertNull(JacksonUtils.getBooleanFieldOrNull(node, "none"));
		assertEquals(true, JacksonUtils.getBooleanFieldOrDefault(node, "none", true));
		assertEquals(true, JacksonUtils.getBooleanField(node, "t"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getBooleanField(node, "none"));
	}

	@Test
	public void booleanField_rejectsOtherValues() throws IOException {
		JsonNode node = json("{\"yes\":\"yes\",\"one\":1,\"obj\":{}}");
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getBooleanFieldOrNull(node, "yes"));
		assertEquals("invalid field 'yes': boolean expected, but string", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getBooleanFieldOrNull(node, "one"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getBooleanFieldOrNull(node, "obj"));
	}

	// --- 정수 필드 ---

	@Test
	public void intField_acceptsOnlyIntRangeIntegers() throws IOException {
		JsonNode node = json("{\"i\":42,\"neg\":-7,\"s\":\"abc\",\"f\":3.9,\"big\":3000000000,\"ns\":\"42\"}");
		assertEquals(42, JacksonUtils.getIntFieldOrDefault(node, "i", -1));
		assertEquals(-7, JacksonUtils.getIntFieldOrDefault(node, "neg", -1));
		assertEquals(-1, JacksonUtils.getIntFieldOrDefault(node, "none", -1));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntFieldOrDefault(node, "s", -1));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntFieldOrDefault(node, "f", -1));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntFieldOrDefault(node, "big", -1));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntFieldOrDefault(node, "ns", -1));
		assertEquals(42, JacksonUtils.getIntField(node, "i"));
		assertNull(JacksonUtils.getIntFieldOrNull(node, "none"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntField(node, "none"));
	}

	// --- long·double 필드 ---

	@Test
	public void longField_acceptsLongRangeIntegers() throws IOException {
		JsonNode node = json("{\"big\":3000000000,\"f\":1.5,\"s\":\"7\"}");
		assertEquals(3000000000L, JacksonUtils.getLongField(node, "big"));
		assertNull(JacksonUtils.getLongFieldOrNull(node, "none"));
		assertEquals(-1L, JacksonUtils.getLongFieldOrDefault(node, "none", -1L));
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getLongField(node, "f"));
		assertEquals("invalid field 'f': long expected, but number", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getLongFieldOrNull(node, "s"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getLongField(node, "none"));
	}

	@Test
	public void doubleField_acceptsNumbers() throws IOException {
		JsonNode node = json("{\"d\":1.5,\"i\":3,\"s\":\"1.5\"}");
		assertEquals(1.5, JacksonUtils.getDoubleField(node, "d"));
		assertEquals(3.0, JacksonUtils.getDoubleField(node, "i"));
		assertNull(JacksonUtils.getDoubleFieldOrNull(node, "none"));
		assertEquals(0.5, JacksonUtils.getDoubleFieldOrDefault(node, "none", 0.5));
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getDoubleFieldOrNull(node, "s"));
		assertEquals("invalid field 's': double expected, but string", e.getMessage());

		// double 범위를 넘어 무한대가 되는 값은 거부한다(int·long의 범위 검사와 같은 취지).
		JsonNode huge = json("{\"x\":1e400,\"y\":-1e400}");
		e = assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getDoubleField(huge, "x"));
		assertEquals("invalid field 'x': finite double expected", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getDoubleFieldOrNull(huge, "y"));
	}

	// --- JSON null과 필드 누락 ---

	@Test
	public void jsonNull_isTreatedAsMissingForEveryType() throws IOException {
		// 필드가 없을 때와 값이 JSON null일 때를 구분하지 않는다: 필수 조회는 예외, OrNull은 null, OrDefault는 기본값.
		JsonNode node = json("{\"nul\":null}");
		for ( String name: new String[] { "nul", "none" } ) {
			assertNull(JacksonUtils.getFieldOrNull(node, name));
			assertNull(JacksonUtils.getStringFieldOrNull(node, name));
			assertNull(JacksonUtils.getBooleanFieldOrNull(node, name));
			assertNull(JacksonUtils.getIntFieldOrNull(node, name));
			assertNull(JacksonUtils.getLongFieldOrNull(node, name));
			assertNull(JacksonUtils.getDoubleFieldOrNull(node, name));
			assertNull(JacksonUtils.getArrayFieldOrNull(node, name));

			assertEquals("d", JacksonUtils.getStringFieldOrDefault(node, name, "d"));
			assertEquals(true, JacksonUtils.getBooleanFieldOrDefault(node, name, true));
			assertEquals(7, JacksonUtils.getIntFieldOrDefault(node, name, 7));
			assertEquals(7L, JacksonUtils.getLongFieldOrDefault(node, name, 7L));
			assertEquals(0.5, JacksonUtils.getDoubleFieldOrDefault(node, name, 0.5));
			assertTrue(JacksonUtils.getArrayFieldOrEmpty(node, name).isEmpty());

			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getStringField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getBooleanField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getLongField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getDoubleField(node, name));
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getArrayField(node, name));
		}
	}

	// --- 부모 노드 검사 ---

	@Test
	public void typedAccessors_rejectNonObjectParent() throws IOException {
		// 부모가 JSON 객체가 아니면 OrNull·OrDefault도 기본값으로 처리하지 않고 거부한다.
		for ( String text: new String[] { "42", "[1,2]", "\"abc\"", "null" } ) {
			JsonNode node = json(text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getIntFieldOrDefault(node, "port", 80), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getStringFieldOrNull(node, "s"), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getBooleanFieldOrDefault(node, "b", true), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getLongFieldOrNull(node, "l"), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getDoubleFieldOrDefault(node, "d", 0), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getArrayFieldOrEmpty(node, "a"), text);
			assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getField(node, "f"), text);
			// 저수준 조회인 getFieldOrNull은 검사하지 않고 null을 돌려준다.
			assertNull(JacksonUtils.getFieldOrNull(node, "f"), text);
		}
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getIntFieldOrDefault(json("42"), "port", 80));
		assertEquals("invalid node for field 'port': object expected, but number", e.getMessage());
	}

	// --- 배열 필드 ---

	@Test
	public void arrayField_returnsElementsAndHandlesMissingField() throws IOException {
		JsonNode node = json("{\"arr\":[1,2,3],\"empty\":[],\"nul\":null}");
		List<JsonNode> elements = JacksonUtils.getArrayField(node, "arr");
		assertEquals(List.of(1, 2, 3), elements.stream().map(JsonNode::intValue).toList());
		assertThrows(UnsupportedOperationException.class, () -> elements.add(null));

		assertTrue(JacksonUtils.getArrayField(node, "empty").isEmpty());
		assertNull(JacksonUtils.getArrayFieldOrNull(node, "none"));
		assertNull(JacksonUtils.getArrayFieldOrNull(node, "nul"));
		assertTrue(JacksonUtils.getArrayFieldOrEmpty(node, "none").isEmpty());
		assertEquals(3, JacksonUtils.getArrayFieldOrEmpty(node, "arr").size());
		// 필수 조회는 필드가 없으면 IOException.
		assertThrows(IOException.class, () -> JacksonUtils.getArrayField(node, "none"));
	}

	@Test
	public void arrayField_rejectsNonArrays() throws IOException {
		JsonNode node = json("{\"obj\":{\"k\":5},\"num\":7}");
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> JacksonUtils.getArrayFieldOrNull(node, "obj"));
		assertEquals("invalid field 'obj': array expected, but object", e.getMessage());
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getArrayFieldOrEmpty(node, "num"));
		assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getArrayField(node, "num"));
	}

	// --- 공유 매퍼 설정 ---

	@Test
	public void mapper_writesDatesAsIsoStrings() throws IOException {
		Instant instant = Instant.parse("2024-07-10T05:40:37.123Z");
		assertEquals("\"2024-07-10T05:40:37.123Z\"", JacksonUtils.MAPPER.writeValueAsString(instant));
		assertEquals("\"2024-07-10T14:40:37\"",
					JacksonUtils.MAPPER.writeValueAsString(LocalDateTime.of(2024, 7, 10, 14, 40, 37)));
		assertEquals("\"2024-07-10T05:40:37.123Z\"",
					JacksonUtils.newJsonMapper(true).writeValueAsString(instant));

		// 읽을 때는 ISO 문자열과 숫자(epoch 초) 형식을 모두 받는다.
		assertEquals(instant, JacksonUtils.MAPPER.readValue("\"2024-07-10T05:40:37.123Z\"", Instant.class));
		assertEquals(instant, JacksonUtils.MAPPER.readValue("1720590037.123000000", Instant.class));
	}

	// --- getField 예외 메시지 ---

	@Test
	public void getField_messageListsFieldNamesButNotValues() throws IOException {
		JsonNode node = json("{\"url\":\"jdbc:x\",\"password\":\"s3cret\"}");
		// 필드 누락도 타입 불일치와 같은 JacksonDeserializationException(IOException의 하위 타입)이다.
		IOException e = assertThrows(JacksonDeserializationException.class, () -> JacksonUtils.getField(node, "user"));
		assertEquals("Field not found: field=user, available fields=[url, password]", e.getMessage());
		assertTrue(!e.getMessage().contains("s3cret"), e.getMessage());
	}

	@Test
	public void getField_messageIsBoundedForLargeNodes() throws IOException {
		StringBuilder sb = new StringBuilder("{");
		for ( int i = 0; i < 15; ++i ) {
			sb.append(i > 0 ? "," : "").append("\"f").append(i).append("\":").append(i);
		}
		JsonNode node = json(sb.append("}").toString());
		IOException e = assertThrows(IOException.class, () -> JacksonUtils.getField(node, "none"));
		assertEquals("Field not found: field=none, available fields=[f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, ... (5 more)]",
					e.getMessage());

		// 경계: 정확히 10개면 생략 표시가 없고, 11개면 1개를 생략한다.
		for ( int count: new int[] { 10, 11 } ) {
			StringBuilder obj = new StringBuilder("{");
			for ( int i = 0; i < count; ++i ) {
				obj.append(i > 0 ? "," : "").append("\"f").append(i).append("\":").append(i);
			}
			JsonNode bounded = json(obj.append("}").toString());
			String msg = assertThrows(IOException.class, () -> JacksonUtils.getField(bounded, "none")).getMessage();
			String expected = "Field not found: field=none, available fields=[f0, f1, f2, f3, f4, f5, f6, f7, f8, f9"
							+ ((count == 11) ? ", ... (1 more)]" : "]");
			assertEquals(expected, msg);
		}

		JsonNode array = json("[1,2]");
		e = assertThrows(IOException.class, () -> JacksonUtils.getField(array, "none"));
		// 부모가 객체가 아니면 '필드 없음'이 아니라 구조 오류로 보고한다.
		assertEquals("invalid node for field 'none': object expected, but array", e.getMessage());
	}

	// --- JacksonDeserializationException ---

	@Test
	public void exception_isIOExceptionAndPassesThroughJackson() throws IOException {
		// IOException 계열이므로 Jackson deserializer 안에서 던져도 감싸이지 않는다.
		assertTrue(IOException.class.isAssignableFrom(JacksonDeserializationException.class));
		JsonNode node = json("{\"x\":1}");
		@SuppressWarnings("serial")
		class Thrower extends com.fasterxml.jackson.databind.deser.std.StdDeserializer<Object> {
			Thrower() { super(Object.class); }
			@Override
			public Object deserialize(com.fasterxml.jackson.core.JsonParser p,
										com.fasterxml.jackson.databind.DeserializationContext c) throws IOException {
				return JacksonUtils.getField(p.readValueAsTree(), "none");
			}
		}
		com.fasterxml.jackson.databind.ObjectMapper mapper = JacksonUtils.newJsonMapper(false).rebuild()
				.addModule(new com.fasterxml.jackson.databind.module.SimpleModule().addDeserializer(Object.class, new Thrower()))
				.build();
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> mapper.treeToValue(node, Object.class));
		assertEquals("Field not found: field=none, available fields=[x]", e.getMessage());
	}

	@Test
	public void exceptionMessage_doesNotRepeatTheCause() {
		IllegalStateException cause = new IllegalStateException("boom");
		assertEquals(cause.toString(), new JacksonDeserializationException(cause).getMessage());
		assertEquals("bad", new JacksonDeserializationException("bad", cause).getMessage());
		assertSame(cause, new JacksonDeserializationException("bad", cause).getCause());
		assertEquals("bad", new JacksonDeserializationException("bad").getMessage());
	}
}
