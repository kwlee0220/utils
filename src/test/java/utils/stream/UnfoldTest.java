package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import io.vavr.Tuple;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UnfoldTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream = FStream.unfold(0, i -> Tuple.of(i+1, ""+i))
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
		FStream<String> stream = FStream.unfold((Integer)0, (Integer i) -> {
			if ( i < 2 ) {
				return Tuple.of(i+1, ""+i);
			}
			else {
				throw new RuntimeException();
			}
		});
		
		FOption<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("0", r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("1", r.get());
		
		r = stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.unfold(0, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<String> stream = FStream.unfold((Integer)null, i -> Tuple.of(i+1, ""+i));
	}
}
