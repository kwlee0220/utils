package utils.json;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.databind.JsonNode;

import utils.Preconditions;

/**
 * JSON 스칼라 노드를 Java 수치·불리언 값으로 <b>값을 잃지 않고</b> 바꾸는 변환 함수 모음.
 * <p>
 * Jackson의 {@code asInt()}·{@code asDouble()}·{@code asBoolean()}은 바꿀 수 없는 값을 0·false로 만들거나
 * 소수부를 버리고 범위를 넘으면 넘침(overflow)을 일으킨다({@code "abc"}→0, 3.9→3, 3000000000→-1294967296).
 * 이 클래스는 그런 경우를 모두 {@link JacksonDeserializationException}으로 거부한다.
 * <p>
 * 받아들이는 입력은 {@link Policy}로 정한다.
 * <table border="1">
 *   <caption>정책별 허용 입력</caption>
 *   <tr><th></th><th>{@link Policy#STRICT}</th><th>{@link Policy#TEXT_TOLERANT}</th></tr>
 *   <tr><td>정수 타입</td><td>범위 안의 JSON 정수</td><td>+ 소수부가 0인 실수({@code 3.0}), 숫자 문자열({@code "42"}, {@code "3.0"})</td></tr>
 *   <tr><td>실수 타입</td><td>유한한 JSON 숫자</td><td>+ 숫자 문자열</td></tr>
 *   <tr><td>{@code boolean}</td><td>JSON 불리언, 문자열 {@code "true"}/{@code "false"}(대소문자 무관)</td><td>+ 문자열 {@code "1"}/{@code "0"}</td></tr>
 * </table>
 * 숫자 문자열은 {@link BigDecimal} 문법(부호, 소수점, 지수)만 받는다. {@code "NaN"}, {@code "Infinity"}, 16진수,
 * Java 접미사({@code "1f"})는 받지 않는다. 앞뒤 공백은 무시한다.
 * <p>
 * 노드는 {@code null}이 아니어야 하고, JSON {@code null}은 타입 불일치로 거부한다. 필드가 없거나 JSON {@code null}인
 * 경우를 어떻게 처리할지는 호출자가 먼저 정한다.
 * <p>
 * 예외 메시지에는 기대한 타입과 JSON 노드 종류만 넣고 값은 넣지 않는다(예: {@code "int expected, but string"},
 * {@code "out of int range"}). 호출자는 필드 이름이나 데이터 타입 같은 맥락을 앞에 붙여 쓴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JsonScalars {
	/**
	 * JSON 노드의 타입과 Java 값 타입이 다를 때 어디까지 받아들일지 정한다.
	 */
	public enum Policy {
		/** JSON 타입을 그대로 쓴다. 불리언만 예외적으로 {@code "true"}/{@code "false"} 문자열도 받는다. */
		STRICT,
		/** {@link #STRICT}에 더해, 숫자 문자열·소수부가 0인 실수(정수 타입)·{@code "1"}/{@code "0"}(불리언)을 받는다. */
		TEXT_TOLERANT,
	}

	private static final BigInteger BYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);
	private static final BigInteger BYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);
	private static final BigInteger SHORT_MIN = BigInteger.valueOf(Short.MIN_VALUE);
	private static final BigInteger SHORT_MAX = BigInteger.valueOf(Short.MAX_VALUE);
	private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
	private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
	private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
	private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

	private JsonScalars() {
		throw new AssertionError("Should not be invoked!!: class=" + JsonScalars.class.getName());
	}

	// --- 정수 ---

	/**
	 * {@code byte} 값으로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	정수가 아니거나 범위를 넘는 경우.
	 */
	public static byte toByte(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toRangedInteger(node, policy, "byte", BYTE_MIN, BYTE_MAX).byteValue();
	}

	/**
	 * {@code short} 값으로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	정수가 아니거나 범위를 넘는 경우.
	 */
	public static short toShort(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toRangedInteger(node, policy, "short", SHORT_MIN, SHORT_MAX).shortValue();
	}

	/**
	 * {@code int} 값으로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	정수가 아니거나 범위를 넘는 경우.
	 */
	public static int toInt(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toRangedInteger(node, policy, "int", INT_MIN, INT_MAX).intValue();
	}

	/**
	 * {@code long} 값으로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	정수가 아니거나 범위를 넘는 경우.
	 */
	public static long toLong(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toRangedInteger(node, policy, "long", LONG_MIN, LONG_MAX).longValue();
	}

	/**
	 * 크기 제한이 없는 정수({@link BigInteger})로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	정수가 아닌 경우.
	 */
	public static BigInteger toBigInteger(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toRangedInteger(node, policy, "integer", null, null);
	}

	// --- 실수 ---

	/**
	 * {@code float} 값으로 바꾼다. 유한하고 {@code float} 범위 안이어야 한다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	숫자가 아니거나, 무한대·{@code NaN}이거나, 범위를 넘는 경우.
	 */
	public static float toFloat(JsonNode node, Policy policy) throws JacksonDeserializationException {
		double value = toFiniteDouble(node, policy, "float");
		if ( Math.abs(value) > Float.MAX_VALUE ) {
			throw new JacksonDeserializationException("out of float range");
		}
		return (float)value;
	}

	/**
	 * {@code double} 값으로 바꾼다. 유한해야 한다({@code 1e400}처럼 범위를 넘어 무한대가 되는 값은 거부한다).
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	숫자가 아니거나, 무한대·{@code NaN}인 경우.
	 */
	public static double toDouble(JsonNode node, Policy policy) throws JacksonDeserializationException {
		return toFiniteDouble(node, policy, "double");
	}

	/**
	 * {@link BigDecimal} 값으로 바꾼다. 노드가 가진 정밀도를 그대로 쓴다.
	 * <p>
	 * 매퍼가 JSON 실수를 {@code double}로 읽었다면(Jackson 기본 설정) 이미 그 정밀도로 줄어 있다. 더 높은 정밀도가
	 * 필요하면 {@link Policy#TEXT_TOLERANT}로 숫자 문자열을 받는다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	숫자가 아니거나 무한대·{@code NaN}인 경우.
	 */
	public static BigDecimal toDecimal(JsonNode node, Policy policy) throws JacksonDeserializationException {
		checkNode(node);
		if ( node.isNumber() ) {
			if ( (node.isDouble() || node.isFloat()) && !Double.isFinite(node.doubleValue()) ) {
				throw new JacksonDeserializationException("finite decimal expected");
			}
			return node.decimalValue();
		}
		return parseNumericText(node, policy, "decimal");
	}

	// --- 불리언 ---

	/**
	 * {@code boolean} 값으로 바꾼다.
	 *
	 * @param node		변환할 노드({@code null} 불가).
	 * @param policy	허용 정책. {@link Policy#TEXT_TOLERANT}이면 {@code "1"}/{@code "0"}도 받는다.
	 * @return	변환한 값.
	 * @throws JacksonDeserializationException	불리언으로 읽을 수 없는 경우.
	 */
	public static boolean toBoolean(JsonNode node, Policy policy) throws JacksonDeserializationException {
		checkNode(node);
		if ( node.isBoolean() ) {
			return node.booleanValue();
		}
		if ( node.isTextual() ) {
			String text = node.textValue().trim();
			if ( "true".equalsIgnoreCase(text) ) {
				return true;
			}
			if ( "false".equalsIgnoreCase(text) ) {
				return false;
			}
			if ( policy == Policy.TEXT_TOLERANT ) {
				if ( "1".equals(text) ) {
					return true;
				}
				if ( "0".equals(text) ) {
					return false;
				}
			}
		}
		throw mismatch("boolean", node);
	}

	// --- 내부 ---

	private static BigInteger toRangedInteger(JsonNode node, Policy policy, String typeName,
												BigInteger min, BigInteger max) throws JacksonDeserializationException {
		checkNode(node);

		BigInteger value;
		if ( node.isIntegralNumber() ) {
			value = node.bigIntegerValue();
		}
		else if ( node.isNumber() ) {
			// 3.0처럼 소수부가 0인 실수는 TEXT_TOLERANT에서만 받는다. 3.9·무한대·NaN은 늘 거부한다.
			if ( policy != Policy.TEXT_TOLERANT || !node.canConvertToExactIntegral() ) {
				throw mismatch(typeName, node);
			}
			value = node.decimalValue().toBigIntegerExact();
		}
		else {
			BigDecimal decimal = parseNumericText(node, policy, typeName);
			try {
				value = decimal.toBigIntegerExact();
			}
			catch ( ArithmeticException e ) {
				throw mismatch(typeName, node);
			}
		}

		if ( (min != null && value.compareTo(min) < 0) || (max != null && value.compareTo(max) > 0) ) {
			throw new JacksonDeserializationException(String.format("out of %s range", typeName));
		}
		return value;
	}

	private static double toFiniteDouble(JsonNode node, Policy policy, String typeName)
		throws JacksonDeserializationException {
		checkNode(node);
		double value = node.isNumber() ? node.doubleValue() : parseNumericText(node, policy, typeName).doubleValue();
		if ( !Double.isFinite(value) ) {
			throw new JacksonDeserializationException(String.format("finite %s expected", typeName));
		}
		return value;
	}

	/** {@link Policy#TEXT_TOLERANT}이고 노드가 숫자 문자열이면 {@link BigDecimal}로 읽는다. 그 밖은 타입 불일치. */
	private static BigDecimal parseNumericText(JsonNode node, Policy policy, String typeName)
		throws JacksonDeserializationException {
		if ( policy == Policy.TEXT_TOLERANT && node.isTextual() ) {
			try {
				return new BigDecimal(node.textValue().trim());
			}
			catch ( NumberFormatException e ) {
				// 원인 예외의 메시지에는 값이 들어가므로 메시지에는 쓰지 않고 cause로만 남긴다.
				throw new JacksonDeserializationException(String.format("%s expected, but non-numeric string",
																		typeName), e);
			}
		}
		throw mismatch(typeName, node);
	}

	private static void checkNode(JsonNode node) {
		Preconditions.checkNotNullArgument(node, "node is null");
	}

	private static JacksonDeserializationException mismatch(String expected, JsonNode node) {
		return new JacksonDeserializationException(String.format("%s expected, but %s", expected,
																node.getNodeType().name().toLowerCase()));
	}
}
