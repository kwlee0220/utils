package utils.stream;


import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GenerateTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.generate(0, v -> v+1).take(3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test1() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Function<Integer,Integer> gen = (Integer i) -> {
				if ( i < 2 ) {
					return i+1;
				}
				else {
					throw new RuntimeException();
				}
			};
			FStream<Integer> stream = FStream.generate(0, gen);
		
			FOption<Integer> r;
		
			r = stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(0), r.get());
		
			r = stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(1), r.get());
		
			r = stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(2), r.get());
		
			r = stream.next();
			});
	}
	
	@Test
	public void test2() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.generate(0, null);
			});
	}
	
	@Test
	public void test3() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Function<String,String> gen = s -> s;
			FStream.generate(null, gen);
			});
	}
}
