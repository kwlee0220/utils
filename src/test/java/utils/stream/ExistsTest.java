package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ExistsTest {
	@Test
	public void test0() throws Exception {
		Stream<Integer> stream;
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(true, stream.exists(i -> i > 3));
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(false, stream.exists(i -> i > 4));
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(true, stream.forAll(i -> i >= 1));
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(false, stream.forAll(i -> i >= 2));
	}

	@Test
	public void test2() throws Exception {
		Stream<Integer> stream;
		
		stream = Stream.empty();
		Assert.assertEquals(false, stream.exists(i -> i > 3));
		
		stream = Stream.empty();
		Assert.assertEquals(true, stream.forAll(i -> i > 3));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		Stream<Integer> stream;
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.exists(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		Stream<Integer> stream;
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.forAll(null);
	}

	@Test
	public void test5() throws Exception {
		Stream<String> stream;
		
		RuntimeException error = new RuntimeException();
		
		stream = Stream.of(Lists.newArrayList("t", "h", "i", "s"));
		Assert.assertEquals(false, stream.exists(s -> {throw error;}));
		
		stream = Stream.of(Lists.newArrayList("t", "h", "i", "s"));
		Assert.assertEquals(false, stream.forAll(s -> {throw error;}));
	}
}
