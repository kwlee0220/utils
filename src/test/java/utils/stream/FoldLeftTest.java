package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FoldLeftTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		
		int sum = stream.fold(0, (s,t) -> s+t);
		Assertions.assertEquals(8, sum);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
		String c = stream.fold("", (s,t) -> s+t);
		Assertions.assertEquals("this", c);
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();

		int sum = stream.fold(0, (s,t) -> s+t);
		Assertions.assertEquals(0, sum);
	}
	
//	@Test(expected=IllegalArgumentException.class)
//	public void test3() throws Exception {
//		FStream<String> stream = FStream.of(Lists.newArrayList("t", "h", "i", "s"));
//
//		String c = stream.fold(null, (s,t) -> s+t);
//	}
	
	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

			stream.fold("", null);
			});
	}

	@Test
	public void test5() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
		
			RuntimeException error = new RuntimeException();
			stream.fold("", (s,t) -> {throw error;});
			});
	}
}
