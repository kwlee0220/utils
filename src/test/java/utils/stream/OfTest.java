package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOptional;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OfTest {
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4));
		
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
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList());
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
}
