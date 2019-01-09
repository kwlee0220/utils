package utils.stream;


import org.junit.Assert;
import org.junit.Test;

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
}
