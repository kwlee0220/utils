package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOptional;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FindTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream;
		FOptional<Integer> ret;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.find(i -> i > 3);
		Assert.assertEquals(true, ret.isPresent());
		Assert.assertEquals(Integer.valueOf(4), ret.get());
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.find(i -> i > 4);
		Assert.assertEquals(true, ret.isAbsent());
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream;
		FOptional<Integer> ret;
		
		stream = FStream.empty();
		ret = stream.find(i -> i > 2);
		Assert.assertEquals(true, ret.isAbsent());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.find(null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		FStream<String> stream;
		FOptional<String> ret;
		
		RuntimeException error = new RuntimeException();
		
		stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
		ret = stream.find(s -> {throw error;});
	}
}
