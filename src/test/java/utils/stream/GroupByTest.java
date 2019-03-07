package utils.stream;


import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import utils.KeyValue;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GroupByTest {
	@Test
	public void test0() throws Exception {
		Integer v;
		
		IntFStream base = FStream.range(0, 10);
		FStream<KeyedFStream<Integer,Integer>> groups = base.groupBy(v2 -> v2 % 3);
		
		KeyedFStream<Integer,Integer> group0 = groups.next().getOrNull();
		Assert.assertNotNull(group0);
		Assert.assertEquals(Integer.valueOf(0), group0.getKey());
		
		Assert.assertEquals(Integer.valueOf(0), group0.next().getOrNull());
		
		KeyedFStream<Integer,Integer> group1 = groups.next().getOrNull();
		Assert.assertNotNull(group1);
		Assert.assertEquals(Integer.valueOf(1), group1.getKey());
		
		Assert.assertEquals(Integer.valueOf(1), group1.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(4), group1.next().getOrNull());
		
		KeyedFStream<Integer,Integer> group2 = groups.next().getOrNull();
		Assert.assertNotNull(group2);
		Assert.assertEquals(Integer.valueOf(2), group2.getKey());
		
		Assert.assertEquals(Integer.valueOf(2), group2.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(5), group2.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(8), group2.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(7), group1.next().getOrNull());
		
		KeyedFStream<Integer,Integer> group3 = groups.next().getOrNull();
		Assert.assertNull(group3);

		Assert.assertEquals(Integer.valueOf(3), group0.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(6), group0.next().getOrNull());
		Assert.assertEquals(Integer.valueOf(9), group0.next().getOrNull());
	}
	
	@Test
	public void test1() throws Exception {
		IntFStream base = FStream.range(0, 10);
		
		KeyedGroups<Integer,Integer> groups = base.groupByKey(v2 -> v2 % 3);
		
		Assert.assertEquals(true, groups.containsKey(0));
		Assert.assertEquals(true, groups.containsKey(1));
		Assert.assertEquals(true, groups.containsKey(2));
		Assert.assertEquals(false, groups.containsKey(3));
	}
	
	@Test
	public void test2() throws Exception {
		IntFStream base = FStream.range(0, 10);
		
		KeyedGroups<Integer,Integer> groups = base.groupByKey(v2 -> v2 % 3);
		
		Assert.assertEquals(Arrays.asList(0, 3, 6, 9), groups.get(0));
		Assert.assertEquals(Arrays.asList(1, 4, 7), groups.get(1));
		Assert.assertEquals(Arrays.asList(2, 5, 8), groups.get(2));
		Assert.assertEquals(Arrays.asList(), groups.get(3));
	}
	
	@Test
	public void test3() throws Exception {
		IntFStream base = FStream.range(0, 10);
		
		KeyedGroups<Integer,Integer> groups = base.groupByKey(k -> k % 3,
															v -> v + (v%3));
		
		Assert.assertEquals(Arrays.asList(0, 3, 6, 9), groups.get(0));
		Assert.assertEquals(Arrays.asList(2, 5, 8), groups.get(1));
		Assert.assertEquals(Arrays.asList(4, 7, 10), groups.get(2));
		Assert.assertEquals(Arrays.asList(), groups.get(3));
	}
	
	@Test
	public void test4() throws Exception {
		IntFStream base = FStream.range(0, 5);
		KeyedGroups<Integer,Integer> groups = base.groupByKey(v2 -> v2 % 3);
		
		List<KeyValue<Integer,Integer>> list = groups.ungroup().toList();
		List<KeyValue<Integer,Integer>> expected
								= Arrays.asList(KeyValue.of(0, 0), KeyValue.of(0, 3),
												KeyValue.of(1, 1), KeyValue.of(1, 4),
												KeyValue.of(2, 2));
		Assert.assertEquals(expected, list);
	}
}
