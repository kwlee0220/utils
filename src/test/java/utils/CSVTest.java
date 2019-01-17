package utils;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.func.FOption;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CSVTest {
	private CSV m_csv;
	private CSV m_csv2;
	
	@Before
	public void setup() {
		m_csv = CSV.get().withDelimiter(',').withEscape('\\');
		m_csv2 = CSV.get().withDelimiter(',').withEscape('\\').withQuote('\'');
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> strm = m_csv.parse("aa,bbb , c");
		
		Assert.assertEquals(FOption.of("aa"), strm.next());
		Assert.assertEquals(FOption.of("bbb "), strm.next());
		Assert.assertEquals(FOption.of(" c"), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<String> strm = m_csv.parse("");
		
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<String> strm = m_csv.parse(",a, ,c,,");

		Assert.assertEquals(FOption.of(""), strm.next());
		Assert.assertEquals(FOption.of("a"), strm.next());
		Assert.assertEquals(FOption.of(" "), strm.next());
		Assert.assertEquals(FOption.of("c"), strm.next());
		Assert.assertEquals(FOption.of(""), strm.next());
		Assert.assertEquals(FOption.of(""), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test4() throws Exception {
		FStream<String> strm = m_csv.parse(",");

		Assert.assertEquals(FOption.of(""), strm.next());
		Assert.assertEquals(FOption.of(""), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test5() throws Exception {
		FStream<String> strm = m_csv.parse("aa,bbb\\, c");

		Assert.assertEquals(FOption.of("aa"), strm.next());
		Assert.assertEquals(FOption.of("bbb, c"), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test6() throws Exception {
		FStream<String> strm = m_csv.parse("aa,'bbb\\, c'");

		Assert.assertEquals(FOption.of("aa"), strm.next());
		Assert.assertEquals(FOption.of("'bbb, c'"), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test
	public void test7() throws Exception {
		FStream<String> strm = m_csv2.parse("aa,'bbb\\, c'");

		Assert.assertEquals(FOption.of("aa"), strm.next());
		Assert.assertEquals(FOption.of("bbb\\, c"), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test8() throws Exception {
		FStream<String> strm = m_csv2.parse("aa,'bbb\\, c");

		Assert.assertEquals(FOption.of("aa"), strm.next());
		Assert.assertEquals(FOption.of("bbb\\, c"), strm.next());
		Assert.assertEquals(FOption.empty(), strm.next());
	}
}
