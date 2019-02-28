package utils.func;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FOptionTest {
	@Before
	public void setUp() {
	}
	
	@Test
	public void test3() throws Exception {
		FStream<String> strm;
		
		strm = FOption.<String>empty().stream();
		Assert.assertEquals(true, strm.next().isAbsent());
		
		strm = FOption.of("a").stream();
		FOption<String> next = strm.next();
		Assert.assertEquals(true, next.isPresent());
		Assert.assertEquals("a", next.get());
	}
}
