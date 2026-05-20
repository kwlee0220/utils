package utils.stream;


import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ToListTest {
	@Test
	public void test0() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 4, 5, 3);
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 5, 3));
		
		List<Integer> list2 = stream.toList();
		Assertions.assertEquals(list, list2);
		
		Assertions.assertEquals(Collections.emptyList(), FStream.empty().toList());
	}
}
