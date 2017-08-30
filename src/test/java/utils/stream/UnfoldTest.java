package utils.stream;


import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UnfoldTest {
	@Test
	public void test0() throws Exception {
		Stream<String> stream = Stream.unfold(0, i -> Option.of(Tuple.of(""+i, i+1)))
										.take(3);
		
		Option<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals("0", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals("1", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals("2", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=RuntimeException.class)
	public void test1() throws Exception {
		Function<Integer,Option<Tuple2<String,Integer>>> gen = (Integer i) -> {
			if ( i < 2 ) {
				return Option.of(Tuple.of(""+i, i+1));
			}
			else {
				throw new RuntimeException();
			}
		};
		Stream<String> stream = Stream.unfold(0, gen);
		
		Option<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals("0", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals("1", r.get());
		
		r = stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test2() throws Exception {
		Stream<Integer> stream = Stream.unfold(0, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		Stream<String> stream = Stream.unfold((Integer)null, i -> Option.of(Tuple.of(""+i, i+1)));
	}
}
