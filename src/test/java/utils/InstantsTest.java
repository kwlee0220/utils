package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * {@link Instants}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InstantsTest {
	// --- parse: 타임존이 명시된 표기 ---

	@Test
	public void parse_utcSuffix() {
		assertEquals(Instant.parse("2024-07-10T14:40:37Z"), Instants.parse("2024-07-10T14:40:37Z", ZoneId.systemDefault()));
	}

	@Test
	public void parse_utcSuffix_withFraction() {
		assertEquals(Instant.parse("2024-07-10T14:40:37.123Z"), Instants.parse("2024-07-10T14:40:37.123Z", ZoneId.systemDefault()));
	}

	@Test
	public void parse_utcSuffix_minutePrecision() {
		// 초를 생략한 표기도 허용한다.
		assertEquals(Instant.parse("2024-07-10T14:40:00Z"), Instants.parse("2024-07-10T14:40Z", ZoneId.systemDefault()));
	}

	@Test
	public void parse_numericOffset() {
		// '+09:00' 오프셋 표기: UTC로는 05:40:37이다.
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"), Instants.parse("2024-07-10T14:40:37+09:00", ZoneId.systemDefault()));
	}

	@Test
	public void parse_numericOffset_minutePrecision() {
		assertEquals(Instant.parse("2024-07-10T05:40:00Z"), Instants.parse("2024-07-10T14:40+09:00", ZoneId.systemDefault()));
	}

	@Test
	public void parse_negativeOffset() {
		assertEquals(Instant.parse("2024-07-10T19:40:37Z"), Instants.parse("2024-07-10T14:40:37-05:00", ZoneId.systemDefault()));
	}

	@Test
	public void parse_compactOffset() {
		// 콜론 없는 '±hhmm' 오프셋.
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"), Instants.parse("2024-07-10T14:40:37+0900", ZoneId.systemDefault()));
		assertEquals(Instant.parse("2024-07-10T19:40:37.123Z"), Instants.parse("2024-07-10T14:40:37.123-0500", ZoneId.systemDefault()));
		assertEquals(Instant.parse("2024-07-10T05:40:00Z"), Instants.parse("2024-07-10T14:40+0900", ZoneId.systemDefault()));
	}

	@Test
	public void parse_lowercaseZ() {
		assertEquals(Instant.parse("2024-07-10T14:40:37Z"), Instants.parse("2024-07-10T14:40:37z", ZoneId.systemDefault()));
	}

	@Test
	public void parse_malformedOffset_throws() {
		// 오프셋 패턴에 걸리지만 형식이 맞지 않는 표기는 여전히 거부한다.
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2024-07-10T14:40:37+09:0000", ZoneId.systemDefault()));
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2024-07-10T14:40:37+2500", ZoneId.systemDefault()));
	}

	// --- parse: 타임존이 없는 표기 (시스템 기본 시간대로 해석) ---

	@Test
	public void parse_localDateTime() {
		LocalDateTime ldt = LocalDateTime.parse("2024-07-10T14:40:37");
		assertEquals(LocalDateTimes.toInstant(ldt, ZoneId.systemDefault()), Instants.parse("2024-07-10T14:40:37", ZoneId.systemDefault()));
	}

	@Test
	public void parse_spaceSeparated() {
		LocalDateTime ldt = LocalDateTime.parse("2024-07-10T14:40:37");
		assertEquals(LocalDateTimes.toInstant(ldt, ZoneId.systemDefault()), Instants.parse("2024-07-10 14:40:37", ZoneId.systemDefault()));
	}

	@Test
	public void parse_spaceSeparatedVariants() {
		assertEquals(Instant.parse("2024-07-10T14:40:37Z"), Instants.parse("2024-07-10 14:40:37Z", ZoneId.systemDefault()));
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"), Instants.parse("2024-07-10 14:40:37+0900", ZoneId.systemDefault()));
		assertEquals(Instant.parse("2024-07-10T14:40:37.5Z"), Instants.parse("2024-07-10 14:40:37.5Z", ZoneId.systemDefault()));
		assertEquals(LocalDateTimes.toInstant(LocalDateTime.parse("2024-07-10T14:40"), ZoneId.systemDefault()),
					Instants.parse("2024-07-10 14:40", ZoneId.systemDefault()));
		assertEquals(LocalDateTimes.toInstant(LocalDateTime.parse("2024-07-10T14:40:37.5"), ZoneId.systemDefault()),
					Instants.parse("2024-07-10 14:40:37.5", ZoneId.systemDefault()));
	}

	@Test
	public void parse_dateOnly_isStartOfDayInSystemZone() {
		assertEquals(LocalDate.parse("2024-07-10").atStartOfDay(ZoneId.systemDefault()).toInstant(),
					Instants.parse("2024-07-10", ZoneId.systemDefault()));
	}

	@Test
	public void parse_ignoresSurroundingWhitespace() {
		assertEquals(Instant.parse("2024-07-10T14:40:37Z"), Instants.parse("  2024-07-10T14:40:37Z \n", ZoneId.systemDefault()));
		assertEquals(LocalDateTimes.toInstant(LocalDateTime.parse("2024-07-10T14:40:37"), ZoneId.systemDefault()),
					Instants.parse(" 2024-07-10 14:40:37 ", ZoneId.systemDefault()));
	}

	@Test
	public void parse_rejectsOtherSeparators() {
		// 날짜와 시각 사이의 공백은 정확히 하나만 받는다.
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2024-07-10  14:40:37", ZoneId.systemDefault()));
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2024-07-10_14:40:37", ZoneId.systemDefault()));
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2024-7-10", ZoneId.systemDefault()));
	}

	// --- parse: 오류 ---

	@Test
	public void parse_invalid_throws() {
		assertThrows(DateTimeParseException.class, () -> Instants.parse("not-a-datetime", ZoneId.systemDefault()));
	}

	// --- toUtcString / format ---

	@Test
	public void toUtcString_isUtcBased() {
		assertEquals("2024-07-10T05:40:37.000Z", Instants.formatUtc(Instant.parse("2024-07-10T05:40:37Z")));
	}

	@Test
	public void toUtcString_keepsSubMillisecondDigits() {
		assertEquals("2024-07-10T05:40:37.123456Z", Instants.formatUtc(Instant.parse("2024-07-10T05:40:37.123456Z")));
		assertEquals("2024-07-10T05:40:37.123456789Z",
					Instants.formatUtc(Instant.parse("2024-07-10T05:40:37.123456789Z")));
		assertEquals("2024-07-10T05:40:37.120Z", Instants.formatUtc(Instant.parse("2024-07-10T05:40:37.12Z")));
	}

	@Test
	public void subMillisecondInstants_roundTrip() {
		for ( String text: new String[] {"2024-07-10T05:40:37.123456Z", "2024-07-10T05:40:37.000000001Z"} ) {
			Instant instant = Instant.parse(text);
			assertEquals(instant, Instants.parse(Instants.formatUtc(instant), ZoneId.systemDefault()));
			assertEquals(instant, Instants.parse(Instants.format(instant, ZoneId.systemDefault()), ZoneId.systemDefault()));
		}
	}

	// --- 시간대 인자와 기본 시간대 ---

	@Test
	public void toString_withExplicitZone() {
		Instant instant = Instant.parse("2024-07-10T05:40:37.123Z");
		assertEquals("2024-07-10T01:40:37.123-04:00", Instants.format(instant, ZoneId.of("America/New_York")));
		assertEquals("2024-07-10T14:40:37.123+09:00", Instants.format(instant, ZoneId.of("Asia/Seoul")));
		assertEquals("2024-07-10T05:40:37.123Z", Instants.format(instant, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(null, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(instant, null));
	}

	@Test
	public void parse_withDefaultZone() {
		ZoneId newYork = ZoneId.of("America/New_York");
		assertEquals(Instant.parse("2024-07-10T18:40:37Z"), Instants.parse("2024-07-10T14:40:37", newYork));
		assertEquals(Instant.parse("2024-07-10T18:40:00Z"), Instants.parse("2024-07-10 14:40", newYork));
		assertEquals(Instant.parse("2024-07-10T04:00:00Z"), Instants.parse("2024-07-10", newYork));
		// 오프셋이 명시되면 defaultZone은 쓰지 않는다.
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"), Instants.parse("2024-07-10T14:40:37+09:00", newYork));
		assertThrows(IllegalArgumentException.class, () -> Instants.parse("2024-07-10T14:40:37", null));
	}

	@Test
	public void parse_dstGapAndOverlap() {
		ZoneId newYork = ZoneId.of("America/New_York");
		// 없는 시각(02:30, 봄 전환)은 1시간 뒤로 밀려 03:30 EDT가 된다.
		assertEquals(Instant.parse("2024-03-10T07:30:00Z"), Instants.parse("2024-03-10T02:30", newYork));
		// 두 번 있는 시각(01:30, 가을 전환)은 앞쪽 오프셋(EDT, -04:00)으로 해석한다.
		assertEquals(Instant.parse("2024-11-03T05:30:00Z"), Instants.parse("2024-11-03T01:30", newYork));
	}

	@Test
	public void nullArguments() {
		// fromTimestamp만 null을 통과시킨다(JDBC NULL 컬럼).
		assertEquals(null, Instants.fromTimestamp(null));
		assertThrows(IllegalArgumentException.class, () -> Instants.formatUtc(null));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(null, ZoneId.systemDefault()));
		assertThrows(IllegalArgumentException.class, () -> Instants.parse(null, ZoneId.systemDefault()));
		assertThrows(IllegalArgumentException.class, () -> Instants.parse(null, ZoneOffset.UTC));
	}

	// --- 엄격한 날짜 검증, 단일 오프셋, 초 단위 오프셋(외부 검토) ---

	@Test
	public void parse_rejectsInvalidDatesWithOffset() {
		for ( String text: new String[] {"2023-02-29T12:00Z", "2024-02-30T12:00+0900", "2024-01-01T24:00Z",
										"2024-02-30 12:00Z", "2024-04-31T00:00+09:00"} ) {
			assertThrows(DateTimeParseException.class, () -> Instants.parse(text, ZoneId.systemDefault()), text);
		}
		// 오프셋이 없는 표기는 원래부터 거부했다.
		assertThrows(DateTimeParseException.class, () -> Instants.parse("2023-02-29T12:00", ZoneId.systemDefault()));
		// 윤년의 02-29는 받는다.
		assertEquals(Instant.parse("2024-02-29T12:00:00Z"), Instants.parse("2024-02-29T12:00Z", ZoneId.systemDefault()));
	}

	@Test
	public void parse_rejectsDoubleOffsets() {
		for ( String text: new String[] {"2024-01-01T00:00ZZ", "2024-01-01T00:00+09:00+0900", "2024-01-01T00:00+0900+09:00",
										"2024-01-01T00:00Z+09:00"} ) {
			assertThrows(DateTimeParseException.class, () -> Instants.parse(text, ZoneId.systemDefault()), text);
		}
	}

	@Test
	public void parse_acceptsSecondsInOffset() {
		assertEquals(Instant.EPOCH, Instants.parse("1970-01-01T00:00:30+00:00:30", ZoneId.systemDefault()));
		assertEquals(Instant.parse("1900-01-01T00:00:00Z"), Instants.parse("1900-01-01T08:27:52+08:27:52", ZoneId.systemDefault()));
	}

	@Test
	public void toString_keepsSecondsOfOffsetAndRoundTrips() {
		ZoneOffset plus30s = ZoneOffset.ofHoursMinutesSeconds(0, 0, 30);
		assertEquals("1970-01-01T00:00:30.000+00:00:30", Instants.format(Instant.EPOCH, plus30s));
		assertEquals(Instant.EPOCH, Instants.parse(Instants.format(Instant.EPOCH, plus30s), ZoneId.systemDefault()));
		assertEquals("1970-01-01T00:00:30+00:00:30", Instants.format(Instant.EPOCH, plus30s, ChronoUnit.SECONDS));
		assertEquals(Instant.EPOCH, Instants.parse(Instants.format(Instant.EPOCH, plus30s, ChronoUnit.SECONDS), ZoneId.systemDefault()));

		// 일반 시간대도 1908년 이전에는 초 단위 오프셋(지방 평균시)을 쓴다.
		Instant old = Instant.parse("1900-01-01T00:00:00Z");
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		assertEquals("1900-01-01T08:27:52.000+08:27:52", Instants.format(old, seoul));
		assertEquals(old, Instants.parse(Instants.format(old, seoul), ZoneId.systemDefault()));

		// 초가 없는 오프셋의 출력은 그대로다.
		assertEquals("2024-07-10T14:40:37.000+09:00", Instants.format(Instant.parse("2024-07-10T05:40:37Z"), seoul));
	}

	// --- 정밀도 지정 format, toTimestamp ---

	@Test
	public void toString_withFixedPrecision_truncates() {
		Instant instant = Instant.parse("2024-07-10T05:40:37.123456789Z");
		assertEquals("2024-07-10T05:40:37Z", Instants.format(instant, ZoneOffset.UTC, ChronoUnit.SECONDS));
		assertEquals("2024-07-10T05:40:37.123Z", Instants.format(instant, ZoneOffset.UTC, ChronoUnit.MILLIS));
		assertEquals("2024-07-10T05:40:37.123456Z", Instants.format(instant, ZoneOffset.UTC, ChronoUnit.MICROS));
		assertEquals("2024-07-10T05:40:37.123456789Z", Instants.format(instant, ZoneOffset.UTC, ChronoUnit.NANOS));
		assertEquals("2024-07-10T14:40:37.123+09:00",
					Instants.format(instant, ZoneId.of("Asia/Seoul"), ChronoUnit.MILLIS));

		// 반올림하지 않고 버린다.
		Instant almost = Instant.parse("2024-07-10T05:40:37.999999Z");
		assertEquals("2024-07-10T05:40:37Z", Instants.format(almost, ZoneOffset.UTC, ChronoUnit.SECONDS));
		assertEquals("2024-07-10T05:40:37.999Z", Instants.format(almost, ZoneOffset.UTC, ChronoUnit.MILLIS));

		// 고정 자릿수는 값이 0이어도 채운다.
		assertEquals("2024-07-10T05:40:37.000000Z",
					Instants.format(Instant.parse("2024-07-10T05:40:37Z"), ZoneOffset.UTC, ChronoUnit.MICROS));
	}

	@Test
	public void toString_withFixedPrecision_rejectsOtherUnitsAndNulls() {
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");
		assertThrows(IllegalArgumentException.class, () -> Instants.format(instant, ZoneOffset.UTC, ChronoUnit.MINUTES));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(instant, ZoneOffset.UTC, ChronoUnit.HALF_DAYS));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(instant, ZoneOffset.UTC, null));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(null, ZoneOffset.UTC, ChronoUnit.MILLIS));
		assertThrows(IllegalArgumentException.class, () -> Instants.format(instant, null, ChronoUnit.MILLIS));
	}

	@Test
	public void toTimestamp_keepsNanosAndRoundTrips() {
		Instant instant = Instant.parse("2024-07-10T05:40:37.123456789Z");
		Timestamp ts = Instants.toTimestamp(instant);
		assertEquals(123_456_789, ts.getNanos());
		assertEquals(instant, Instants.fromTimestamp(ts));
		assertEquals(null, Instants.toTimestamp(null));
	}

	// --- round-trip ---

	@Test
	public void toUtcString_roundTrips() {
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");
		assertEquals(instant, Instants.parse(Instants.formatUtc(instant), ZoneId.systemDefault()));
	}

	@Test
	public void toString_roundTrips() {
		// toZonedString은 시스템 기본 시간대의 오프셋을 포함하므로 fromString이 그대로 복원할 수 있어야 한다.
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");
		assertEquals(instant, Instants.parse(Instants.format(instant, ZoneId.systemDefault()), ZoneId.systemDefault()));
	}

	@Test
	public void parse_regionIdSuffix() {
		// ZonedDateTime.toString() 표기: 오프셋이 우선하고 지역 ID는 검사만 한다.
		java.time.ZonedDateTime zdt = java.time.ZonedDateTime.of(2024, 7, 10, 14, 40, 37, 123_456_789,
																ZoneId.of("Asia/Seoul"));
		assertEquals(zdt.toInstant(), Instants.parse(zdt.toString(), ZoneId.systemDefault()));
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"),
								Instants.parse("2024-07-10T14:40:37+09:00[Asia/Seoul]", ZoneOffset.UTC));
		// 오프셋이 없으면 지역 ID의 시각으로 해석한다(defaultZone은 쓰지 않는다).
		assertEquals(Instant.parse("2024-07-10T05:40:00Z"),
								Instants.parse("2024-07-10T14:40[Asia/Seoul]", ZoneOffset.UTC));
		assertEquals(Instant.parse("2024-07-09T15:00:00Z"),
								Instants.parse("2024-07-10[Asia/Seoul]", ZoneOffset.UTC));
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"),
								Instants.parse(" 2024-07-10 14:40:37[Asia/Seoul] ", ZoneOffset.UTC));
	}

	@Test
	public void parse_regionIdSuffix_invalid() {
		assertThrows(java.time.format.DateTimeParseException.class,
								() -> Instants.parse("2024-07-10T14:40:37+09:00[Asia/Nowhere]", ZoneId.systemDefault()));
		assertThrows(java.time.format.DateTimeParseException.class,
								() -> Instants.parse("2024-07-10T14:40:37[Asia/Seoul][UTC]", ZoneId.systemDefault()));
		assertThrows(java.time.format.DateTimeParseException.class,
								() -> Instants.parse("2024-07-10T14:40:37[]", ZoneId.systemDefault()));
		assertThrows(java.time.format.DateTimeParseException.class, () -> Instants.parse("[Asia/Seoul]", ZoneId.systemDefault()));
	}
}
