package utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.func.FOption;
import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CSVTest {
	private CSV m_csv;
	private CSV m_csv2;

	@BeforeEach
	public void setup() {
		m_csv = CSV.get().withDelimiter(',').withEscape('\\');
		m_csv2 = CSV.get().withDelimiter(',').withEscape('\\').withQuote('\'');
	}

	@Test
	public void test1() throws Exception {
		FStream<String> strm = m_csv.parse("aa,bbb , c");

		Assertions.assertEquals(FOption.of("aa"), strm.next());
		Assertions.assertEquals(FOption.of("bbb "), strm.next());
		Assertions.assertEquals(FOption.of(" c"), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test2() throws Exception {
		FStream<String> strm = m_csv.parse("");

		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test3() throws Exception {
		FStream<String> strm = m_csv.parse(",a, ,c,,");

		Assertions.assertEquals(FOption.of(""), strm.next());
		Assertions.assertEquals(FOption.of("a"), strm.next());
		Assertions.assertEquals(FOption.of(" "), strm.next());
		Assertions.assertEquals(FOption.of("c"), strm.next());
		Assertions.assertEquals(FOption.of(""), strm.next());
		Assertions.assertEquals(FOption.of(""), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test4() throws Exception {
		FStream<String> strm = m_csv.parse(",");

		Assertions.assertEquals(FOption.of(""), strm.next());
		Assertions.assertEquals(FOption.of(""), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test5() throws Exception {
		FStream<String> strm = m_csv.parse("aa,bbb\\, c");

		Assertions.assertEquals(FOption.of("aa"), strm.next());
		Assertions.assertEquals(FOption.of("bbb, c"), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test6() throws Exception {
		FStream<String> strm = m_csv.parse("aa,'bbb\\, c'");

		Assertions.assertEquals(FOption.of("aa"), strm.next());
		Assertions.assertEquals(FOption.of("'bbb, c'"), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test7() throws Exception {
		FStream<String> strm = m_csv2.parse("aa,'bbb\\, c'");

		Assertions.assertEquals(FOption.of("aa"), strm.next());
		Assertions.assertEquals(FOption.of("bbb\\, c"), strm.next());
		Assertions.assertEquals(FOption.empty(), strm.next());
	}

	@Test
	public void test8() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<String> strm = m_csv2.parse("aa,'bbb\\, c");

			Assertions.assertEquals(FOption.of("aa"), strm.next());
			Assertions.assertEquals(FOption.of("bbb\\, c"), strm.next());
			Assertions.assertEquals(FOption.empty(), strm.next());
		});
	}
}
