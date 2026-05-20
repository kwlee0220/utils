package utils;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.stream.FStream;

/**
 * {@link CSV}의 빌더 검증, parse/encode round-trip, edge case를 검증한다.
 * <p>
 * 기본 파싱 케이스는 {@link CSVTest}에서 다루며, 본 테스트는 RFC 4180 quote-doubling,
 * escape-self-escape, 충돌 검증, fail-fast, null 처리 등 추가 동작을 다룬다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CSVMoreTest {

	// ---- 팩토리 ----

	@Test
	public void get_default_delim_is_comma() {
		Assertions.assertEquals(',', CSV.get().delimiter());
	}

	@Test
	public void getTsv_delim_is_tab() {
		Assertions.assertEquals('\t', CSV.getTsv().delimiter());
	}

	@Test
	public void initial_escape_and_quote_are_null() {
		CSV csv = CSV.get();
		Assertions.assertNull(csv.escape());
		Assertions.assertNull(csv.quote());
	}

	// ---- 빌더 + 충돌 검증 ----

	@Test
	public void withDelimiter_changes_delim() {
		Assertions.assertEquals(';', CSV.get().withDelimiter(';').delimiter());
	}

	@Test
	public void withEscape_null_disables() {
		CSV csv = CSV.get().withEscape('\\').withEscape(null);
		Assertions.assertNull(csv.escape());
	}

	@Test
	public void withQuote_null_disables() {
		CSV csv = CSV.get().withQuote('"').withQuote(null);
		Assertions.assertNull(csv.quote());
	}

	@Test
	public void conflict_delim_eq_escape_via_withEscape() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withEscape(',');   // default delim is ','
		});
	}

	@Test
	public void conflict_delim_eq_escape_via_withDelimiter() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withEscape('\\').withDelimiter('\\');
		});
	}

	@Test
	public void conflict_delim_eq_quote_via_withQuote() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withQuote(',');   // default delim
		});
	}

	@Test
	public void conflict_delim_eq_quote_via_withDelimiter() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withQuote('"').withDelimiter('"');
		});
	}

	@Test
	public void conflict_escape_eq_quote_via_withEscape() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withQuote('"').withEscape('"');
		});
	}

	@Test
	public void conflict_escape_eq_quote_via_withQuote() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withEscape('\\').withQuote('\\');
		});
	}

	@Test
	public void all_distinct_OK() {
		CSV.get().withDelimiter(',').withEscape('\\').withQuote('"');   // 통과
	}

	// ---- parse: 기본 ----

	@Test
	public void parse_simple_three_tokens() {
		List<String> r = CSV.get().parse("a,b,c").toList();
		Assertions.assertEquals(List.of("a", "b", "c"), r);
	}

	@Test
	public void parse_single_token_no_delim() {
		Assertions.assertEquals(List.of("hello"), CSV.get().parse("hello").toList());
	}

	@Test
	public void parse_empty_input_returns_zero_tokens() {
		// 의도된 비대칭: parse("") → 0 tokens.
		Assertions.assertEquals(0, CSV.get().parse("").toList().size());
	}

	@Test
	public void parse_single_delim_returns_two_empty_tokens() {
		Assertions.assertEquals(List.of("", ""), CSV.get().parse(",").toList());
	}

	@Test
	public void parse_trailing_delim_appends_empty_token() {
		Assertions.assertEquals(List.of("a", ""), CSV.get().parse("a,").toList());
	}

	@Test
	public void parse_leading_delim_prepends_empty_token() {
		Assertions.assertEquals(List.of("", "a"), CSV.get().parse(",a").toList());
	}

	// ---- parse: null 안전 ----

	@Test
	public void parse_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().parse(null);
		});
	}

	// ---- parse: escape ----

	@Test
	public void parse_escape_protects_delim() {
		List<String> r = CSV.get().withEscape('\\').parse("a\\,b").toList();
		Assertions.assertEquals(List.of("a,b"), r);
	}

	@Test
	public void parse_escape_protects_escape() {
		List<String> r = CSV.get().withEscape('\\').parse("a\\\\b").toList();
		Assertions.assertEquals(List.of("a\\b"), r);
	}

	@Test
	public void parse_trailing_escape_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withEscape('\\').parse("a\\").toList();
		});
	}

	// ---- parse: quote ----

	@Test
	public void parse_quote_protects_delim() {
		List<String> r = CSV.get().withQuote('"').parse("\"a,b\",c").toList();
		Assertions.assertEquals(List.of("a,b", "c"), r);
	}

	@Test
	public void parse_quote_doubling_yields_literal_quote() {
		// "a""b" → a"b
		List<String> r = CSV.get().withQuote('"').parse("\"a\"\"b\"").toList();
		Assertions.assertEquals(List.of("a\"b"), r);
	}

	@Test
	public void parse_empty_quoted_field() {
		List<String> r = CSV.get().withQuote('"').parse("\"\",\"\"").toList();
		Assertions.assertEquals(List.of("", ""), r);
	}

	@Test
	public void parse_unmatched_quote_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().withQuote('"').parse("\"abc").toList();
		});
	}

	// ---- toString (encode) ----

	@Test
	public void toString_simple_values() {
		Assertions.assertEquals("a,b,c", CSV.get().toString("a", "b", "c"));
	}

	@Test
	public void toString_with_quote_wraps_all_values() {
		// quote가 설정되면 delim 포함 여부와 무관하게 모든 값이 wrap된다 (구현 단순성).
		Assertions.assertEquals("\"a,b\",\"c\"", CSV.get().withQuote('"').toString("a,b", "c"));
	}

	@Test
	public void toString_with_quote_doubles_quote_in_value() {
		Assertions.assertEquals("\"a\"\"b\"", CSV.get().withQuote('"').toString("a\"b"));
	}

	@Test
	public void toString_with_escape_doubles_escape_in_value() {
		Assertions.assertEquals("a\\\\b", CSV.get().withEscape('\\').toString("a\\b"));
	}

	@Test
	public void toString_with_escape_escapes_delim() {
		Assertions.assertEquals("a\\,b", CSV.get().withEscape('\\').toString("a,b"));
	}

	@Test
	public void toString_no_escape_no_quote_with_delim_in_value_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			CSV.get().toString("a,b");
		});
	}

	@Test
	public void toString_no_escape_no_quote_without_delim_in_value_OK() {
		Assertions.assertEquals("hello,world", CSV.get().toString("hello", "world"));
	}

	@Test
	public void toString_with_both_quote_and_escape_uses_quote_only_path() {
		// quote가 있으면 escape는 무시되어야 한다 (quote 영역 안에서 parser는 escape를 인식하지 않음).
		String enc = CSV.get().withEscape('\\').withQuote('"').toString("a,b");
		Assertions.assertEquals("\"a,b\"", enc);
	}

	@Test
	public void toString_iterable_overload() {
		Assertions.assertEquals("a,b,c", CSV.get().toString(List.of("a", "b", "c")));
	}

	@Test
	public void toString_fstream_overload() {
		Assertions.assertEquals("a,b,c", CSV.get().toString(FStream.of("a", "b", "c")));
	}

	// ---- round-trip ----

	@Test
	public void roundTrip_quote_with_delim_in_value() {
		CSV csv = CSV.get().withQuote('"');
		List<String> orig = List.of("he said \"hi\"", "world,with,commas", "plain");
		List<String> back = csv.parse(csv.toString(orig)).toList();
		Assertions.assertEquals(orig, back);
	}

	@Test
	public void roundTrip_escape_with_delim_in_value() {
		CSV csv = CSV.get().withEscape('\\');
		List<String> orig = List.of("a,b", "c\\d", "e\\,f");
		List<String> back = csv.parse(csv.toString(orig)).toList();
		Assertions.assertEquals(orig, back);
	}

	@Test
	public void roundTrip_both_escape_and_quote_via_quoteOnly() {
		CSV csv = CSV.get().withEscape('\\').withQuote('"');
		List<String> orig = List.of("a,b", "c\"d", "plain");
		List<String> back = csv.parse(csv.toString(orig)).toList();
		Assertions.assertEquals(orig, back);
	}

	// ---- 정적 헬퍼 ----

	@Test
	public void static_parseCsv_with_delim() {
		Assertions.assertEquals(List.of("a", "b"), CSV.parseCsv("a;b", ';').toList());
	}

	@Test
	public void static_parseCsv_with_delim_and_escape() {
		Assertions.assertEquals(List.of("a;b", "c"),
							CSV.parseCsv("a\\;b;c", ';', '\\').toList());
	}

	@Test
	public void static_parseCsvAsArray_default() {
		Assertions.assertArrayEquals(new String[] { "a", "b" }, CSV.parseCsvAsArray("a,b"));
	}

	@Test
	public void static_parseCsvAsArray_with_delim() {
		Assertions.assertArrayEquals(new String[] { "a", "b" }, CSV.parseCsvAsArray("a;b", ';'));
	}

	@Test
	public void static_parseCsvAsArray_with_delim_and_escape() {
		Assertions.assertArrayEquals(new String[] { "a;b", "c" },
								CSV.parseCsvAsArray("a\\;b;c", ';', '\\'));
	}

	// ---- TSV ----

	@Test
	public void tsv_parses_tab_separated() {
		Assertions.assertEquals(List.of("a", "b", "c"), CSV.getTsv().parse("a\tb\tc").toList());
	}

	@Test
	public void tsv_serializes_with_tab() {
		Assertions.assertEquals("a\tb\tc", CSV.getTsv().toString("a", "b", "c"));
	}
}
