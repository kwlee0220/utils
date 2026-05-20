package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StartsWithTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream2 = FStream.from(Lists.newArrayList("a","b", "c"));
		
		Assertions.assertEquals(true, stream1.startsWith(stream2));
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream3 = FStream.from(Lists.newArrayList("a","b", "d"));
		
		Assertions.assertEquals(false, stream1.startsWith(stream3));
	}
	
	@Test
	public void test2() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream4 = FStream.empty();

		Assertions.assertEquals(true, stream1.startsWith(stream4));
	}
	
	@Test
	public void test3() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c"));
		FStream<String> stream2 = FStream.from(Lists.newArrayList("a","b", "c", "d"));

		Assertions.assertEquals(false, stream1.startsWith(stream2));
	}
	
	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
		
			Assertions.assertEquals(true, stream1.startsWith(null));
			});
	}
}
