package utils.stream;


import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DelayStreamTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> strm = FStream.range(0, 5)
										.delay(100, TimeUnit.MILLISECONDS);
		
		for ( int i =0; i < 5; ++i ) {
			long started = System.currentTimeMillis();
			Assert.assertEquals(Integer.valueOf(i), strm.next().getOrNull());
			long elapsed = System.currentTimeMillis() - started;
			Assert.assertTrue(elapsed >= 80 && elapsed <= 120);
		}
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> strm = FStream.range(0, 1)
										.delay(300, TimeUnit.MILLISECONDS);
		
		long started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(0), strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started >= 300);

		Assert.assertEquals(null, strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started < 350);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> strm = FStream.range(0, 10)
										.delay(300, TimeUnit.MILLISECONDS);
		
		long started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(0), strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started >= 300);

		strm.close();
		Assert.assertEquals(null, strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started < 350);
	}
}
