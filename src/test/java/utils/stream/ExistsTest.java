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
		FStream<Integer> stream;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(true, stream.exists(i -> i > 3));
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(false, stream.exists(i -> i > 4));
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(true, stream.forAll(i -> i >= 1));
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		Assert.assertEquals(false, stream.forAll(i -> i >= 2));
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.empty();
		Assert.assertEquals(false, stream.exists(i -> i > 3));
		
		stream = FStream.empty();
		Assert.assertEquals(true, stream.forAll(i -> i > 3));
	}
	
	@Test(expected=NullPointerException.class)
	public void test3() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.exists(null);
	}
	
	@Test(expected=NullPointerException.class)
	public void test4() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.forAll(null);
	}

	@Test
	public void test5() throws Exception {
		FStream<String> stream;
		
		RuntimeException error = new RuntimeException();
		
		stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
		Assert.assertEquals(false, stream.exists(s -> {throw error;}));
		
		stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
		Assert.assertEquals(false, stream.forAll(s -> {throw error;}));
	}
}
