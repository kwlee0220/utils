package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AppendTest {
	@Test
	public void test0() throws Exception {
		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
		Stream<Integer> stream = Stream.concat(stream1, stream2);
		
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
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList());
		Stream<Integer> stream = Stream.concat(stream1, stream2);
		
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
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		Stream<Integer> stream1 = Stream.empty();
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
		Stream<Integer> stream = Stream.concat(stream1, stream2);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test3() throws Exception {
		Stream<Integer> stream1 = Stream.empty();
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList());
		Stream<Integer> stream = Stream.concat(stream1, stream2);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
		Stream<Integer> stream = Stream.concat(null, stream2);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
		Stream<Integer> stream = Stream.concat(stream1, null);
	}
}
