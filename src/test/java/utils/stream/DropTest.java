package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DropTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.drop(2);
		
		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(4), r);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(1), r);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.drop(0);

		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(1), r);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(2), r);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(4), r);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(1), r);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.drop(4);

		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.drop(10);

		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test4() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.drop(1);

		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4, 1));
		stream = stream.drop(-1);
	}
}
