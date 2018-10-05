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
		public Execution<T> submit(ExecutableWork<T> job) {
			SimpleRunner<T> runner = new SimpleRunner<>(job);
			m_executor.submit(runner);
			
			return runner;
		}

		@Override
		public void submit(ExecutableHandle<T> handle) {
			m_executor.submit(handle);
		}

		@Override
		public void shutdown() {
			m_executor.shutdown();
		}
	}
	
	public static <T> ExecutionHandle<T> start(ExecutableWork<T> job) {
		SimpleRunner<T> runner = new SimpleRunner<>(job);
		new Thread(runner).start();
		
		return runner;
	}
	
	public static <T> void start(ExecutableHandle<T> job) {
		new Thread(job).start();
	}
	
	private static class DirectThreadExecutor<T> implements Executor<T> {
		DirectThreadExecutor() { }

		@Override
		public ExecutionHandle<T> submit(ExecutableWork<T> job) {
			SimpleRunner<T> runner = new SimpleRunner<>(job);
			new Thread(runner).start();
			
			return runner;
		}

		@Override
		public void submit(ExecutableHandle<T> handle) {
			new Thread(handle).start();
		}

		@Override
		public void shutdown() { }
	}
}
