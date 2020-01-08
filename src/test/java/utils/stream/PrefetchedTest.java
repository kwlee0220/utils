package utils.stream;


import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrefetchTest {
	@Test
	public void test0() throws Exception {
		PrefetchStream<Integer> strm = FStream.range(0, 10)
										.delay(100, TimeUnit.MILLISECONDS)
										.prefetched(3);
		Assert.assertEquals(false, strm.avilable());

		long started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(0), strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started >= 100);
		
		TimeUnit.MILLISECONDS.sleep(200);
		started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(1), strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started < 50);
		Assert.assertEquals(Integer.valueOf(2), strm.next().getOrNull());
		Assert.assertTrue(System.currentTimeMillis() - started < 50);
	}
}
