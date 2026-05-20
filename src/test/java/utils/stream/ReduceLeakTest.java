package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReduceLeakTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream = FStream.range(65, 70)
										.map(c -> Character.toString((char)(int)c))
										.take(5);
		stream = stream.reduceLeak((a,n) -> a+n);
		
		FOption<String> r;
		
		r = stream.next();
		Assertions.assertEquals("A", r.get());
		
		r = stream.next();
		Assertions.assertEquals("AB", r.get());
		
		r = stream.next();
		Assertions.assertEquals("ABC", r.get());
		
		r = stream.next();
		Assertions.assertEquals("ABCD", r.get());
		
		r = stream.next();
		Assertions.assertEquals("ABCDE", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.reduceLeak((a,n) -> a+n);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test2() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream = FStream.empty();
			stream = stream.reduceLeak(null);
			});
	}
}
