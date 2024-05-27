package utils.func;


import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FuncTest {
	
	@Before
	public void setUp() {
	}
	
	@Test
	public void testReplaceIf_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 1);
		
		Function<Integer,Integer> func = v -> -v;
		List<Integer> old = Funcs.replaceIf(list, i -> i == 3, func);
		Assert.assertEquals(1, old.size());
		Assert.assertEquals(3, (int)old.get(0));
		Assert.assertEquals(List.of(1, 2, -3, 1), list);
	}
	
	@Test
	public void testReplaceIf_2() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 1);
		
		Function<Integer,Integer> func = v -> -v;
		List<Integer> old = Funcs.replaceIf(list, i -> i == 4, func);
		Assert.assertEquals(0, old.size());
		Assert.assertEquals(List.of(1, 2, 3, 1), list);
	}
	
	@Test
	public void testReplaceIf_3() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4);
		
		Function<Integer,Integer> func = v -> -v;
		List<Integer> old = Funcs.replaceIf(list, i -> i%2 == 1, func);
		Assert.assertEquals(2, old.size());
		Assert.assertEquals(List.of(1, 3), old);
		Assert.assertEquals(List.of(-1, 2, -3, 4), list);
	}
	
	@Test
	public void testReplaceFirst1_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 3);
		
		Function<Integer,Integer> func = v -> v + 1;
		Integer old = Funcs.replaceFirst(list, i -> i == 3, func);
		Assert.assertEquals((int)old, 3);
		Assert.assertEquals(List.of(1, 2, 4, 4, 3), list);
	}
	
	@Test
	public void testReplaceFirst1_2() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);
		
		Function<Integer,Integer> func = v -> v + 1;
		Integer old = Funcs.replaceFirst(list, i -> i == 7, func);
		Assert.assertEquals(old, null);
	}
	
	@Test
	public void testReplaceFirst2_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);
		
		Integer old = Funcs.replaceFirst(list, i -> i == 3, -1);
		Assert.assertEquals((int)old, 3);
		Assert.assertEquals(-1, (int)list.get(2));
	}
	
	@Test
	public void testReplaceOrInsertFirst_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);
		
		Function<Integer,Integer> func = v -> v + 1;
		int old = Funcs.replaceOrInsertFirst(list, i -> i == 3, func);
		Assert.assertEquals(old, 3);
		Assert.assertEquals(4, (int)list.get(2));
	}
	
	@Test
	public void testReplaceOrInsertFirst_2() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);
		
		Function<Integer,Integer> func = v -> -7;
		Integer old = Funcs.replaceOrInsertFirst(list, i -> i == 7, func);
		Assert.assertEquals(old, null);
		Assert.assertEquals(3, (int)list.get(2));
		Assert.assertEquals(7, list.size());
		Assert.assertEquals(-7, (int)list.get(6));
	}
}
