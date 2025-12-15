package utils.async;


import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopExecutionTest {
	private static final Duration INTERVAL_100MS = Duration.ofMillis(100);
	
	static class TestPeriodicLoop extends PeriodicLoopExecution<Integer> {
		private final int m_lastIteration;
		private int m_count = 0;

		TestPeriodicLoop(Duration interval, int lastIteration) {
			super(interval);

			m_lastIteration = lastIteration;
		}

		@Override
		protected Optional<Integer> performPeriodicAction(long loopIndex) throws Exception {
			if ( loopIndex >= m_lastIteration ) {
				return Optional.of(m_count);
			}
			++m_count;
			return Optional.empty();
		}
	};
	
	@Test
	public void testBasic() throws Exception {
		// 100ms 주기로 3번 iteration을 수행하는 loop
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 3);
		
		long started = System.currentTimeMillis();
		loop.start();
		int count = loop.waitForFinished().get();
		long elapsed = System.currentTimeMillis() - started;
		
		Assert.assertEquals(3, count);
		// 대충 300ms 정도 소요된다.
		Assert.assertTrue(elapsed > 250 && elapsed <= 350);
	}
	
	@Test
	public void testCancel() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 5);
		loop.start();
		
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(250);
				loop.cancel(true);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		Assert.assertTrue(aresult.isCancelled());
		Assert.assertEquals(3, loop.m_count);
	}
	
	@Test
	public void testCancelImmediately() throws Exception {
		// 100ms 주기로 5번 iteration을 수행하는 loop
		// 대충 500ms 정도 소요된다
		// 200ms 후에 cancel 시킨다.
		
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL_100MS, 5);

		long started = System.currentTimeMillis();
		loop.start();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(230);
				// cancel을 성공해야 함.
				boolean ret = loop.cancel(true);
				Assert.assertTrue(ret);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		long elapsed = System.currentTimeMillis() - started;
		
		// 200ms 후에 cancel 시키므로 중단되어야 함.
		Assert.assertTrue(aresult.isCancelled());
		// 2번 iteration 수행되어야 함.
		Assert.assertEquals(3, loop.m_count);
		Assert.assertTrue(elapsed < 250);
	}
}
