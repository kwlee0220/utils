package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ExistsTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		Assertions.assertEquals(true, stream.exists(i -> i > 3));
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		Assertions.assertEquals(false, stream.exists(i -> i > 4));
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		Assertions.assertEquals(true, stream.allMatch(i -> i >= 1));
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		Assertions.assertEquals(false, stream.allMatch(i -> i >= 2));
	}

	@Test
	public void test2() throws Exception {
		FStream<Integer> stream;
		
		stream = FStream.empty();
		Assertions.assertEquals(false, stream.exists(i -> i > 3));
		
		stream = FStream.empty();
		Assertions.assertEquals(true, stream.allMatch(i -> i > 3));
	}
	
	@Test
	public void test3() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream;
		
			stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
			stream.exists(null);
			});
	}
	
	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream;
		
			stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
			stream.allMatch(null);
			});
	}

	@Test
	public void test5() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			FStream<String> stream;
		
			RuntimeException error = new RuntimeException();
		
			stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
			Assertions.assertEquals(false, stream.exists(s -> {throw error;}));
		
			stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));
			Assertions.assertEquals(false, stream.allMatch(s -> {throw error;}));
			});
	}
}
