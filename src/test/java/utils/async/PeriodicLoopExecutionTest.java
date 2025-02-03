package utils.async;


import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Test;

import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopExecutionTest {
	private static final Duration INTERVAL = Duration.ofMillis(100);
	
	static class TestPeriodicLoop extends PeriodicLoopExecution<Integer> {
		private final int m_lastIteration;
		private int m_count = 0;

		TestPeriodicLoop(Duration interval, int lastIteration) {
			super(interval);

			m_lastIteration = lastIteration;
		}

		@Override
		protected FOption<Integer> performPeriodicAction(long loopIndex) throws Exception {
			if ( loopIndex >= m_lastIteration ) {
				return FOption.of(m_count);
			}
			++m_count;
			return FOption.empty();
		}
	};
	
	@Test
	public void testBasic() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL, 3);
		loop.start();
		
		int count = loop.waitForFinished().get();
		Assert.assertEquals(3, count);
	}
	
	@Test
	public void testCancel() throws Exception {
		TestPeriodicLoop loop = new TestPeriodicLoop(INTERVAL, 5);
		loop.start();
		
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(200);
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
		TestPeriodicLoop loop = new TestPeriodicLoop(Duration.ofMillis(10000), 5);
		loop.start();
		
		long started = System.currentTimeMillis();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(200);
				loop.cancel(true);
			}
			catch ( InterruptedException e ) { }
		});
		AsyncResult<Integer> aresult = loop.waitForFinished();
		long elapsed = System.currentTimeMillis() - started;
		
		Assert.assertTrue(aresult.isCancelled());
		Assert.assertEquals(1, loop.m_count);
		Assert.assertTrue(elapsed < 250);
	}
}
