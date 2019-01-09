package utils.stream;


import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MinMaxTest {
	private static final Comparator<Integer> CMPTOR = (i1,i2) -> i1-i2;
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		
		List<Integer> max = stream.max(CMPTOR);
		Assert.assertEquals(1, max.size());
		Assert.assertEquals(4, (int)max.get(0));
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.max(CMPTOR).isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 4);
		
		List<Integer> max = stream.max(CMPTOR);
		Assert.assertEquals(4, (int)max.get(0));
		Assert.assertEquals(2, max.size());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, 4, 1);

		List<Integer> max = stream.max();
		Assert.assertEquals(1, max.size());
		Assert.assertEquals(5, (int)max.get(0));
	}
	
	@Test
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.max().isEmpty());
	}
	
	@Test
	public void test10() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);

		List<Integer> min = stream.min(CMPTOR);
		Assert.assertEquals(2, min.size());
		Assert.assertEquals(1, (int)min.get(0));
	}
	
	@Test
	public void test11() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.min(CMPTOR).isEmpty());
	}
	
	@Test
	public void test12() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, -2, 1);


		List<Integer> min = stream.min();
		Assert.assertEquals(1, min.size());
		Assert.assertEquals(-2, (int)min.get(0));
	}
	
	@Test
	public void test13() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.min().isEmpty());
	}
}
