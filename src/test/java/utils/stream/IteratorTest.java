package utils.stream;


import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IteratorTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4);
		
		Iterator<Integer> iter = stream.iterator();

		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals(1, (int)iter.next());
		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals(2, (int)iter.next());
		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals(4, (int)iter.next());
		Assert.assertFalse(iter.hasNext());
	}
	
	@Test(expected=NoSuchElementException.class)
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		
		Iterator<Integer> iter = stream.iterator();

		Assert.assertFalse(iter.hasNext());
		iter.next();
	}
}
