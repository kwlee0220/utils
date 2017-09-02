package utils.stream;


import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ToListTest {
	@Test
	public void test0() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 4, 5, 3);
		Stream<Integer> stream = Stream.of(Lists.newArrayList(1, 2, 4, 5, 3));
		
		List<Integer> list2 = stream.toList();
		Assert.assertEquals(list, list2);
		
		Assert.assertEquals(Collections.emptyList(), Stream.empty().toList());
	}
}
