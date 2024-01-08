package utils.stream;


import java.util.Arrays;
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
	
	@Test
	public void test1() throws Exception {
		List<Integer> ret;
		
		ret = FStream.repeat(9, 0).toList();
		Assert.assertEquals(0, ret.size());
		
		String r2 = FStream.repeat(9, 1).map(Object::toString).join("");
		Assert.assertEquals("9", r2);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test91() throws Exception {
		FStream.repeat(9, -1);
	}
	
	@Test
	public void test10() throws Exception {
		List<Integer> ret;
		List<Integer> seed = Arrays.asList(1, 2, 3);
		FStream<Integer> stream = FStream.repeat(seed)
										.flatMap(s -> FStream.from(s));
		
		ret = stream.take(7).toList();
		Assert.assertEquals(Arrays.asList(1, 2, 3, 1, 2, 3, 1), ret);
	}
	
	@Test
	public void test11() throws Exception {
		List<Integer> seed = Arrays.asList(1, 2, 3);
		String ret = FStream.repeat(seed, 3)
							.flatMap(s -> FStream.from(s))
							.map(i -> ""+i)
							.join("");
		
		Assert.assertEquals("123123123", ret);
	}
}
