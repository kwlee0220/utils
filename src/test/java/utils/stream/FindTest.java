package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FindTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream;
		boolean ret;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.anyMatch(i -> i > 3);
		Assert.assertEquals(true, ret);
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.anyMatch(i -> i > 4);
		Assert.assertEquals(false, ret);
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream;
		boolean ret;
		
		stream = FStream.empty();
		ret = stream.anyMatch(i -> i > 2);
		Assert.assertEquals(false, ret);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.anyMatch(null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		FStream<String> stream;
		boolean ret;
		
		RuntimeException error = new RuntimeException();
		
		stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
		ret = stream.anyMatch(s -> {throw error;});
	}
}
