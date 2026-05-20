package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MapTest {
	@BeforeEach
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4))
											.map(v -> v -1);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.map(v -> v -1);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
}
