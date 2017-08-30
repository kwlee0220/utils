package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class TakeTest {
	@Test
	public void test0() throws Exception {
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.take(2);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.take(4);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.take(10);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test3() throws Exception {
		Stream<Integer> stream = Stream.empty();
		stream = stream.take(10);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test4() throws Exception {
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.take(0);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.take(-1);
	}
}
