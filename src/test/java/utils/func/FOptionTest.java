package utils.func;


import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FOptionTest {
	private String m_str;
	
	@Before
	public void setUp() {
		m_str = null;
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void test2() throws Exception {
		FOption<String> opt = FOption.empty();

		opt.ifAbsentOrThrow(() -> m_str = "a");
		Assert.assertEquals("a", m_str);
		
		opt.ifAbsentOrThrow(() -> { throw new IllegalArgumentException(); });
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void test3() throws Exception {
		FOption<String> opt = FOption.of("z");

		opt.ifPresentOrThrow(v -> m_str = v);
		Assert.assertEquals("z", m_str);
		
		opt.ifPresentOrThrow(v -> { throw new IllegalArgumentException(); });
	}
	
	@Test
	public void test4() throws Exception {
		FStream<String> strm;
		
		strm = FOption.<String>empty().fstream();
		Assert.assertEquals(true, strm.next().isAbsent());
		
		strm = FOption.of("a").fstream();
		
		FOption<String> next = strm.next();
		Assert.assertEquals(true, next.isPresent());
		Assert.assertEquals("a", next.get());
		
		next = strm.next();
		Assert.assertTrue(next.isAbsent());
	}
	
	@Test(expected = NoSuchValueException.class)
	public void test5() throws Exception {
		FOption<String> opt = FOption.ofNullable("a");
		Assert.assertEquals(true, opt.isPresent());
		Assert.assertEquals("a", opt.get());
		
		opt.ifPresent(v -> m_str = v);
		Assert.assertEquals("a", m_str);
		
		FOption<String> opt2 = FOption.ofNullable(null);
		Assert.assertEquals(true, opt2.isAbsent());
		opt2.get();
	}
	
	@Test
	public void test6() throws Exception {
		Optional<String> opt2 = Optional.of("abc");
		FOption<String> opt = FOption.from(opt2);
		Assert.assertEquals(true, opt.isPresent());

		FOption<String> opt3 = FOption.from(Optional.<String>empty());
		Assert.assertEquals(true, opt3.isAbsent());
	}
}
