package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class TakeWhileTest {
	private String toString(FStream<Integer> strm) {
		return strm.map(v -> "" + v).join("");
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		String ret;
		
		ret = toString(stream.takeWhile(i -> i <= 3));
		Assert.assertEquals("12", ret);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		String ret;
		
		ret = toString(stream.takeWhile(i -> i <= 5));
		Assert.assertEquals("1241", ret);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		String ret;
		
		ret = toString(stream.takeWhile(i -> i <= 0));
		Assert.assertEquals("", ret);
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.takeWhile(i -> i <= 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test(expected=RuntimeException.class)
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.takeWhile(i -> { throw new RuntimeException(); });
		
		stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.takeWhile(null);
	}
}
