package utils.stream;


import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReduceByKeyTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 5);
		
		Map<Integer,Integer> accums = stream.reduceByKey(i -> i%2, (a,v) -> a+v);
		Assert.assertEquals(2, accums.size());
		Assert.assertEquals(6, (int)accums.get(0));
		Assert.assertEquals(7, (int)accums.get(1));
		Assert.assertEquals(false, accums.containsKey(2));
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Map<Integer,Integer> accums = stream.reduceByKey(i -> i%2, (a,v) -> a+v);
		Assert.assertEquals(0, accums.size());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		stream.reduceByKey(null, (a,v) -> a+v);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		stream.reduceByKey(i -> i%2, null);
	}
}
