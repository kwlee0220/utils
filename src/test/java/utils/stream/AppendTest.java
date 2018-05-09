package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;
import utils.func.FOptional;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AppendTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, stream2);
		
		FOptional<Integer> r;
		
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
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList());
		FStream<Integer> stream = FStream.concat(stream1, stream2);
		
		FOptional<Integer> r;
		
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
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, stream2);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList());
		FStream<Integer> stream = FStream.concat(stream1, stream2);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(null, stream2);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, null);
	}
}
