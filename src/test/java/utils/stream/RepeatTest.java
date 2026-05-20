package utils.stream;


import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class RepeatTest {
	@BeforeEach
	public void setUp() {
		
	}
	
	@Test
	public void test0() throws Exception {
		List<Integer> ret;
		
		ret = FStream.repeat(9, 999).toList();
		Assertions.assertEquals(999, ret.size());
		
		List<String> ret2 = FStream.repeat("a", 999).toList();
		Assertions.assertEquals(999, ret.size());
		for ( String s: ret2 ) {
			Assertions.assertEquals("a", s);
		}
	}
	
	@Test
	public void test1() throws Exception {
		List<Integer> ret;
		
		ret = FStream.repeat(9, 0).toList();
		Assertions.assertEquals(0, ret.size());
		
		String r2 = FStream.repeat(9, 1).map(Object::toString).join("");
		Assertions.assertEquals("9", r2);
	}

	@Test
	public void test91() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.repeat(9, -1);
			});
	}
	
	@Test
	public void test10() throws Exception {
		List<Integer> ret;
		List<Integer> seed = Arrays.asList(1, 2, 3);
		FStream<Integer> stream = FStream.repeat(seed)
										.flatMap(s -> FStream.from(s));
		
		ret = stream.take(7).toList();
		Assertions.assertEquals(Arrays.asList(1, 2, 3, 1, 2, 3, 1), ret);
	}
	
	@Test
	public void test11() throws Exception {
		List<Integer> seed = Arrays.asList(1, 2, 3);
		String ret = FStream.repeat(seed, 3)
							.flatMap(s -> FStream.from(s))
							.map(i -> ""+i)
							.join("");
		
		Assertions.assertEquals("123123123", ret);
	}
}
