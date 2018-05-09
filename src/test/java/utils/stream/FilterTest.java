package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOptional;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FilterTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5));
		stream = stream.filter(i -> i < 3);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.filter(i -> i > 5);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.filter(i -> i <= 3);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=RuntimeException.class)
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> { throw new RuntimeException(); });
		stream = stream.filter(i -> { throw new RuntimeException(); });
		
		stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.filter(null);
	}
}
