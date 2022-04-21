package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReduceTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		
		int sum = stream.reduce((s,t) -> s+t);
		Assert.assertEquals(8, sum);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
		String c = stream.reduce((s,t) -> s+t);
		Assert.assertEquals("this", c);
	}
	
	@Test(expected=IllegalStateException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();
		
		stream.reduce((s,t) -> s+t);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

		stream.reduce(null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
		RuntimeException error = new RuntimeException();
		stream.reduce((s,t) -> {throw error;});
	}
	
	@Test
	public void test6() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t"));
		
		String c = stream.reduce((s,t) -> s+t);
		Assert.assertEquals("t", c);
	}
}
