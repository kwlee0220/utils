package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ToArrayTest {
	@Test
	public void test0() throws Exception {
		Integer[] list = new Integer[] {1, 2, 4, 5, 3};
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 5, 3));
		
		Integer[] list2 = stream.toArray(Integer.class);
		Assert.assertArrayEquals(list, list2);
		
		Assert.assertArrayEquals(new Integer[0], FStream.empty().toArray(Integer.class));
	}
}
