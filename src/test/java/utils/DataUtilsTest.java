package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
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
		assertEquals(0, DataUtils.asInt(null));
		assertEquals(12, DataUtils.asInt(" 12 "));
		assertEquals(1, DataUtils.asInt(true));
		assertEquals(3_000_000_000L, DataUtils.asLong(3_000_000_000L));
		assertEquals(2.5, DataUtils.asDouble("2.5"));
		assertEquals(1.5f, DataUtils.asFloat(1.5));
		assertFalse(DataUtils.asBoolean("no"));
		assertTrue(DataUtils.asBoolean("1"));
		assertTrue(DataUtils.asBoolean("yes"));
		assertTrue(DataUtils.asBoolean(1L));
		assertFalse(DataUtils.asBoolean(null));
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
		assertTrue(DataUtils.asBoolean("00"));		// 정확히 "0"만 거짓
		assertFalse(DataUtils.asBoolean("FALSE"));
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

	@Test
	public void asDateConvertsLocalDate() {
		assertEquals(java.sql.Date.valueOf("2026-10-28"), DataUtils.asDate(java.time.LocalDate.of(2026, 10, 28)));
	}

	@Test
	public void asInstantAcceptsDateTimeObjects() {
		Instant ts = Instant.parse("2026-10-28T01:02:03.123456789Z");
		ZoneId zone = ZoneId.systemDefault();

		assertNull(DataUtils.asInstant(null));
		assertEquals(ts, DataUtils.asInstant(ts));
		assertEquals(ts, DataUtils.asInstant("2026-10-28T01:02:03.123456789Z"));
		assertEquals(ts, DataUtils.asInstant(Timestamp.from(ts)));		// 나노초 유지
		assertEquals(Instant.ofEpochMilli(ts.toEpochMilli()), DataUtils.asInstant(Date.from(ts)));
		assertEquals(ts, DataUtils.asInstant(ts.atOffset(ZoneOffset.ofHours(9))));
		assertEquals(ts, DataUtils.asInstant(ts.atZone(zone)));
		assertEquals(ts, DataUtils.asInstant(LocalDateTime.ofInstant(ts, zone)));

		// java.sql.Date.toInstant()는 UnsupportedOperationException을 던지지만 asInstant()는 처리한다.
		java.sql.Date day = java.sql.Date.valueOf("2026-10-28");
		assertEquals(Instant.ofEpochMilli(day.getTime()), DataUtils.asInstant(day));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asInstant(new Object()));
	}

	@Test
	public void asDatetimeAcceptsSameObjectsAsInstant() {
		Instant ts = Instant.parse("2026-10-28T01:02:03Z");
		LocalDateTime local = LocalDateTime.ofInstant(ts, ZoneId.systemDefault());

		assertNull(DataUtils.asDatetime(null));
		assertEquals(local, DataUtils.asDatetime(local));
		assertEquals(local, DataUtils.asDatetime(ts));
		assertEquals(local, DataUtils.asDatetime(ts.atOffset(ZoneOffset.UTC)));
		assertEquals(local, DataUtils.asDatetime(ts.atZone(ZoneOffset.UTC)));
		assertEquals(local, DataUtils.asDatetime("2026-10-28T01:02:03Z"));
		assertEquals(local, DataUtils.asDatetime(Timestamp.from(ts)));
		assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0), DataUtils.asDatetime(java.sql.Date.valueOf("2026-10-28")));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asDatetime(new Object()));
	}

	@Test
	public void asDateAndAsTimeHandleNullAndCommonTypes() {
		assertNull(DataUtils.asDate(null));

		assertNull(DataUtils.asTime(null));
		assertNull(DataUtils.asTime(" "));
		assertEquals(LocalTime.of(13, 5, 9), DataUtils.asTime(LocalTime.of(13, 5, 9)));
		assertEquals(LocalTime.of(13, 5, 9), DataUtils.asTime(java.sql.Time.valueOf("13:05:09")));
		assertEquals(LocalTime.of(13, 5), DataUtils.asTime("13:05"));
		assertEquals(LocalTime.of(13, 5, 9, 250_000_000), DataUtils.asTime("13:05:09.25"));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asTime("13-05-09"));
		assertThrows(IllegalArgumentException.class, () -> DataUtils.asTime(1305));
	}
}
