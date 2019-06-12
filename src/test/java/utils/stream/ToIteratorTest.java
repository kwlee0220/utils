package utils.stream;


import java.util.Iterator;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ToIteratorTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4);
		Iterator<Integer> iter = stream.iterator();
		
		Assert.assertEquals(1, iter.next().intValue());
		Assert.assertEquals(2, iter.next().intValue());
		Assert.assertEquals(4, iter.next().intValue());
		Assert.assertEquals(false, iter.hasNext());
	}
	
	@Test(timeout = 1000)
	public void test1() throws Exception {
		Stream<Integer> jstrm = Stream.iterate(0, seed -> {
			try { Thread.sleep(100); } catch ( InterruptedException e ) { }
			return seed + 1;
		});
		FStream<Integer> strm = FStream.from(jstrm);
		
		Iterator<Integer> iter = strm.stream().limit(16).iterator();
		for ( int i =0; i < 8; ++i ) {
			Assert.assertEquals((Integer)i, iter.next());
		}
	}
}
