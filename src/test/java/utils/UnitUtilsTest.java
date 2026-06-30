package utils;

import java.awt.Dimension;
import java.time.Duration;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link UnitUtils}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UnitUtilsTest {

	// --- parseDimension ---

	@Test
	public void parseDimension_acceptsLowerAndUpperX() {
		Assertions.assertEquals(new Dimension(1024, 768), UnitUtils.parseDimension("1024x768"));
		Assertions.assertEquals(new Dimension(640, 480), UnitUtils.parseDimension("640X480"));
		Assertions.assertEquals(new Dimension(10, 20), UnitUtils.parseDimension(" 10 x 20 "));
	}

	@Test
	public void parseDimension_invalid_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseDimension(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseDimension("1024"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseDimension("axb"));
	}

	// --- parseLengthInMeter / toMeterString ---

	@Test
	public void parseLengthInMeter_handlesSuffixesAndCase() {
		Assertions.assertEquals(0.1, UnitUtils.parseLengthInMeter("100mm"), 1e-9);
		Assertions.assertEquals(0.5, UnitUtils.parseLengthInMeter("50cm"), 1e-9);
		Assertions.assertEquals(5.0, UnitUtils.parseLengthInMeter("5m"), 1e-9);
		Assertions.assertEquals(1500.0, UnitUtils.parseLengthInMeter("1.5km"), 1e-9);
		Assertions.assertEquals(1500.0, UnitUtils.parseLengthInMeter("1.5KM"), 1e-9);	// 대소문자 무시
		Assertions.assertEquals(5.0, UnitUtils.parseLengthInMeter("5"), 1e-9);			// 접미사 없음 = 미터
	}

	@Test
	public void toMeterString_picksUnitByMagnitude() {
		Assertions.assertEquals("0.5m", UnitUtils.toMeterString(0.5));
		Assertions.assertEquals("100m", UnitUtils.toMeterString(100));
		Assertions.assertEquals("5km", UnitUtils.toMeterString(5000));
	}

	// --- parseByteSize / toByteSizeString ---

	@Test
	public void parseByteSize_handlesSuffixesAndCase() {
		Assertions.assertEquals(1024L, UnitUtils.parseByteSize("1kb"));
		Assertions.assertEquals(1024L, UnitUtils.parseByteSize("1k"));
		Assertions.assertEquals(1024L * 1024, UnitUtils.parseByteSize("1mb"));
		Assertions.assertEquals(1024L * 1024, UnitUtils.parseByteSize("1m"));
		Assertions.assertEquals(1024L * 1024 * 1024, UnitUtils.parseByteSize("1gb"));
		Assertions.assertEquals(5L * 1024 * 1024 * 1024, UnitUtils.parseByteSize("5GB"));
		Assertions.assertEquals(1024L, UnitUtils.parseByteSize("1024"));				// 접미사 없음 = byte
	}

	@Test
	public void toByteSizeString_withExplicitUnit() {
		Assertions.assertEquals("1.5mb", UnitUtils.toByteSizeString(1024L * 1024 * 3 / 2, "mb", "%.1f"));
		Assertions.assertEquals("2kb", UnitUtils.toByteSizeString(2048L, "kb", "%.0f"));
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> UnitUtils.toByteSizeString(100, "tb", "%.0f"));
	}

	@Test
	public void toByteSizeString_autoUnit() {
		Assertions.assertEquals("512", UnitUtils.toByteSizeString(512));
		Assertions.assertEquals("1kb", UnitUtils.toByteSizeString(1024));
		Assertions.assertEquals("1.5kb", UnitUtils.toByteSizeString(1536));
		Assertions.assertEquals("1mb", UnitUtils.toByteSizeString(1024L * 1024));
		Assertions.assertEquals("1gb", UnitUtils.toByteSizeString(1024L * 1024 * 1024));
	}

	// --- parseDuration(String) / parseDuration(String, String) ---

	@Test
	public void parseDuration_iso8601() {
		Assertions.assertEquals(Duration.ofHours(2).plusMinutes(30), UnitUtils.parseDuration("PT2H30M"));
	}

	@Test
	public void parseDuration_suffixes() {
		Assertions.assertEquals(Duration.ofMillis(100), UnitUtils.parseDuration("100ms"));
		Assertions.assertEquals(Duration.ofSeconds(30), UnitUtils.parseDuration("30s"));
		Assertions.assertEquals(Duration.ofMinutes(5), UnitUtils.parseDuration("5m"));
		Assertions.assertEquals(Duration.ofHours(2), UnitUtils.parseDuration("2h"));
		Assertions.assertEquals(Duration.ofDays(1), UnitUtils.parseDuration("1d"));
	}

	@Test
	public void parseDuration_nullReturnsNull() {
		Assertions.assertNull(UnitUtils.parseDuration(null));
		Assertions.assertNull(UnitUtils.parseDuration(null, "s"));
	}

	@Test
	public void parseDuration_suffixless_requiresDefaultUnit() {
		// 단일 인자(=defaultUnit null): 접미사 없으면 거부
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseDuration("1500"));
		// defaultUnit 지정 시 그 단위로 해석
		Assertions.assertEquals(Duration.ofMillis(1500), UnitUtils.parseDuration("1500", "ms"));
		Assertions.assertEquals(Duration.ofSeconds(5), UnitUtils.parseDuration("5", "s"));
		Assertions.assertEquals(Duration.ofHours(3), UnitUtils.parseDuration("3", "h"));
	}

	@Test
	public void parseDuration_suffixTakesPrecedenceOverDefaultUnit() {
		// 접미사가 있으면 defaultUnit은 무시된다.
		Assertions.assertEquals(Duration.ofSeconds(30), UnitUtils.parseDuration("30s", "h"));
	}

	// --- parseSecondDuration (접미사 없으면 초, 소수 허용; 'd'=일 보존) ---

	@Test
	public void parseSecondDuration_bareNumberIsSeconds() {
		Assertions.assertEquals(Duration.ofSeconds(5), UnitUtils.parseSecondDuration("5"));
		Assertions.assertEquals(Duration.ofMillis(1500), UnitUtils.parseSecondDuration("1.5"));	// 소수 초
	}

	@Test
	public void parseSecondDuration_daySuffixIsDaysNotSeconds() {
		// 회귀 방지: Double.parseDouble이 'd'를 타입 접미사로 오인해 5초가 되던 버그.
		Assertions.assertEquals(Duration.ofDays(5), UnitUtils.parseSecondDuration("5d"));
		Assertions.assertEquals(Duration.ofDays(1), UnitUtils.parseSecondDuration("1d"));
	}

	@Test
	public void parseSecondDuration_otherSuffixesAndIso() {
		Assertions.assertEquals(Duration.ofSeconds(30), UnitUtils.parseSecondDuration("30s"));
		Assertions.assertEquals(Duration.ofHours(2), UnitUtils.parseSecondDuration("2h"));
		Assertions.assertEquals(Duration.ofMinutes(5), UnitUtils.parseSecondDuration("5m"));
		Assertions.assertEquals(Duration.ofMillis(500), UnitUtils.parseSecondDuration("500ms"));
		Assertions.assertEquals(Duration.ofHours(2).plusMinutes(30), UnitUtils.parseSecondDuration("PT2H30M"));
	}

	@Test
	public void parseSecondDuration_nullAndInvalid() {
		Assertions.assertNull(UnitUtils.parseSecondDuration(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseSecondDuration("abc"));
	}

	// --- toMillisString / toSecondString ---

	@Test
	public void toMillisString_format() {
		Assertions.assertEquals("0.500", UnitUtils.toMillisString(500));
		Assertions.assertEquals("30", UnitUtils.toMillisString(30_000));
		Assertions.assertEquals("01:30", UnitUtils.toMillisString(90_000));
		Assertions.assertEquals("1:00:00.250", UnitUtils.toMillisString(3_600_250));
		Assertions.assertEquals("-30", UnitUtils.toMillisString(-30_000));	// 음수
	}

	@Test
	public void toSecondString_format() {
		Assertions.assertEquals("0s", UnitUtils.toSecondString(0));
		Assertions.assertEquals("30s", UnitUtils.toSecondString(30_000));
		Assertions.assertEquals("01m:30s", UnitUtils.toSecondString(90_000));
		Assertions.assertEquals("1h:00m:00s", UnitUtils.toSecondString(3_600_000));
	}

	// --- toDateString / parseDateString ---

	@Test
	public void dateString_roundTrip_withoutMillis() {
		Calendar c = new GregorianCalendar(2024, Calendar.JANUARY, 31, 12, 30, 45);

		String s = UnitUtils.toDateString(c, false);
		Assertions.assertEquals("20240131T123045", s);

		Calendar parsed = UnitUtils.parseDateString(s);
		Assertions.assertEquals(2024, parsed.get(Calendar.YEAR));
		Assertions.assertEquals(Calendar.JANUARY, parsed.get(Calendar.MONTH));
		Assertions.assertEquals(31, parsed.get(Calendar.DAY_OF_MONTH));
		Assertions.assertEquals(12, parsed.get(Calendar.HOUR_OF_DAY));
		Assertions.assertEquals(30, parsed.get(Calendar.MINUTE));
		Assertions.assertEquals(45, parsed.get(Calendar.SECOND));
	}

	@Test
	public void dateString_roundTrip_withMillis() {
		Calendar c = new GregorianCalendar(2024, Calendar.JANUARY, 31, 12, 30, 45);
		c.set(Calendar.MILLISECOND, 250);

		String s = UnitUtils.toDateString(c, true);
		Assertions.assertEquals("20240131T123045:250", s);

		Calendar parsed = UnitUtils.parseDateString(s);
		Assertions.assertEquals(45, parsed.get(Calendar.SECOND));
		Assertions.assertEquals(250, parsed.get(Calendar.MILLISECOND));	// 밀리초 보존(round-trip)
	}

	@Test
	public void parseDateString_dateOnly_setsMidnight() {
		Calendar c = UnitUtils.parseDateString("20240131");
		Assertions.assertEquals(31, c.get(Calendar.DAY_OF_MONTH));
		Assertions.assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
		Assertions.assertEquals(0, c.get(Calendar.MINUTE));
		Assertions.assertEquals(0, c.get(Calendar.SECOND));
	}

	@Test
	public void parseDateString_nullAndInvalidLength() {
		Assertions.assertNull(UnitUtils.parseDateString(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> UnitUtils.parseDateString("2024"));
	}

	@Test
	public void toDateString_nullCalendarReturnsNull() {
		Assertions.assertNull(UnitUtils.toDateString((Calendar)null, false));
	}
}
