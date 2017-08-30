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
		Stream<Integer> stream;
		Option<Integer> ret;
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.find(i -> i > 3);
		Assert.assertEquals(true, ret.isDefined());
		Assert.assertEquals(Integer.valueOf(4), ret.get());
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		ret = stream.find(i -> i > 4);
		Assert.assertEquals(true, ret.isEmpty());
	}

	@Test
	public void test2() throws Exception {
		Stream<Integer> stream;
		Option<Integer> ret;
		
		stream = Stream.empty();
		ret = stream.find(i -> i > 2);
		Assert.assertEquals(true, ret.isEmpty());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		Stream<Integer> stream;
		
		stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream.find(null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		Stream<String> stream;
		Option<String> ret;
		
		RuntimeException error = new RuntimeException();
		
		stream = Stream.of(Lists.newArrayList("t", "h", "i", "s"));
		ret = stream.find(s -> {throw error;});
	}
}
