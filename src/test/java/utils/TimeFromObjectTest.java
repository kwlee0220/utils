package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 시간 클래스의 {@code from(Object, ZoneId)} 메소드({@link Instants#from(Object, ZoneId)},
 * {@link LocalDateTimes#from(Object, ZoneId)}, {@link LocalDates#from(Object, ZoneId)},
 * {@link LocalTimes#from(Object, ZoneId)})의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimeFromObjectTest {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate DATE = LocalDate.of(2026, 10, 28);
	private static final Instant START_OF_DAY = Instant.parse("2026-10-27T15:00:00Z");	// 서울 2026-10-28 00:00
	private static final Instant NOON = Instant.parse("2026-10-28T03:00:00Z");			// 서울 12:00

	@Test
	public void instantFrom_acceptsDateTimeObjects() {
		Instant ts = Instant.parse("2026-10-28T01:02:03.123456789Z");

		assertNull(Instants.from(null, SEOUL));
		assertEquals(ts, Instants.from(ts, SEOUL));
		assertEquals(ts, Instants.from("2026-10-28T01:02:03.123456789Z", SEOUL));
		assertEquals(ts, Instants.from(Timestamp.from(ts), SEOUL));		// 나노초 유지
		assertEquals(Instant.ofEpochMilli(ts.toEpochMilli()), Instants.from(Date.from(ts), SEOUL));
		assertEquals(ts, Instants.from(ts.atOffset(ZoneOffset.ofHours(9)), SEOUL));
		assertEquals(ts, Instants.from(ts.atZone(SEOUL), SEOUL));
		assertEquals(ts, Instants.from(LocalDateTime.ofInstant(ts, SEOUL), SEOUL));
		assertEquals(NOON, Instants.from(NOON.toEpochMilli(), SEOUL));		// Long은 epoch 밀리초

		// LocalDate와 java.sql.Date는 zone에서 그날이 시작되는 시각이다.
		// (java.sql.Date.toInstant()는 UnsupportedOperationException을 던지지만 from()은 처리한다.)
		assertEquals(START_OF_DAY, Instants.from(DATE, SEOUL));
		assertEquals(START_OF_DAY, Instants.from(java.sql.Date.valueOf(DATE), SEOUL));
		assertEquals(Instant.parse("2026-10-28T00:00:00Z"), Instants.from(DATE, ZoneOffset.UTC));

		// 날짜가 없는 값은 받지 않는다(java.sql.Time은 1970-01-01로 채워진 Date이다).
		assertThrows(IllegalArgumentException.class, () -> Instants.from(java.sql.Time.valueOf("09:00:00"), SEOUL));
		assertThrows(IllegalArgumentException.class, () -> Instants.from(LocalTime.NOON, SEOUL));
		assertThrows(IllegalArgumentException.class, () -> Instants.from(new Object(), SEOUL));
		assertThrows(IllegalArgumentException.class, () -> Instants.from(1305, SEOUL));	// Integer는 받지 않는다
	}

	@Test
	public void instantFrom_strings() {
		// 빈 문자열은 null이고, 형식 오류는 IllegalArgumentException으로 감싼다.
		assertNull(Instants.from("", SEOUL));
		assertNull(Instants.from("  ", SEOUL));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Instants.from("abc", SEOUL));
		assertTrue(e.getCause() instanceof DateTimeParseException);
		assertThrows(IllegalArgumentException.class, () -> Instants.from("2024-02-30T00:00:00Z", SEOUL));
		// 앞뒤 공백은 무시하고, 오프셋이 없는 표기는 zone으로 해석한다.
		assertEquals(Instant.parse("2026-10-28T01:02:03Z"), Instants.from(" 2026-10-28T01:02:03Z ", SEOUL));
		assertEquals(NOON, Instants.from("2026-10-28 12:00", SEOUL));
	}

	@Test
	public void localDateTimeFrom() {
		Instant ts = Instant.parse("2026-10-28T01:02:03Z");
		LocalDateTime local = LocalDateTime.ofInstant(ts, SEOUL);

		assertNull(LocalDateTimes.from(null, SEOUL));
		assertNull(LocalDateTimes.from(" ", SEOUL));
		assertEquals(local, LocalDateTimes.from(local, SEOUL));
		assertEquals(local, LocalDateTimes.from(ts, SEOUL));
		assertEquals(local, LocalDateTimes.from(ts.atOffset(ZoneOffset.UTC), SEOUL));
		assertEquals(local, LocalDateTimes.from(ts.atZone(ZoneOffset.UTC), SEOUL));
		assertEquals(local, LocalDateTimes.from("2026-10-28T01:02:03Z", SEOUL));
		assertEquals(local, LocalDateTimes.from(Timestamp.from(ts), SEOUL));
		assertEquals(LocalDateTime.of(2026, 10, 28, 12, 0), LocalDateTimes.from(NOON.toEpochMilli(), SEOUL));
		// 날짜 값은 그날 자정이다.
		assertEquals(DATE.atStartOfDay(), LocalDateTimes.from(DATE, SEOUL));
		assertEquals(DATE.atStartOfDay(), LocalDateTimes.from(java.sql.Date.valueOf(DATE), SEOUL));
		// 같은 시각이라도 zone에 따라 지역 시각이 다르다.
		assertEquals(LocalDateTime.of(2026, 10, 28, 3, 0), LocalDateTimes.from(NOON, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.from("abc", SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.from(new Object(), SEOUL));
	}

	@Test
	public void localDateFrom() {
		assertNull(LocalDates.from(null, SEOUL));
		assertNull(LocalDates.from(" ", SEOUL));
		assertEquals(DATE, LocalDates.from(DATE, SEOUL));
		assertEquals(DATE, LocalDates.from(java.sql.Date.valueOf(DATE), SEOUL));
		assertEquals(DATE, LocalDates.from("2026-10-28", SEOUL));
		assertEquals(DATE, LocalDates.from(" 2026-10-28 ", SEOUL));
		assertEquals(DATE, LocalDates.from("2026-10-28T12:00:00+09:00", SEOUL));
		assertEquals(DATE, LocalDates.from(LocalDateTime.of(2026, 10, 28, 23, 59), SEOUL));
		assertEquals(DATE, LocalDates.from(Timestamp.from(NOON), SEOUL));
		assertEquals(DATE, LocalDates.from(NOON, SEOUL));
		assertEquals(DATE, LocalDates.from(Date.from(NOON), SEOUL));
		// 시각 값의 날짜는 zone에 따라 다르다(서울 00:30은 UTC로는 전날이다).
		Instant justAfterMidnight = Instant.parse("2026-10-27T15:30:00Z");
		assertEquals(DATE, LocalDates.from(justAfterMidnight, SEOUL));
		assertEquals(DATE.minusDays(1), LocalDates.from(justAfterMidnight, ZoneOffset.UTC));

		assertThrows(IllegalArgumentException.class, () -> LocalDates.from("2026-02-30", SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalDates.from(java.sql.Time.valueOf("09:00:00"), SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalDates.from(LocalTime.NOON, SEOUL));
	}

	@Test
	public void localTimeFrom() {
		assertNull(LocalTimes.from(null, SEOUL));
		assertNull(LocalTimes.from(" ", SEOUL));
		assertEquals(LocalTime.of(13, 5, 9), LocalTimes.from(LocalTime.of(13, 5, 9), SEOUL));
		assertEquals(LocalTime.of(13, 5, 9), LocalTimes.from(java.sql.Time.valueOf("13:05:09"), SEOUL));
		assertEquals(LocalTime.of(13, 5), LocalTimes.from("13:05", SEOUL));
		assertEquals(LocalTime.of(13, 5, 9, 250_000_000), LocalTimes.from("13:05:09.25", SEOUL));
		// 시각 부분이 있는 값은 그 시각 부분이다.
		assertEquals(LocalTime.NOON, LocalTimes.from(Timestamp.from(NOON), SEOUL));
		assertEquals(LocalTime.NOON, LocalTimes.from(LocalDateTime.of(2026, 10, 28, 12, 0), SEOUL));
		assertEquals(LocalTime.NOON, LocalTimes.from(NOON, SEOUL));
		assertEquals(LocalTime.of(3, 0), LocalTimes.from(NOON, ZoneOffset.UTC));
		assertEquals(LocalTime.NOON, LocalTimes.from(NOON.atOffset(ZoneOffset.ofHours(9)), SEOUL));

		assertThrows(IllegalArgumentException.class, () -> LocalTimes.from("13-05-09", SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.from(1305, SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.from(DATE, SEOUL));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.from(java.sql.Date.valueOf(DATE), SEOUL));
	}

	@Test
	public void zoneIsRequired() {
		assertThrows(IllegalArgumentException.class, () -> Instants.from(NOON, null));
		assertThrows(IllegalArgumentException.class, () -> LocalDateTimes.from(NOON, null));
		assertThrows(IllegalArgumentException.class, () -> LocalDates.from(DATE, null));
		assertThrows(IllegalArgumentException.class, () -> LocalTimes.from(LocalTime.NOON, null));
		// null 입력이어도 zone은 검사한다.
		assertThrows(IllegalArgumentException.class, () -> Instants.from(null, null));
	}
}
