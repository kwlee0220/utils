package utils;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link LocalTimes}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalTimesTest {
	@Test
	public void parse_isoLocalTime() {
		Assertions.assertEquals(LocalTime.of(9, 0), LocalTimes.parse("09:00"));
		Assertions.assertEquals(LocalTime.of(9, 0, 30, 500_000_000), LocalTimes.parse("09:00:30.5"));
		Assertions.assertThrows(DateTimeParseException.class, () -> LocalTimes.parse("9:00"));
		Assertions.assertThrows(DateTimeParseException.class, () -> LocalTimes.parse("24:00"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalTimes.parse(null));
	}

	@Test
	public void toString_dependsOnPrecision() {
		Assertions.assertEquals("09:00", LocalTimes.format(LocalTime.of(9, 0)));
		Assertions.assertEquals("09:00:30", LocalTimes.format(LocalTime.of(9, 0, 30)));
		Assertions.assertEquals("09:00:30.500", LocalTimes.format(LocalTime.of(9, 0, 30, 500_000_000)));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalTimes.format(null));
	}

	@Test
	public void toMillisOfDay_truncatesSubMillis() {
		Assertions.assertEquals(0, LocalTimes.toMillisOfDay(LocalTime.MIDNIGHT));
		Assertions.assertEquals(1, LocalTimes.toMillisOfDay(LocalTime.ofNanoOfDay(1_999_999)));
		Assertions.assertEquals(86_399_999, LocalTimes.toMillisOfDay(LocalTime.MAX));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalTimes.toMillisOfDay(null));
	}

	@Test
	public void fromMillisOfDay_rangeBoundaries() {
		Assertions.assertEquals(LocalTime.MIDNIGHT, LocalTimes.fromMillisOfDay(0));
		Assertions.assertEquals(LocalTime.of(23, 59, 59, 999_000_000), LocalTimes.fromMillisOfDay(86_399_999));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalTimes.fromMillisOfDay(86_400_000));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LocalTimes.fromMillisOfDay(-1));
	}

	@Test
	public void millisOfDay_roundTrip() {
		for ( long millis: new long[]{0, 1, 32_400_000, 86_399_999} ) {
			Assertions.assertEquals(millis, LocalTimes.toMillisOfDay(LocalTimes.fromMillisOfDay(millis)));
		}
	}
}
