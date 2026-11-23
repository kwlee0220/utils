package utils;

import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link Lengths}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LengthsTest {
	@Test
	public void parseMeters_handlesSuffixesAndCase() {
		Assertions.assertEquals(0.1, Lengths.parseMeters("100mm"), 1e-9);
		Assertions.assertEquals(0.5, Lengths.parseMeters("50cm"), 1e-9);
		Assertions.assertEquals(5.0, Lengths.parseMeters("5m"), 1e-9);
		Assertions.assertEquals(1500.0, Lengths.parseMeters("1.5km"), 1e-9);
		Assertions.assertEquals(1500.0, Lengths.parseMeters("1.5KM"), 1e-9);	// 대소문자 무시
		Assertions.assertEquals(5.0, Lengths.parseMeters("5"), 1e-9);			// 접미사 없음 = 미터
	}

	@Test
	public void formatMeters_picksUnitByMagnitude() {
		Assertions.assertEquals("4mm", Lengths.formatMeters(0.004));
		Assertions.assertEquals("4.5mm", Lengths.formatMeters(0.0045));
		Assertions.assertEquals("50cm", Lengths.formatMeters(0.5));
		Assertions.assertEquals("1.5m", Lengths.formatMeters(1.5));
		Assertions.assertEquals("100m", Lengths.formatMeters(100));
		Assertions.assertEquals("100.4m", Lengths.formatMeters(100.44));
		Assertions.assertEquals("1.5km", Lengths.formatMeters(1500));		// 2km로 반올림하지 않는다
		Assertions.assertEquals("5km", Lengths.formatMeters(5000));
	}

	@Test
	public void formatMeters_carriesToNextUnitAfterRounding() {
		Assertions.assertEquals("1km", Lengths.formatMeters(999.96));		// 1000m가 아니다
		Assertions.assertEquals("1m", Lengths.formatMeters(0.9996));		// 100cm가 아니다
		Assertions.assertEquals("1cm", Lengths.formatMeters(0.00996));	// 10mm가 아니다
		Assertions.assertEquals("999.9m", Lengths.formatMeters(999.94));
	}

	@Test
	public void formatMeters_signZeroAndNonFinite() {
		Assertions.assertEquals("0m", Lengths.formatMeters(0));
		Assertions.assertEquals("0m", Lengths.formatMeters(-0.0));
		Assertions.assertEquals("-1.5km", Lengths.formatMeters(-1500));
		Assertions.assertEquals("-50cm", Lengths.formatMeters(-0.5));
		Assertions.assertEquals("0mm", Lengths.formatMeters(0.00001));		// 0.1mm 미만은 0으로 반올림
		Assertions.assertThrows(IllegalArgumentException.class, () -> Lengths.formatMeters(Double.NaN));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Lengths.formatMeters(Double.POSITIVE_INFINITY));
	}

	@Test
	public void formatMeters_roundTripsThroughParse() {
		// 각 단위의 0.1까지만 유지하므로(예: 12345.6m → 12.3km) 그 정밀도 안의 값으로 검사한다.
		for ( double len: new double[]{0.004, 0.0045, 0.5, 1.5, 100, 1500, 12300} ) {
			Assertions.assertEquals(len, Lengths.parseMeters(Lengths.formatMeters(len)), 1e-9, "" + len);
		}
		Assertions.assertEquals("12.3km", Lengths.formatMeters(12345.6));
	}

	@Test
	public void parseMeters_rejectsNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Lengths.parseMeters(null));
	}

	@Test
	public void formatting_ignoresDefaultLocale() {
		forEachUnusualLocale(tag -> {
			Assertions.assertEquals("1.5km", Lengths.formatMeters(1500), tag);
			Assertions.assertEquals(1500.0, Lengths.parseMeters(Lengths.formatMeters(1500)), tag);
			Assertions.assertEquals(0.1, Lengths.parseMeters("100MM"), 1e-9, tag);
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
