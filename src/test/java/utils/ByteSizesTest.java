package utils;

import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link ByteSizes}의 단위 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ByteSizesTest {
	@Test
	public void parse_handlesSuffixesAndCase() {
		Assertions.assertEquals(1024L, ByteSizes.parse("1kb"));
		Assertions.assertEquals(1024L, ByteSizes.parse("1k"));
		Assertions.assertEquals(1024L * 1024, ByteSizes.parse("1mb"));
		Assertions.assertEquals(1024L * 1024, ByteSizes.parse("1m"));
		Assertions.assertEquals(1024L * 1024 * 1024, ByteSizes.parse("1gb"));
		Assertions.assertEquals(5L * 1024 * 1024 * 1024, ByteSizes.parse("5GB"));
		Assertions.assertEquals(1024L, ByteSizes.parse("1024"));				// 접미사 없음 = byte
		Assertions.assertEquals(1L << 40, ByteSizes.parse("1tb"));
		Assertions.assertEquals(1L << 40, ByteSizes.parse("1T"));
		Assertions.assertEquals(3L << 39, ByteSizes.parse("1.5TB"));
	}

	@Test
	public void format_withExplicitUnit() {
		Assertions.assertEquals("1.5MB", ByteSizes.format(1024L * 1024 * 3 / 2, "mb", "%.1f"));
		Assertions.assertEquals("2KB", ByteSizes.format(2048L, "kb", "%.0f"));
		// 'b' 단위도 다른 단위와 같은 실수 형식을 쓴다.
		Assertions.assertEquals("100B", ByteSizes.format(100, "b", "%.0f"));
		Assertions.assertEquals("100.0B", ByteSizes.format(100, "B", "%.1f"));
		Assertions.assertEquals("1.5TB", ByteSizes.format(3L << 39, "tb", "%.1f"));
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> ByteSizes.format(100, "pb", "%.0f"));
	}

	@Test
	public void format_autoUnit() {
		Assertions.assertEquals("512B", ByteSizes.format(512));
		Assertions.assertEquals("1KB", ByteSizes.format(1024));
		Assertions.assertEquals("1.5KB", ByteSizes.format(1536));
		Assertions.assertEquals("1MB", ByteSizes.format(1024L * 1024));
		Assertions.assertEquals("1GB", ByteSizes.format(1024L * 1024 * 1024));
		Assertions.assertEquals("1TB", ByteSizes.format(1L << 40));
		Assertions.assertEquals("5TB", ByteSizes.format(5L << 40));			// 5120gb가 아니다
		Assertions.assertEquals("8388608TB", ByteSizes.format(Long.MAX_VALUE));	// tb보다 큰 단위는 없다
	}

	@Test
	public void format_roundsBeforeChoosingFormat() {
		// 1.04kb는 반올림하면 1.0이므로 "1.0kb"가 아니라 "1kb"이다.
		Assertions.assertEquals("1KB", ByteSizes.format(1065));
		Assertions.assertEquals("1KB", ByteSizes.format(1075));		// 1.0498kb
		Assertions.assertEquals("1.1KB", ByteSizes.format(1076));		// 1.0508kb
		Assertions.assertEquals("1023.9KB", ByteSizes.format(1023L * 1024 + 900));
	}

	@Test
	public void format_carriesToNextUnitAfterRounding() {
		// 1023.98kb를 반올림하면 1024.0kb가 되므로 다음 단위로 쓴다.
		Assertions.assertEquals("1MB", ByteSizes.format(1023L * 1024 + 1000));
		Assertions.assertEquals("1GB", ByteSizes.format((1L << 30) - 1));
		Assertions.assertEquals("1TB", ByteSizes.format((1L << 40) - 1));
	}

	@Test
	public void format_negative() {
		Assertions.assertEquals("-512B", ByteSizes.format(-512));
		Assertions.assertEquals("-1.5KB", ByteSizes.format(-1536));
		Assertions.assertEquals("-8388608TB", ByteSizes.format(Long.MIN_VALUE));
	}

	@Test
	public void format_roundTripsThroughParse() {
		for ( long sz: new long[]{512, 1024, 1536, 10L << 20, 3L << 39} ) {
			Assertions.assertEquals(sz, ByteSizes.parse(ByteSizes.format(sz)), "" + sz);
		}
	}

	@Test
	public void parse_acceptsCanonicalOutputAndSpaces() {
		Assertions.assertEquals(512L, ByteSizes.parse("512B"));
		Assertions.assertEquals(512L, ByteSizes.parse("512b"));
		Assertions.assertEquals(1536L, ByteSizes.parse(" 1.5 KB "));
		Assertions.assertEquals(3L << 39, ByteSizes.parse("1.5 TB"));
		Assertions.assertEquals(-1536L, ByteSizes.parse("-1.5kb"));
		Assertions.assertEquals(102L, ByteSizes.parse("0.1kb"));		// 102.4 → 반올림
		Assertions.assertEquals(12L, ByteSizes.parse("12.0"));		// 소수 부분이 0이면 받는다
	}

	@Test
	public void parse_rejectsInvalidInput() {
		for ( String str: new String[]{"1.5", "1.5b", "abc", "", "10x", "1e3", "5 k b", "kb"} ) {
			Assertions.assertThrows(IllegalArgumentException.class, () -> ByteSizes.parse(str), str);
		}
		Assertions.assertThrows(IllegalArgumentException.class, () -> ByteSizes.parse(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> ByteSizes.parse("8388608tb"));	// 2^63
	}

	@Test
	public void format_explicitUnitIsCanonical() {
		// 단위는 입력의 대소문자와 상관없이 표준 표기로 쓰고, 형식 문자열에 이어 붙이지 않는다.
		Assertions.assertEquals("1.5KB", ByteSizes.format(1536, "k", "%.1f"));
		Assertions.assertEquals("1.5KB", ByteSizes.format(1536, "Kb", "%.1f"));
		Assertions.assertEquals("[1.5]KB", ByteSizes.format(1536, "kb", "[%.1f]"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> ByteSizes.format(1536, "kbb", "%.1f"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> ByteSizes.format(1536, null, "%.1f"));
	}

	@Test
	public void formatting_ignoresDefaultLocale() {
		forEachUnusualLocale(tag -> {
			Assertions.assertEquals("1.5KB", ByteSizes.format(1536), tag);
			Assertions.assertEquals("1.5MB", ByteSizes.format(1024L * 1024 * 3 / 2, "mb", "%.1f"), tag);
			Assertions.assertEquals(1536L, ByteSizes.parse(ByteSizes.format(1536)), tag);
			Assertions.assertEquals(1024L * 1024, ByteSizes.parse("1MB"), tag);
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
