package utils.stream;


import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UnfoldTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream = FStream.unfold(0, i -> Tuple.of(""+i, i+1))
										.take(3);
		
		FOption<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("0", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("1", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("2", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=RuntimeException.class)
	public void test1() throws Exception {
		Function<Integer,Tuple2<String,Integer>> gen = (Integer i) -> {
			if ( i < 2 ) {
				return Tuple.of(""+i, i+1);
			}
			else {
				throw new RuntimeException();
			}
		};
		FStream<String> stream = FStream.unfold((Integer)0, gen);
		
		FOption<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("0", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("1", r.get());
		
		r = stream.next();
	}
	
	@Test(expected=NullPointerException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.unfold(0, null);
	}
	
	@Test(expected=NullPointerException.class)
	public void test3() throws Exception {
		FStream<String> stream = FStream.unfold((Integer)null, i -> Tuple.of(""+i, i+1));
	}
}
