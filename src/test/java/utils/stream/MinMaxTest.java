package utils.stream;


import java.util.Comparator;

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
		
		Assert.assertEquals(4, (int)stream.max(CMPTOR).get());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.max(CMPTOR).isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, 4, 1);
		
		Assert.assertEquals(5, (int)stream.max().get());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.max().isAbsent());
	}
	
	@Test
	public void test10() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		
		Assert.assertEquals(1, (int)stream.min(CMPTOR).get());
	}
	
	@Test
	public void test11() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.min(CMPTOR).isAbsent());
	}
	
	@Test
	public void test12() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, -2, 1);
		
		Assert.assertEquals(-2, (int)stream.min().get());
	}
	
	@Test
	public void test13() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.min().isAbsent());
	}
}
