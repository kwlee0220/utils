package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FoldLeftTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		
		int sum = stream.foldLeft(0, (s,t) -> s+t);
		Assert.assertEquals(8, sum);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
		String c = stream.foldLeft("", (s,t) -> s+t);
		Assert.assertEquals("this", c);
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();

		int sum = stream.foldLeft(0, (s,t) -> s+t);
		Assert.assertEquals(0, sum);
	}
	
//	@Test(expected=IllegalArgumentException.class)
//	public void test3() throws Exception {
//		FStream<String> stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
//
//		String c = stream.foldLeft(null, (s,t) -> s+t);
//	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

		stream.foldLeft("", null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
		RuntimeException error = new RuntimeException();
		stream.foldLeft("", (s,t) -> {throw error;});
	}
}
