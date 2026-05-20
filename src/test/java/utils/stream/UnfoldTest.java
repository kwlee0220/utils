package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.Tuple;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UnfoldTest {
	@Test
	public void test0() throws Exception {
		FOption<String> r;
		FStream<String> stream = FStream.unfold(0, i -> Tuple.of(i+1, ""+i))
										.take(3);
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals("0", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals("1", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals("2", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
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
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals("0", r.get());
		
			r = stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals("1", r.get());
		
			r = stream.next();
			});
	}
	
	@Test
	public void test2() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.unfold(0, null);
			});
	}
	
	@Test
	public void test3() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.unfold((Integer)null, i -> Tuple.of(i+1, ""+i));
			});
	}
}
