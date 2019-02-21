package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ScanTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.generate(0, i -> i+1).take(5);
		stream = stream.scan((a,n) -> a+n);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(Integer.valueOf(6), r.get());
		
		r = stream.next();
		Assert.assertEquals(Integer.valueOf(10), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.scan((a,n) -> a+n);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test(expected=IllegalArgumentException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.scan(null);
	}
}
