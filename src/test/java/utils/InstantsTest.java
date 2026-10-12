package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * {@link Instants}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InstantsTest {
	// --- fromString: 타임존이 명시된 표기 ---

	@Test
	public void fromString_utcSuffix() {
		assertEquals(Instant.parse("2024-07-10T14:40:37Z"), Instants.fromString("2024-07-10T14:40:37Z"));
	}

	@Test
	public void fromString_utcSuffix_withFraction() {
		assertEquals(Instant.parse("2024-07-10T14:40:37.123Z"), Instants.fromString("2024-07-10T14:40:37.123Z"));
	}

	@Test
	public void fromString_utcSuffix_minutePrecision() {
		// 초를 생략한 표기도 허용한다.
		assertEquals(Instant.parse("2024-07-10T14:40:00Z"), Instants.fromString("2024-07-10T14:40Z"));
	}

	@Test
	public void fromString_numericOffset() {
		// '+09:00' 오프셋 표기: UTC로는 05:40:37이다.
		assertEquals(Instant.parse("2024-07-10T05:40:37Z"), Instants.fromString("2024-07-10T14:40:37+09:00"));
	}

	@Test
	public void fromString_numericOffset_minutePrecision() {
		assertEquals(Instant.parse("2024-07-10T05:40:00Z"), Instants.fromString("2024-07-10T14:40+09:00"));
	}

	@Test
	public void fromString_negativeOffset() {
		assertEquals(Instant.parse("2024-07-10T19:40:37Z"), Instants.fromString("2024-07-10T14:40:37-05:00"));
	}

	// --- fromString: 타임존이 없는 표기 (시스템 기본 시간대로 해석) ---

	@Test
	public void fromString_localDateTime() {
		LocalDateTime ldt = LocalDateTime.parse("2024-07-10T14:40:37");
		assertEquals(LocalDateTimes.toInstant(ldt), Instants.fromString("2024-07-10T14:40:37"));
	}

	@Test
	public void fromString_spaceSeparated() {
		LocalDateTime ldt = LocalDateTime.parse("2024-07-10T14:40:37");
		assertEquals(LocalDateTimes.toInstant(ldt), Instants.fromString("2024-07-10 14:40:37"));
	}

	// --- fromString: 오류 ---

	@Test
	public void fromString_invalid_throws() {
		assertThrows(DateTimeParseException.class, () -> Instants.fromString("not-a-datetime"));
	}

	// --- toUTCString / toZonedString ---

	@Test
	public void toUTCString_isUtcBased() {
		assertEquals("2024-07-10T05:40:37.000Z", Instants.toUTCString(Instant.parse("2024-07-10T05:40:37Z")));
	}

	@Test
	public void toStringMethods_nullSafe() {
		assertEquals(null, Instants.toUTCString(null));
		assertEquals(null, Instants.toZonedString(null));
		assertEquals(null, Instants.fromTimestamp(null));
	}

	// --- round-trip ---

	@Test
	public void utcString_roundTrips() {
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");
		assertEquals(instant, Instants.fromString(Instants.toUTCString(instant)));
	}

	@Test
	public void zonedString_roundTrips() {
		// toZonedString은 시스템 기본 시간대의 오프셋을 포함하므로 fromString이 그대로 복원할 수 있어야 한다.
		Instant instant = Instant.parse("2024-07-10T05:40:37Z");
		assertEquals(instant, Instants.fromString(Instants.toZonedString(instant)));
	}
}
