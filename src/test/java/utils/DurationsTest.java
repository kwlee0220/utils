package utils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link Durations}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DurationsTest {
	@Test
	public void parse_iso8601() {
		Assertions.assertEquals(Duration.ofHours(2).plusMinutes(30), Durations.parse("PT2H30M"));
		Assertions.assertEquals(Duration.ofDays(1), Durations.parse("P1D"));			// 'PT'로 시작하지 않는 표기
		Assertions.assertEquals(Duration.ofMillis(1500), Durations.parse("pt1.5s"));	// 소문자
		Assertions.assertEquals(Duration.ofSeconds(-5), Durations.parse("-PT5S"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse("PTX"));
	}

	@Test
	public void parse_suffixes() {
		Assertions.assertEquals(Duration.ofMillis(100), Durations.parse("100ms"));
		Assertions.assertEquals(Duration.ofSeconds(30), Durations.parse("30s"));
		Assertions.assertEquals(Duration.ofMinutes(5), Durations.parse("5m"));
		Assertions.assertEquals(Duration.ofHours(2), Durations.parse("2h"));
		Assertions.assertEquals(Duration.ofDays(1), Durations.parse("1d"));
	}

	@Test
	public void parse_normalizesInput() {
		Assertions.assertEquals(Duration.ofSeconds(5), Durations.parse(" 5s "));		// 앞뒤 공백
		Assertions.assertEquals(Duration.ofMinutes(5), Durations.parse("5 m"));		// 숫자와 접미사 사이 공백
		Assertions.assertEquals(Duration.ofSeconds(5), Durations.parse("5S"));		// 대문자 접미사
		Assertions.assertEquals(Duration.ofMillis(5), Durations.parse("5MS"));
		Assertions.assertEquals(Duration.ofSeconds(-5), Durations.parse("-5s"));
		Assertions.assertEquals(Duration.ofSeconds(5), Durations.parse("+5s"));
	}

	@Test
	public void parse_fractions() {
		Assertions.assertEquals(Duration.ofMillis(1500), Durations.parse("1.5s"));
		Assertions.assertEquals(Duration.ofHours(36), Durations.parse("1.5d"));		// 1.5일, 1.5초가 아니다
		Assertions.assertEquals(Duration.ofSeconds(90), Durations.parse("1.5m"));
		Assertions.assertEquals(Duration.ofNanos(1_500_000), Durations.parse("1.5ms"));
		// 나노초보다 작은 부분은 반올림한다.
		Assertions.assertEquals(Duration.ofNanos(2), Durations.parse("0.0000000015s"));
	}

	@Test
	public void parse_rejectsNonDecimalNumbers() {
		// Double.parseDouble이 받는 표기(타입 접미사, NaN, 무한대, 지수, 16진)는 받지 않는다.
		for ( String str: new String[]{"5f", "2F", "5D1", "NaN", "Infinity", "-Infinity", "1e3", "0x1p3", "abc", "",
										"5x", "1.", ".5s", "5 s s"} ) {
			Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse(str, ChronoUnit.SECONDS),
									str);
		}
	}

	@Test
	public void parse_rejectsNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse(null, ChronoUnit.SECONDS));
	}

	@Test
	public void parse_suffixless_requiresDefaultUnit() {
		// 단일 인자(=defaultUnit null): 접미사 없으면 거부
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
															() -> Durations.parse("1500"));
		Assertions.assertTrue(e.getMessage().contains("unit is missing"), e.getMessage());
		// defaultUnit 지정 시 그 단위로 해석
		Assertions.assertEquals(Duration.ofMillis(1500), Durations.parse("1500", ChronoUnit.MILLIS));
		Assertions.assertEquals(Duration.ofSeconds(5), Durations.parse("5", ChronoUnit.SECONDS));
		Assertions.assertEquals(Duration.ofHours(3), Durations.parse("3", ChronoUnit.HOURS));
		// 접미사 없는 소수도 defaultUnit 단위로 해석한다.
		Assertions.assertEquals(Duration.ofMillis(1500), Durations.parse("1.5", ChronoUnit.SECONDS));
		Assertions.assertEquals(Duration.ofNanos(1500), Durations.parse("1.5", ChronoUnit.MICROS));
	}

	@Test
	public void parse_suffixTakesPrecedenceOverDefaultUnit() {
		// 접미사가 있으면 defaultUnit은 무시된다.
		Assertions.assertEquals(Duration.ofSeconds(30), Durations.parse("30s", ChronoUnit.HOURS));
		Assertions.assertEquals(Duration.ofDays(5), Durations.parse("5d", ChronoUnit.SECONDS));
		Assertions.assertEquals(Duration.ofDays(5), Durations.parse("5D", ChronoUnit.SECONDS));
	}

	@Test
	public void parse_defaultUnitIsAlwaysValidated() {
		// 지원하지 않는 defaultUnit은 입력과 상관없이 거부한다(접미사가 있어도, null 입력이어도).
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse("5s", ChronoUnit.WEEKS));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse("5", ChronoUnit.MONTHS));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.parse(null, ChronoUnit.FOREVER));
	}

	@Test
	public void parse_outOfRange() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> Durations.parse("999999999999999999999999d"));
	}

	@Test
	public void parse_errorMessageShowsOriginalInput() {
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
															() -> Durations.parse(" 5x "));
		Assertions.assertTrue(e.getMessage().contains("' 5x '"), e.getMessage());
	}

	@Test
	public void formatClock_format() {
		Assertions.assertEquals("0:00.000", Durations.formatClock(0));
		Assertions.assertEquals("0:00.500", Durations.formatClock(500));
		Assertions.assertEquals("0:30.000", Durations.formatClock(30_000));
		Assertions.assertEquals("0:05.250", Durations.formatClock(5_250));
		Assertions.assertEquals("1:30.000", Durations.formatClock(90_000));
		Assertions.assertEquals("59:59.999", Durations.formatClock(3_599_999));
		Assertions.assertEquals("1:00:00.250", Durations.formatClock(3_600_250));
		Assertions.assertEquals("25:00:00.000", Durations.formatClock(Duration.ofHours(25)));
		// 밀리초보다 작은 부분은 버린다.
		Assertions.assertEquals("0:01.999", Durations.formatClock(Duration.ofNanos(1_999_999_999)));
	}

	@Test
	public void formatClock_negative() {
		// 부호는 한 번만 붙인다.
		Assertions.assertEquals("-0:30.000", Durations.formatClock(-30_000));
		Assertions.assertEquals("-0:00.500", Durations.formatClock(-500));
		Assertions.assertEquals("-1:00:00.250", Durations.formatClock(-3_600_250));
		// 버려서 0이 되면 부호를 붙이지 않는다.
		Assertions.assertEquals("0:00.000", Durations.formatClock(Duration.ofNanos(-1)));
	}

	@Test
	public void formatClock_extremes() {
		Assertions.assertEquals("-2562047788015:12:55.808", Durations.formatClock(Long.MIN_VALUE));
		Assertions.assertEquals("2562047788015:12:55.807", Durations.formatClock(Long.MAX_VALUE));
		// Duration은 long 밀리초로 나타낼 수 없을 만큼 커도 된다.
		Assertions.assertEquals("2562047788015215:30:07.999", Durations.formatClock(Duration.ofSeconds(Long.MAX_VALUE, 999_999_999)));
		Assertions.assertEquals("-2562047788015215:30:08.000", Durations.formatClock(Duration.ofSeconds(Long.MIN_VALUE)));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.formatClock(null));
	}

	@Test
	public void formatShort_format() {
		Assertions.assertEquals("0s", Durations.formatShort(0));
		Assertions.assertEquals("0s", Durations.formatShort(499));			// 반올림
		Assertions.assertEquals("1s", Durations.formatShort(500));
		Assertions.assertEquals("30s", Durations.formatShort(30_000));
		Assertions.assertEquals("1m 30s", Durations.formatShort(90_000));
		Assertions.assertEquals("1m 0s", Durations.formatShort(59_500));		// 반올림해서 1분
		Assertions.assertEquals("1h 0m 0s", Durations.formatShort(3_600_000));
		Assertions.assertEquals("25h 0m 5s", Durations.formatShort(Duration.ofHours(25).plusSeconds(5)));
		Assertions.assertEquals("-1m 30s", Durations.formatShort(-90_000));
		Assertions.assertEquals("0s", Durations.formatShort(-499));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Durations.formatShort(null));
	}

	@Test
	public void formatting_ignoresDefaultLocale() {
		forEachUnusualLocale(tag -> {
			Assertions.assertEquals("1:00:00.250", Durations.formatClock(3_600_250), tag);
			Assertions.assertEquals("1m 30s", Durations.formatShort(90_000), tag);
			// 터키어 로캘에서도 대문자 접미사를 읽는다.
			Assertions.assertEquals(Duration.ofMillis(5), Durations.parse("5MS"), tag);
		});
	}

	/** 기본 로캘을 잠시 바꿔 실행한다. 독일어는 소수점이 ',', 아랍어(이집트)는 숫자가 아랍-인도 숫자인 로캘이다. */
	private static void forEachUnusualLocale(java.util.function.Consumer<String> test) {
		Locale saved = Locale.getDefault();
		try {
			for ( Locale locale: new Locale[]{Locale.GERMANY, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("tr-TR")} ) {
				Locale.setDefault(locale);
				test.accept(locale.toLanguageTag());
			}
		}
		finally {
			Locale.setDefault(saved);
		}
	}
}
