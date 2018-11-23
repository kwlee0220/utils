package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.CheckedRunnable;
import utils.Throwables;
import utils.Unchecked.CheckedSupplier;

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
		public void submit(AbstractExecution<T> handle) {
			m_executor.submit(handle);
		}

		@Override
		public void shutdown() {
			m_executor.shutdown();
		}
	}
	
	public static <T extends Runnable> T start(T job) {
		new Thread(job).start();
		
		return job;
	}
	
	public static <T> Execution<T> start(CheckedSupplier<T> work) {
		EventDrivenExecution<T> handle = new EventDrivenExecution<>();
		
		handle.notifyStarting();
		
		Thread thread = new Thread(new RunnableWrapper<>(handle, work));
		handle.setCancelWork(() -> thread.interrupt());
		thread.start();
		
		return handle;
	}
	
	public static <T> Execution<T> start(ExecutionAwareWork<T> work) {
		EventDrivenExecution<T> handle = new EventDrivenExecution<>();
		
		handle.notifyStarting();
		
		Thread thread = new Thread(new WorkWrapper<>(handle, work));
		handle.setCancelWork(() -> thread.interrupt());
		thread.start();
		
		return handle;
	}
	
	public static Execution<Void> start(CheckedRunnable work) {
		EventDrivenExecution<Void> handle = new EventDrivenExecution<>();
		
		handle.notifyStarting();
		
		CheckedSupplier<Void> s = () -> { work.run(); return null; };
		Thread thread = new Thread(new RunnableWrapper<>(handle, s));
		handle.setCancelWork(() -> thread.interrupt());
		thread.start();
		
		return handle;
	}
	
	public static <T> Execution<T> start(ExecutableExecution<T> exec) {
		exec.notifyStarting();
		Thread thread = new Thread(exec);
		thread.start();
		
		return exec;
	}
	
	private static class RunnableWrapper<T> implements Runnable {
		private final EventDrivenExecution<T> m_handle;
		private final CheckedSupplier<T> m_work;
		
		RunnableWrapper(EventDrivenExecution<T> handle, CheckedSupplier<T> work) {
			m_work = work;
			m_handle = handle;
		}
		
		@Override
		public void run() {
			if ( !m_handle.notifyStarted() ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			
			// 작업을 수행한다.
			T result = null;
			try {
				result = m_work.get();
			}
			catch ( InterruptedException | CancellationException e ) {
				m_handle.cancel();
				return;
			}
			catch ( Throwable e ) {
				m_handle.notifyFailed(Throwables.unwrapThrowable(e));
				return;
			}
			
			m_handle.complate(result);
		}
	}
	
	private static class WorkWrapper<T> implements Runnable {
		private final EventDrivenExecution<T> m_handle;
		private final ExecutionAwareWork<T> m_work;
		
		WorkWrapper(EventDrivenExecution<T> handle, ExecutionAwareWork<T> work) {
			m_work = work;
			m_handle = handle;
		}
		
		@Override
		public void run() {
			if ( !m_handle.notifyStarted() ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			
			// 작업을 수행한다.
			T result = null;
			try {
				result = m_work.execute(m_handle);
			}
			catch ( InterruptedException | CancellationException e ) {
				m_handle.cancel();
				return;
			}
			catch ( Throwable e ) {
				m_handle.notifyFailed(Throwables.unwrapThrowable(e));
				return;
			}
			
			m_handle.complate(result);
		}
	}
	
	private static class DirectThreadExecutor<T> implements Executor<T> {
		DirectThreadExecutor() { }

		@Override
		public void submit(AbstractExecution<T> handle) {
			new Thread(handle).start();
		}

		@Override
		public void shutdown() { }
	}
}
