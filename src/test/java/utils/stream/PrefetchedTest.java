package utils.stream;


import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;

import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrefetchedTest {
	@Test
	public void test0() throws Exception {
		PrefetchStream<Integer> strm = FStream.range(0, 5)
												.delay(100, MILLISECONDS)
												.prefetched(3);
		Assert.assertEquals(false, strm.available());
		
		long started, elapsed;
		
		started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(0), strm.next().getOrNull());
		elapsed = System.currentTimeMillis() - started;
		Assert.assertTrue(elapsed >= 90 && elapsed < 150);
		
		MILLISECONDS.sleep(300);
		Assert.assertEquals(true, strm.available());
		
		for ( int i =1; i < 4; ++i ) {
			started = System.currentTimeMillis();
			Assert.assertEquals(Integer.valueOf(i), strm.next().getOrNull());
			elapsed = System.currentTimeMillis() - started;
			Assert.assertTrue(elapsed <= 10);
		}
		
		started = System.currentTimeMillis();
		Assert.assertEquals(Integer.valueOf(4), strm.next().getOrNull());
		elapsed = System.currentTimeMillis() - started;
		Assert.assertTrue(elapsed >= 80 && elapsed < 120);
	}
	
	@Test
	public void test1() throws Exception {
		FOption<Try<Integer>> r;
		
		PrefetchStream<Integer> strm = FStream.range(0, 4)
												.delay(100, MILLISECONDS)
												.prefetched(3);
		r = strm.next(50, MILLISECONDS);
		Assert.assertTrue(r.get().isFailure());
		Assert.assertEquals(TimeoutException.class, r.get().getCause().getClass());

		MILLISECONDS.sleep(300);
		
		for ( int i =0; i < 3; ++i ) {
			long started = System.currentTimeMillis();
			r = strm.next(0, MILLISECONDS);
			Assert.assertEquals(i, r.get().get().intValue());
			long elapsed = System.currentTimeMillis() - started;
			Assert.assertTrue(elapsed <= 10);
		}
		
		r = strm.next(100, MILLISECONDS);
		Assert.assertTrue(r.get().isSuccess());
		Assert.assertEquals(3, r.get().get().intValue());
	}
}
