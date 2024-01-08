package utils.stream;


import java.util.function.Predicate;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FilterTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5));
		stream = stream.filter(i -> i < 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.filter(i -> i > 5);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.filter(i -> i <= 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=RuntimeException.class)
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.dropWhile(i -> { throw new RuntimeException(); });
		stream = stream.filter(i -> { throw new RuntimeException(); });
		
		stream.next();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.filter(null);
	}

	@Test(expected=IllegalStateException.class)
	public void test6() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5));
		stream = stream.filter(i -> i < 3);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		stream.close();
		r = stream.next();
	}
	
	static class Parent {
		private String m_a;
		Parent(String a) { m_a = a; }
	}
	
	static class Child extends Parent {
		private int m_b;
		Child(String a, int b) { super(a); m_b = b; }
	}
	
	@Test
	public void test10() throws Exception {
		Predicate<Parent> pred = (Parent o) -> o.m_a.length() >= 2;
		FStream<Child> childStream = FStream.of(new Child("a", 1), new Child("aa", 2), new Child("aaa", 3));
		int[] array = childStream.filter(pred).map(c -> c.m_b).toIntFStream().toArray();
		Assert.assertEquals(2, array.length);
		Assert.assertArrayEquals(new int[]{2, 3}, array);
	}
}
