package utils.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.databind.JsonNode;

import utils.json.JsonScalars.Policy;

/**
 * {@link JsonScalars}가 값을 잃는 변환을 거부하고, 정책에 따라 문자열·손실 없는 실수를 받는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JsonScalarsTest {
	private static JsonNode json(String text) throws IOException {
		return JacksonUtils.MAPPER.readTree(text);
	}

	private static void rejected(String expectedMessage, Executable call) {
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class, call);
		assertEquals(expectedMessage, e.getMessage());
	}

	@Test
	public void integers_strict() throws IOException {
		assertEquals(42, JsonScalars.toInt(json("42"), Policy.STRICT));
		assertEquals(-7L, JsonScalars.toLong(json("-7"), Policy.STRICT));
		assertEquals((short)300, JsonScalars.toShort(json("300"), Policy.STRICT));
		assertEquals((byte)-128, JsonScalars.toByte(json("-128"), Policy.STRICT));
		assertEquals(new BigInteger("123456789012345678901234567890"),
					JsonScalars.toBigInteger(json("123456789012345678901234567890"), Policy.STRICT));

		// Jackson asInt()는 아래를 각각 0, 3, -1294967296으로 바꾼다.
		rejected("int expected, but string", () -> JsonScalars.toInt(json("\"abc\""), Policy.STRICT));
		rejected("int expected, but number", () -> JsonScalars.toInt(json("3.9"), Policy.STRICT));
		rejected("out of int range", () -> JsonScalars.toInt(json("3000000000"), Policy.STRICT));
		// STRICT는 숫자 문자열과 손실 없는 실수도 받지 않는다.
		rejected("int expected, but string", () -> JsonScalars.toInt(json("\"42\""), Policy.STRICT));
		rejected("int expected, but number", () -> JsonScalars.toInt(json("3.0"), Policy.STRICT));
		rejected("out of byte range", () -> JsonScalars.toByte(json("128"), Policy.STRICT));
		rejected("out of short range", () -> JsonScalars.toShort(json("40000"), Policy.STRICT));
		rejected("long expected, but boolean", () -> JsonScalars.toLong(json("true"), Policy.STRICT));
		rejected("integer expected, but null", () -> JsonScalars.toBigInteger(json("null"), Policy.STRICT));
	}

	@Test
	public void integers_textTolerant() throws IOException {
		assertEquals(42, JsonScalars.toInt(json("\"42\""), Policy.TEXT_TOLERANT));
		assertEquals(42, JsonScalars.toInt(json("\" +42 \""), Policy.TEXT_TOLERANT));
		assertEquals(3, JsonScalars.toInt(json("3.0"), Policy.TEXT_TOLERANT));
		assertEquals(3, JsonScalars.toInt(json("\"3.0\""), Policy.TEXT_TOLERANT));
		assertEquals(new BigInteger("99999999999999999999"),
					JsonScalars.toBigInteger(json("\"99999999999999999999\""), Policy.TEXT_TOLERANT));

		rejected("int expected, but number", () -> JsonScalars.toInt(json("3.9"), Policy.TEXT_TOLERANT));
		rejected("int expected, but string", () -> JsonScalars.toInt(json("\"3.9\""), Policy.TEXT_TOLERANT));
		rejected("int expected, but non-numeric string", () -> JsonScalars.toInt(json("\"abc\""), Policy.TEXT_TOLERANT));
		rejected("out of int range", () -> JsonScalars.toInt(json("\"3000000000\""), Policy.TEXT_TOLERANT));
		// Java 접미사·16진수·NaN은 숫자 문자열로 보지 않는다.
		rejected("int expected, but non-numeric string", () -> JsonScalars.toInt(json("\"1f\""), Policy.TEXT_TOLERANT));
		rejected("long expected, but non-numeric string", () -> JsonScalars.toLong(json("\"0x10\""), Policy.TEXT_TOLERANT));
		rejected("int expected, but object", () -> JsonScalars.toInt(json("{}"), Policy.TEXT_TOLERANT));
	}

	@Test
	public void reals() throws IOException {
		assertEquals(1.5, JsonScalars.toDouble(json("1.5"), Policy.STRICT));
		assertEquals(3.0, JsonScalars.toDouble(json("3"), Policy.STRICT));
		assertEquals(1.5f, JsonScalars.toFloat(json("1.5"), Policy.STRICT));
		rejected("double expected, but string", () -> JsonScalars.toDouble(json("\"1.5\""), Policy.STRICT));
		rejected("finite double expected", () -> JsonScalars.toDouble(json("1e400"), Policy.STRICT));
		rejected("out of float range", () -> JsonScalars.toFloat(json("1e300"), Policy.STRICT));

		assertEquals(1.5, JsonScalars.toDouble(json("\"1.5\""), Policy.TEXT_TOLERANT));
		rejected("double expected, but non-numeric string",
				() -> JsonScalars.toDouble(json("\"NaN\""), Policy.TEXT_TOLERANT));
		rejected("double expected, but non-numeric string",
				() -> JsonScalars.toDouble(json("\"Infinity\""), Policy.TEXT_TOLERANT));
		rejected("finite double expected", () -> JsonScalars.toDouble(json("\"1e400\""), Policy.TEXT_TOLERANT));
	}

	@Test
	public void decimals() throws IOException {
		assertEquals(new BigDecimal("0.5"), JsonScalars.toDecimal(json("0.5"), Policy.STRICT));
		assertEquals(new BigInteger("123456789012345678901234567890"),
					JsonScalars.toDecimal(json("123456789012345678901234567890"), Policy.STRICT).toBigIntegerExact());
		// 고정밀 실수는 문자열로 넣어야 그대로 보존된다(매퍼가 JSON 실수를 double로 읽음).
		assertEquals(new BigDecimal("12345678901234567890.5"),
					JsonScalars.toDecimal(json("\"12345678901234567890.5\""), Policy.TEXT_TOLERANT));
		rejected("decimal expected, but string", () -> JsonScalars.toDecimal(json("\"0.5\""), Policy.STRICT));
		rejected("finite decimal expected", () -> JsonScalars.toDecimal(json("1e400"), Policy.STRICT));
	}

	@Test
	public void booleans() throws IOException {
		assertTrue(JsonScalars.toBoolean(json("true"), Policy.STRICT));
		// (b)안: STRICT도 "true"/"false" 문자열은 받는다.
		assertFalse(JsonScalars.toBoolean(json("\"FALSE\""), Policy.STRICT));
		rejected("boolean expected, but string", () -> JsonScalars.toBoolean(json("\"1\""), Policy.STRICT));
		rejected("boolean expected, but number", () -> JsonScalars.toBoolean(json("1"), Policy.STRICT));

		assertTrue(JsonScalars.toBoolean(json("\"1\""), Policy.TEXT_TOLERANT));
		assertFalse(JsonScalars.toBoolean(json("\"0\""), Policy.TEXT_TOLERANT));
		rejected("boolean expected, but string", () -> JsonScalars.toBoolean(json("\"yes\""), Policy.TEXT_TOLERANT));
		rejected("boolean expected, but number", () -> JsonScalars.toBoolean(json("1"), Policy.TEXT_TOLERANT));
	}

	@Test
	public void nullNodeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> JsonScalars.toInt(null, Policy.STRICT));
	}
}
