package utils.async;


import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardTest {
	private static final long OVERHEAD = 30;
	private static final Duration TIMEOUT = Duration.ofMillis(300);
	
	private Guard m_guard;
	private boolean m_interrupted = false;
	
	@Before
	public void setup() {
		m_guard = Guard.create();
	}
	
	@Test
	public void testAwaitFor() throws Exception {
		long started = System.currentTimeMillis();
		
		m_guard.lock();
		try {
			m_guard.awaitInGuardFor(Duration.ofMillis(300));
			long elapsed = System.currentTimeMillis() - started;
			long gap = Math.abs(elapsed - 300L);
			Assert.assertTrue(String.format("%d < %d", gap, OVERHEAD), gap < OVERHEAD);
		}
		finally {
			m_guard.unlock();
		}
	}
	
	@Test
	public void testAwaitUntil() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean result = m_guard.awaitUntil(() -> m_interrupted, TIMEOUT);
				Assert.assertTrue(!result);
			}
			catch ( InterruptedException e ) { }
		});
		future.join();
	}
	
	@Test
	public void testAwaitUntilWithInterrupt() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean result = m_guard.awaitUntil(() -> m_interrupted, TIMEOUT);
				Assert.assertTrue(!result);
			}
			catch ( InterruptedException e ) { }
		});
		Thread.sleep(100);
		m_guard.lock(); try { m_guard.signalAllInGuard(); } finally { m_guard.unlock(); }
		Thread.sleep(50);
		m_guard.lock(); try { m_guard.signalAllInGuard(); } finally { m_guard.unlock(); }
		future.join();
	}
	
	@Test
	public void testAwaitUntilWithCancel() throws Exception {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				boolean result = m_guard.awaitUntil(() -> m_interrupted, TIMEOUT);
				Assert.assertTrue(result);
			}
			catch ( InterruptedException e ) { }
		});
		Thread.sleep(100);
		m_guard.runAndSignalAll(() -> m_interrupted = true);
		future.join();
	}
}
