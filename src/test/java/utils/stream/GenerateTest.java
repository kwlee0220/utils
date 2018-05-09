package utils.stream;


import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import utils.func.FOptional;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GenerateTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.generate(0, i -> i+1).take(3);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test(expected=RuntimeException.class)
	public void test1() throws Exception {
		Function<Integer,Integer> gen = (Integer i) -> {
			if ( i < 2 ) {
				return i+1;
			}
			else {
				throw new RuntimeException();
			}
		};
		FStream<Integer> stream = FStream.generate(0, gen);
		
		FOptional<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.generate(0, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		Function<String,String> gen = s -> s;
		FStream<String> stream = FStream.generate(null, gen);
	}
}
