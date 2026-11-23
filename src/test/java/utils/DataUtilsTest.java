package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * {@link DataUtils}의 숫자·불리언 변환을 검사한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DataUtilsTest {
	@Test
	public void numericConversionsAcceptAnyNumber() {
		// JDBC 드라이버가 NUMERIC/DECIMAL, SMALLINT 등에 대해 돌려주는 타입들.
		Object[] inputs = { new BigDecimal("12"), new BigDecimal("12.00"), BigInteger.valueOf(12), (short)12,
							(byte)12, 12.0f, 12.0, new AtomicLong(12) };
		for ( Object in: inputs ) {
			String msg = "input=" + in + " (" + in.getClass().getSimpleName() + ")";
			assertEquals(12, DataUtils.asInt(in), msg);
			assertEquals(12L, DataUtils.asLong(in), msg);
			assertEquals((short)12, DataUtils.asShort(in), msg);
			assertEquals((byte)12, DataUtils.asByte(in), msg);
			assertEquals(12f, DataUtils.asFloat(in), msg);
			assertEquals(12.0, DataUtils.asDouble(in), msg);
			assertTrue(DataUtils.asBoolean(in), msg);
		}
		assertEquals(12.75, DataUtils.asDouble(new BigDecimal("12.75")));
		assertEquals(12.75f, DataUtils.asFloat(new BigDecimal("12.75")));
		assertFalse(DataUtils.asBoolean(new BigDecimal("0.00")));
		assertFalse(DataUtils.asBoolean((short)0));
	}

	@Test
	public void existingConversionsAreUnchanged() {
		assertEquals(12, DataUtils.asInt(" 12 "));
		assertEquals(1, DataUtils.asInt(true));
		assertEquals(3_000_000_000L, DataUtils.asLong(3_000_000_000L));
		assertEquals(2.5, DataUtils.asDouble("2.5"));
		assertEquals(1.5f, DataUtils.asFloat(1.5));
		assertFalse(DataUtils.asBoolean("no"));
		assertTrue(DataUtils.asBoolean("1"));
		assertTrue(DataUtils.asBoolean("yes"));
		assertTrue(DataUtils.asBoolean(1L));
	}

	@Test
	public void nullRequiresDefaultValue() {
		// 인자 하나짜리는 null을 받지 않는다.
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asShort(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asByte(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asFloat(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asDouble(null));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asBoolean(null));

		// 기본값을 받는 메소드는 null이면 기본값, 아니면 같은 규칙으로 변환한다.
		assertEquals(7, DataUtils.asInt(null, 7));
		assertEquals(7L, DataUtils.asLong(null, 7L));
		assertEquals((short)7, DataUtils.asShort(null, (short)7));
		assertEquals((byte)7, DataUtils.asByte(null, (byte)7));
		assertEquals(7f, DataUtils.asFloat(null, 7f));
		assertEquals(7.0, DataUtils.asDouble(null, 7.0));
		assertTrue(DataUtils.asBoolean(null, true));
		assertEquals(12, DataUtils.asInt("12", 7));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt("12.5", 7));
	}

	@Test
	public void integerConversionsCheckRange() {
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt(3_000_000_000L));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asByte(300.0));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asByte(128));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asShort(1e10));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asShort(new BigDecimal("1e10")));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(new BigInteger("9223372036854775808")));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong("9223372036854775808"));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(1e19));

		// 경계값은 받는다.
		assertEquals(Integer.MAX_VALUE, DataUtils.asInt((long)Integer.MAX_VALUE));
		assertEquals(Integer.MIN_VALUE, DataUtils.asInt("-2147483648"));
		assertEquals((byte)-128, DataUtils.asByte(-128.0));
		assertEquals((short)32767, DataUtils.asShort("32767"));
		assertEquals(Long.MAX_VALUE, DataUtils.asLong(Long.MAX_VALUE));
		assertEquals(Long.MIN_VALUE, DataUtils.asLong(new BigInteger("-9223372036854775808")));
	}

	@Test
	public void integerStringsFollowNumberRules() {
		// 문자열도 숫자와 같은 규칙: 소수 부분이 0이면 받고, 있으면 거부한다.
		assertEquals(1, DataUtils.asInt("1.0"));
		assertEquals(1, DataUtils.asInt(1.0));
		assertEquals(1000, DataUtils.asInt("1e3"));
		assertEquals(-12, DataUtils.asInt(" -12 "));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt("1.5"));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(" "));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong("abc"));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt("NaN"));
		assertEquals(1, DataUtils.asShort(true));
		assertEquals(0, DataUtils.asByte(false));
	}

	@Test
	public void integerConversionsRejectFractions() {
		Object[] inputs = { 12.7, 12.5f, new BigDecimal("12.75"), Double.NaN, Double.POSITIVE_INFINITY };
		for ( Object in: inputs ) {
			String msg = "input=" + in;
			assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt(in), msg);
			assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(in), msg);
			assertThrows(IllegalArgumentException.class, () -> DataUtils.asShort(in), msg);
			assertThrows(IllegalArgumentException.class, () -> DataUtils.asByte(in), msg);
		}
		assertEquals(-3, DataUtils.asInt(-3.0));
		assertEquals(0, DataUtils.asInt(new BigDecimal("0.000")));
	}

	@Test
	public void zeroStringIsFalse() {
		assertFalse(DataUtils.asBoolean("0"));
		assertFalse(DataUtils.asBoolean(" 0 "));
		assertFalse(DataUtils.asBoolean("FALSE"));
	}

	@Test
	public void booleanStringsAreStrict() {
		for ( String str: new String[]{"true", "TRUE", "yes", "Yes", "on", "y", "1", " true "} ) {
			assertTrue(DataUtils.asBoolean(str), str);
		}
		for ( String str: new String[]{"false", "no", "OFF", "n", "0", "", "  "} ) {
			assertFalse(DataUtils.asBoolean(str), str);
		}
		// 목록에 없는 문자열은 오타일 수 있으므로 거부한다.
		for ( String str: new String[]{"flase", "abc", "00", "t", "f", "2"} ) {
			assertThrows(IllegalArgumentException.class, () -> DataUtils.asBoolean(str), str);
		}
		assertTrue(DataUtils.asBoolean(Double.NaN));
		assertTrue(DataUtils.asBoolean(new BigInteger("2")));
		assertFalse(DataUtils.asBoolean(0.0f));
	}

	@Test
	public void nonNumericObjectsAreStillRejected() {
		Object other = new Object();
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInt(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asLong(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asShort(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asByte(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asFloat(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asDouble(other));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asBoolean(other));
	}
}
