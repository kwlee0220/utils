package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapFOptionTest {
	@BeforeEach
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.range(0, 5)
										.flatMapFOption(v -> (v%2==0) ? FOption.empty() : FOption.of(-v));
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(-1), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(-3), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.flatMapFOption(v -> (v%2==0) ? FOption.empty() : FOption.of(-v));
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
}
