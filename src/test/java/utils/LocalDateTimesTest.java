package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link LocalDateTimes}, {@link LocalDates}, {@link LocalTimes}의 문자열 형식, {@code null} 거부,
 * 서머타임 전환 시각 처리를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDateTimesTest {
	private TimeZone m_savedZone;

	@BeforeEach
	public void saveDefaultZone() {
		m_savedZone = TimeZone.getDefault();
	}

	@AfterEach
	public void restoreDefaultZone() {
		TimeZone.setDefault(m_savedZone);
	}

	// --- 문자열 형식(#5) ---

	@Test
	public void toString_precisionDependsOnValue() {
		assertEquals("2024-07-10T14:40", LocalDateTimes.toString(LocalDateTime.of(2024, 7, 10, 14, 40)));
		assertEquals("2024-07-10T14:40:37", LocalDateTimes.toString(LocalDateTime.of(2024, 7, 10, 14, 40, 37)));
		assertEquals("2024-07-10T14:40:37.120",
					LocalDateTimes.toString(LocalDateTime.of(2024, 7, 10, 14, 40, 37, 120_000_000)));
		assertEquals("2024-07-10T14:40:37.123456",
					LocalDateTimes.toString(LocalDateTime.of(2024, 7, 10, 14, 40, 37, 123_456_000)));
	}

	@Test
	public void parse_acceptsOptionalSecondsAndFraction() {
		assertEquals(LocalDateTime.of(2024, 7, 10, 14, 40), LocalDateTimes.parse("2024-07-10T14:40"));
		assertEquals(LocalDateTime.of(2024, 7, 10, 14, 40, 37, 123_456_000),
					LocalDateTimes.parse("2024-07-10T14:40:37.123456"));
	}

	// --- null 거부(#6) ---

	@Test
	public void nullArguments_areRejected() {
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.fromInstant(null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.fromInstant(Instant.EPOCH, null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.toInstant(null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.toEpochMilli(null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.parse(null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.toString(null));

		assertThrows(IllegalArgumentException.class, () -> LocalDates.toEpochMilli(null));

		assertThrows(IllegalArgumentException.class, () -> LocalTimes.parse(null));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.toMillisOfDay(null));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.toString(null));
	}

	// --- 시간대를 명시하는 변환(#9) ---

	@Test
	public void localDateTime_zoneExplicitConversions() {
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		LocalDateTime ldt = LocalDateTime.of(2024, 7, 10, 14, 40, 37);
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");

		assertEquals(instant, LocalDateTimes.toInstant(ldt, seoul));
		assertEquals(instant.toEpochMilli(), LocalDateTimes.toEpochMilli(ldt, seoul));
		assertEquals(ldt, LocalDateTimes.fromEpochMilli(instant.toEpochMilli(), seoul));
		assertEquals(ldt, LocalDateTimes.fromInstant(instant, seoul));

		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.toInstant(ldt, null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.toInstant(null, seoul));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.fromEpochMilli(0, null));
	}

	@Test
	public void localDateTime_conveniencesMatchZoneExplicitForms() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
		ZoneId system = ZoneId.systemDefault();
		LocalDateTime ldt = LocalDateTime.of(2024, 7, 10, 14, 40, 37, 123_000_000);
		long millis = 1_720_622_437_123L;

		assertEquals(LocalDateTimes.toInstant(ldt, system), LocalDateTimes.toInstant(ldt));
		assertEquals(LocalDateTimes.toEpochMilli(ldt, system), LocalDateTimes.toEpochMilli(ldt));
		assertEquals(LocalDateTimes.fromEpochMilli(millis, system), LocalDateTimes.fromEpochMilli(millis));
	}

	@Test
	public void localDate_zoneExplicitConversions() {
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		LocalDate date = LocalDate.of(2024, 7, 10);
		Instant seoulMidnight = Instant.parse("2024-07-09T15:00:00Z");

		assertEquals(seoulMidnight, LocalDates.toInstant(date, seoul));
		assertEquals(seoulMidnight.toEpochMilli(), LocalDates.toEpochMilli(date, seoul));
		// 같은 시각이라도 시간대에 따라 날짜가 다르다.
		assertEquals(date, LocalDates.fromInstant(seoulMidnight, seoul));
		assertEquals(LocalDate.of(2024, 7, 9), LocalDates.fromInstant(seoulMidnight, ZoneOffset.UTC));
		assertEquals(date, LocalDates.fromEpochMilli(seoulMidnight.toEpochMilli(), seoul));

		assertThrows(IllegalArgumentException.class, () -> LocalDates.toInstant(date, null));
		assertThrows(IllegalArgumentException.class, () -> LocalDates.fromInstant(null, seoul));
	}

	@Test
	public void localDate_conveniencesMatchZoneExplicitForms() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
		ZoneId system = ZoneId.systemDefault();
		LocalDate date = LocalDate.of(2024, 7, 10);
		long millis = 1_720_622_437_123L;

		assertEquals(LocalDates.toEpochMilli(date, system), LocalDates.toEpochMilli(date));
		assertEquals(LocalDates.fromEpochMilli(millis, system), LocalDates.fromEpochMilli(millis));
	}

	// --- 왕복 불가능한 경우의 문서화된 동작 ---

	@Test
	public void localDate_skippedDate_mapsToNextDate() {
		// Pacific/Apia는 2011-12-30을 건너뛰었다(날짜변경선 이동). 그날의 "시작"은 다음 날짜에 속한다.
		ZoneId apia = ZoneId.of("Pacific/Apia");
		Instant start = LocalDates.toInstant(LocalDate.of(2011, 12, 30), apia);
		assertEquals(LocalDate.of(2011, 12, 31), LocalDates.fromInstant(start, apia));
	}

	@Test
	public void epochMilli_truncatesSubMillisAndRejectsFarValues() {
		LocalDateTime ldt = LocalDateTime.of(2024, 1, 1, 0, 0, 0, 123_456_789);
		long millis = LocalDateTimes.toEpochMilli(ldt, ZoneOffset.UTC);
		assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0, 123_000_000), LocalDateTimes.fromEpochMilli(millis, ZoneOffset.UTC));

		assertThrows(ArithmeticException.class, () -> LocalDateTimes.toEpochMilli(LocalDateTime.MAX, ZoneOffset.UTC));
		assertThrows(ArithmeticException.class, () -> LocalDates.toEpochMilli(LocalDate.MAX, ZoneOffset.UTC));
	}

	// --- LocalTimes(#12) ---

	@Test
	public void localTimes_millisOfDay() {
		assertEquals(0L, LocalTimes.toMillisOfDay(LocalTime.MIDNIGHT));
		assertEquals(86_399_999L, LocalTimes.toMillisOfDay(LocalTime.MAX));		// 밀리초 미만은 버린다
		assertEquals(LocalTime.of(9, 0, 30, 500_000_000), LocalTimes.fromMillisOfDay(32_430_500L));
		assertEquals(LocalTime.of(23, 59, 59, 999_000_000), LocalTimes.fromMillisOfDay(86_399_999L));
		// 범위 밖은 밀리초 기준 메시지의 IllegalArgumentException. 이전에는 나노초 값을 담은 DateTimeException이었다.
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
													() -> LocalTimes.fromMillisOfDay(86_400_000L));
		assertEquals("millis-of-day out of range [0, 86400000): 86400000", e.getMessage());
		e = assertThrows(IllegalArgumentException.class, () -> LocalTimes.fromMillisOfDay(-1L));
		assertEquals("millis-of-day out of range [0, 86400000): -1", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.fromMillisOfDay(Long.MAX_VALUE));
	}

	@Test
	public void localTimes_parseAndToString() {
		assertEquals(LocalTime.of(9, 0), LocalTimes.parse("09:00"));
		assertEquals(LocalTime.of(9, 0, 30, 500_000_000), LocalTimes.parse("09:00:30.5"));
		assertEquals("09:00", LocalTimes.toString(LocalTime.of(9, 0)));
		assertEquals("09:00:30.500", LocalTimes.toString(LocalTime.of(9, 0, 30, 500_000_000)));
	}

	// --- 서머타임 전환(#7) ---

	@Test
	public void toInstant_dstGapMovesForward_overlapTakesEarlierOffset() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

		// 없는 시각 02:30(봄 전환)은 1시간 뒤인 03:30 EDT(-04:00)가 된다.
		LocalDateTime gap = LocalDateTime.of(2024, 3, 10, 2, 30);
		assertEquals(Instant.parse("2024-03-10T07:30:00Z"), LocalDateTimes.toInstant(gap));
		assertEquals(Instant.parse("2024-03-10T07:30:00Z").toEpochMilli(), LocalDateTimes.toEpochMilli(gap));

		// 두 번 있는 시각 01:30(가을 전환)은 앞쪽 오프셋인 EDT(-04:00)로 해석한다.
		LocalDateTime overlap = LocalDateTime.of(2024, 11, 3, 1, 30);
		assertEquals(Instant.parse("2024-11-03T05:30:00Z"), LocalDateTimes.toInstant(overlap));
	}

	@Test
	public void startOfDay_withoutMidnight_isFirstValidTime() {
		// 칠레(America/Santiago)는 2024-09-08 00:00에 01:00으로 바뀌어 그날 자정이 없다.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Santiago"));
		LocalDate date = LocalDate.of(2024, 9, 8);

		long expected = LocalDateTime.of(date, LocalTime.of(1, 0)).toInstant(ZoneOffset.ofHours(-3)).toEpochMilli();
		assertEquals(expected, LocalDates.toEpochMilli(date));
	}
}
