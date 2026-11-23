package utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link LocalDates}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDatesTest {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	public void toInstant_isStartOfDayInZone() {
		Assertions.assertEquals(Instant.parse("2024-07-09T15:00:00Z"), LocalDates.startOfDay(LocalDate.of(2024, 7, 10), SEOUL));
		Assertions.assertEquals(Instant.parse("2024-07-10T00:00:00Z"),
								LocalDates.startOfDay(LocalDate.of(2024, 7, 10), ZoneOffset.UTC));
	}

	@Test
	public void toInstant_dstGapAtMidnight() {
		// America/Sao_Paulo는 2018-11-04 00:00에 서머타임이 시작되어 자정이 없다. 그날은 01:00(-02:00)에 시작한다.
		Assertions.assertEquals(Instant.parse("2018-11-04T03:00:00Z"),
								LocalDates.startOfDay(LocalDate.of(2018, 11, 4), ZoneId.of("America/Sao_Paulo")));
	}

	@Test
	public void toInstant_missingDateBelongsToNextDate() {
		// Pacific/Apia는 2011-12-30이 통째로 없다(날짜변경선 이동). 그 시각을 날짜로 되돌리면 다음 날이 된다.
		ZoneId apia = ZoneId.of("Pacific/Apia");
		Instant start = LocalDates.startOfDay(LocalDate.of(2011, 12, 30), apia);
		Assertions.assertEquals(LocalDate.of(2011, 12, 31), LocalDates.ofInstant(start, apia));
	}

	@Test
	public void fromInstant_dependsOnZone() {
		Instant instant = Instant.parse("2024-07-09T16:00:00Z");
		Assertions.assertEquals(LocalDate.of(2024, 7, 10), LocalDates.ofInstant(instant, SEOUL));
		Assertions.assertEquals(LocalDate.of(2024, 7, 9), LocalDates.ofInstant(instant, ZoneOffset.UTC));
	}

	@Test
	public void epochMilli_roundTrip() {
		LocalDate date = LocalDate.of(2024, 7, 10);
		long millis = LocalDates.startOfDayEpochMilli(date, SEOUL);
		Assertions.assertEquals(Instant.parse("2024-07-09T15:00:00Z").toEpochMilli(), millis);
		Assertions.assertEquals(date, LocalDates.fromEpochMilli(millis, SEOUL));
		// 그날의 마지막 밀리초도 같은 날짜다.
		Assertions.assertEquals(date, LocalDates.fromEpochMilli(millis + 86_400_000L - 1, SEOUL));
	}

	@Test
	public void toEpochMilli_overflow() {
		Assertions.assertThrows(ArithmeticException.class, () -> LocalDates.startOfDayEpochMilli(LocalDate.MAX, ZoneOffset.UTC));
	}

	@Test
	public void nullArguments() {
		LocalDate date = LocalDate.of(2024, 7, 10);
		Instant instant = Instant.EPOCH;
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.startOfDay(null, SEOUL));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.startOfDay(date, null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.ofInstant(null, SEOUL));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.ofInstant(instant, null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.startOfDayEpochMilli(null, ZoneId.systemDefault()));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalDates.fromEpochMilli(0, null));
	}
}
