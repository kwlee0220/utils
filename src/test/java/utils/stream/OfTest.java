package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OfTest {
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList(1, 2, 4));
		
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
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.of(Lists.newArrayList());
		
		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.empty();
		
		Integer r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
}
