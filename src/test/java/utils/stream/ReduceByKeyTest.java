package utils.stream;


import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReduceByKeyTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 5);
		
		Map<Integer,Integer> accums = stream.reduceByKey(i -> i%2, (a,v) -> a+v);
		Assertions.assertEquals(2, accums.size());
		Assertions.assertEquals(6, (int)accums.get(0));
		Assertions.assertEquals(7, (int)accums.get(1));
		Assertions.assertEquals(false, accums.containsKey(2));
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Map<Integer,Integer> accums = stream.reduceByKey(i -> i%2, (a,v) -> a+v);
		Assertions.assertEquals(0, accums.size());
	}
	
	@Test
	public void test2() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream = FStream.of();
		
			stream.reduceByKey(null, (a,v) -> a+v);
			});
	}
	
	@Test
	public void test3() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream = FStream.of();
		
			stream.reduceByKey(i -> i%2, null);
			});
	}
	
	@Test
	public void reducerReturningNullIsRejected() throws Exception {
		// 2026-11-02 이전에는 null 결과가 키를 지워 누적이 다시 시작되었다({a=a}).
		FStream<String> stream = FStream.of("a", "a", "a");
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
											() -> stream.reduceByKey(v -> v, (a,v) -> null));
		Assertions.assertTrue(e.getMessage().contains("reducer returned null"), e.getMessage());
	}
}
