package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapTry {
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.range(0, 5)
										.flatMapTry(v -> (v%2==0) ? Try.failure(new Exception()) : Try.success(-v));
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(-1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(-3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.flatMapTry(v -> (v%2==0) ? Try.failure(new Exception()) : Try.success(-v));
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
}
