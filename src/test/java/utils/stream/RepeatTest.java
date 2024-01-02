package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class RepeatTest {
	@Before
	public void setUp() {
		
	}
	
	@Test
	public void test0() throws Exception {
		List<Integer> ret;
		
		ret = FStream.repeat(9, 999).toList();
		Assert.assertEquals(999, ret.size());
		
		List<String> ret2 = FStream.repeat("a", 999).toList();
		Assert.assertEquals(999, ret.size());
		for ( String s: ret2 ) {
			Assert.assertEquals("a", s);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void test91() throws Exception {
		FStream.repeat(9, -1);
	}
}
