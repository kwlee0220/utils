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
		public void submit(ExecutableWork<T> job, ExecutionHandle<T> handle) {
			m_executor.submit(new ExecutionRunner<>(job, handle));
		}

		@Override
		public ExecutionHandle<T> submit(ExecutableWork<T> job) {
			ExecutionHandle<T> handle = new ExecutionHandle<>(job);
			m_executor.submit(new ExecutionRunner<>(job, handle));
			
			return handle;
		}

		@Override
		public void submit(ExecutableHandle<T> handle) {
		}

		@Override
		public void shutdown() {
			m_executor.shutdown();
		}
	}
	
	public static <T> void start(ExecutableWork<T> job, ExecutionHandle<T> handle) {
		new Thread(new ExecutionRunner<>(job, handle)).start();
	}
	
	public static <T> ExecutionHandle<T> start(ExecutableWork<T> job) {
		ExecutionHandle<T> handle = new ExecutionHandle<>(job);
		new Thread(new ExecutionRunner<>(job, handle)).start();
		
		return handle;
	}
	
	public static <T> void start(ExecutableHandle<T> job) {
		new Thread(new ExecutionRunner<>(job, job)).start();
	}
	
	private static class DirectThreadExecutor<T> implements Executor<T> {
		DirectThreadExecutor() { }

		@Override
		public void submit(ExecutableWork<T> job, ExecutionHandle<T> handle) {
			new Thread(new ExecutionRunner<>(job, handle)).start();
		}

		@Override
		public ExecutionHandle<T> submit(ExecutableWork<T> job) {
			ExecutionHandle<T> handle = new ExecutionHandle<>(job);
			submit(job, handle);
			
			return handle;
		}

		@Override
		public void shutdown() { }
	}
}
