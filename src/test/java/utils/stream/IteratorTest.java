package utils.stream;


import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IteratorTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4);
		
		Iterator<Integer> iter = stream.iterator();

		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(1, (int)iter.next());
		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(2, (int)iter.next());
		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(4, (int)iter.next());
		Assertions.assertFalse(iter.hasNext());
	}
	
	@Test
	public void test1() throws Exception {
		Assertions.assertThrows(NoSuchElementException.class, () -> {
			FStream<Integer> stream = FStream.empty();
		
			Iterator<Integer> iter = stream.iterator();

			Assertions.assertFalse(iter.hasNext());
			iter.next();
			});
	}
}
