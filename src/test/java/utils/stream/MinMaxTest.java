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
}
