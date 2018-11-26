package utils.async;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Executors {
	static final Logger s_logger = LoggerFactory.getLogger(Executors.class);
	
	private Executors() {
		throw new AssertionError("Should not be called: class=" + Executors.class);
	}
	
	public static <T> Executor<T> newFixedThreadPool(int nworkers) {
		return new FixedThreadPoolExecutor<>(nworkers);
	}
	
	public static <T> Executor<T> newSingleBuffer() {
		SingleBufferConsumerThread<T> exector = new SingleBufferConsumerThread<>();
		exector.startConsume();
		
		return exector;
	}
	
	public static <T> Executor<T> newDirectThread() {
		return new DirectThreadExecutor<>();
	}
	
	private static class FixedThreadPoolExecutor<T> implements Executor<T> {
		private final ExecutorService m_executor;
		
		FixedThreadPoolExecutor(int nworkers) {
			m_executor = java.util.concurrent.Executors.newFixedThreadPool(nworkers);
		}

		@Override
		public void submit(ExecutableExecution<T> handle) {
			m_executor.submit(handle.asRunnable());
		}

		@Override
		public void shutdown() {
			m_executor.shutdown();
		}
	}
	
	private static class DirectThreadExecutor<T> implements Executor<T> {
		DirectThreadExecutor() { }

		@Override
		public void submit(ExecutableExecution<T> handle) {
			handle.start();
		}

		@Override
		public void shutdown() { }
	}
}
