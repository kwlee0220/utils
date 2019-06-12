package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FindNextTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream;
		FOption<Integer> r;
		
		stream = FStream.of(1, 2, 4, 1, 5);
		r = stream.findNext(i -> i > 3);
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(4, (int)r.get());
		
		r = stream.findNext(i -> i > 3);
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(5, (int)r.get());
		
		r = stream.findNext(i -> i > 3);
		Assert.assertEquals(true, r.isAbsent());
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		r = stream.findNext(i -> i > 4);
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream;
		FOption<Integer> r;
		
		stream = FStream.empty();
		r = stream.findNext(i -> i > 2);
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream.findNext(null);
	}

	@Test(expected=RuntimeException.class)
	public void test5() throws Exception {
		FStream<String> stream;
		boolean ret;
		
		RuntimeException error = new RuntimeException();
		
		stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		stream.findNext(s -> {throw error;});
	}
}
