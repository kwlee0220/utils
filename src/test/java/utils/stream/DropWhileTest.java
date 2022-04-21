package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DropWhileTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> i <= 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
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
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> i <= 0);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> i <= 5);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.dropWhile(i -> i <= 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=RuntimeException.class)
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> { throw new RuntimeException(); });
		stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(null);
	}
}
