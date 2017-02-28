package utils.thread;


import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrefetcherTest {
	private int m_count = 0;
	
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(100);
			return m_count++;
		}, 500, false, null);
		
		long ts0 = System.currentTimeMillis();
		Assert.assertEquals(0, (int)prefetcher.get());
		long ts1 = System.currentTimeMillis();
		Assert.assertEquals(1, m_count);
		Assert.assertTrue(ts1-ts0 >= 100);
		
		Thread.sleep(200);
		Assert.assertEquals(2, m_count);
		
		ts0 = System.currentTimeMillis();
		Assert.assertEquals(1, (int)prefetcher.get());
		ts1 = System.currentTimeMillis();
		Assert.assertTrue(ts1-ts0 < 50);
		Thread.sleep(200);
		Assert.assertEquals(3, m_count);
	}
	
	@Test
	public void test1() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(100);
			return m_count++;
		}, 500, false, null);
		
		Assert.assertEquals(0, (int)prefetcher.get());
		Assert.assertEquals(1, (int)prefetcher.get());
		Assert.assertEquals(2, (int)prefetcher.get());
		
		Thread.sleep(200);
		Assert.assertEquals(4, m_count);
	}
	
	@Test
	public void test2() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(100);
			return m_count++;
		}, 300, false, null);
		
		Assert.assertEquals(0, (int)prefetcher.get());
		Thread.sleep(500);
		Assert.assertEquals(2, (int)prefetcher.get());
		Thread.sleep(200);
		Assert.assertEquals(4, m_count);
	}
	
	@Test
	public void test3() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(200);
			return m_count++;
		}, 100, false, null);
		
		Assert.assertEquals(0, (int)prefetcher.get());
		Thread.sleep(500);
		Assert.assertEquals(2, (int)prefetcher.get());
		Thread.sleep(500);
		Assert.assertEquals(4, m_count);
	}
	
	@Test
	public void test4() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(100);
			int v = m_count++;
			if ( v % 2 == 1 ) {
				throw new IllegalStateException();
			}
			return v;
		}, 300, false, null);
		
		Assert.assertEquals(0, (int)prefetcher.get());
		try {
			prefetcher.get();
			Assert.fail();
		}
		catch ( ExecutionException e ) {
			Assert.assertEquals(IllegalStateException.class, e.getCause().getClass());
		}
	}
	
	@Test
	public void testReuse() throws Exception {
		Prefetcher<Integer> prefetcher = new Prefetcher<>(()-> {
			Thread.sleep(300);
			return m_count++;
		}, 500, false, null);
		
		Assert.assertEquals(0, (int)prefetcher.get());
		Assert.assertEquals(1, (int)prefetcher.get());
		Assert.assertEquals(1, (int)prefetcher.get());
		Assert.assertEquals(1, (int)prefetcher.get());
		Thread.sleep(300);
		Assert.assertEquals(2, (int)prefetcher.get());
		Assert.assertEquals(2, (int)prefetcher.get());
		Assert.assertEquals(2, (int)prefetcher.get());
	}
}
