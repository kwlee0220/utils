package utils.stream;


import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CycleTest {
	@Before
	public void setUp() {
		
	}
	
	@Test
	public void test0() throws Exception {
		List<Integer> ret;
		FStream<Integer> stream = FStream.cycle(Arrays.asList(1, 2, 3));
		
		ret = stream.take(7).toList();
		Assert.assertEquals(Arrays.asList(1, 2, 3, 1, 2, 3, 1), ret);
	}
}
