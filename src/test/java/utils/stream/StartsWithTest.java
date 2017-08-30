package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StartsWithTest {
	@Test
	public void test0() throws Exception {
		Stream<String> stream1 = Stream.of(Lists.newArrayList("a","b", "c", "d"));
		Stream<String> stream2 = Stream.of(Lists.newArrayList("a","b", "c"));
		
		Assert.assertEquals(true, stream1.startsWith(stream2));
	}
	
	@Test
	public void test1() throws Exception {
		Stream<String> stream1 = Stream.of(Lists.newArrayList("a","b", "c", "d"));
		Stream<String> stream3 = Stream.of(Lists.newArrayList("a","b", "d"));
		
		Assert.assertEquals(false, stream1.startsWith(stream3));
	}
	
	@Test
	public void test2() throws Exception {
		Stream<String> stream1 = Stream.of(Lists.newArrayList("a","b", "c", "d"));
		Stream<String> stream4 = Stream.empty();

		Assert.assertEquals(true, stream1.startsWith(stream4));
	}
	
	@Test
	public void test3() throws Exception {
		Stream<String> stream1 = Stream.of(Lists.newArrayList("a","b", "c"));
		Stream<String> stream2 = Stream.of(Lists.newArrayList("a","b", "c", "d"));

		Assert.assertEquals(false, stream1.startsWith(stream2));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
		
		Assert.assertEquals(true, stream1.startsWith(null));
	}
}
