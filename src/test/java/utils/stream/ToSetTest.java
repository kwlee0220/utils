package utils.stream;


import java.util.Collections;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ToSetTest {
	@Test
	public void test0() throws Exception {
		Set<Integer> list = Sets.newHashSet(1, 2, 4, 5, 3);
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 5, 3));
		
		Set<Integer> list2 = stream.toSet();
		Assert.assertEquals(list, list2);
		
		Assert.assertEquals(Collections.emptySet(), FStream.empty().toSet());
	}
}
