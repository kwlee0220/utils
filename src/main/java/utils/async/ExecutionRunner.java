package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;

import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ExecutionRunner<T> implements Runnable {
	private final ExecutableWork<T> m_job;
	private final ExecutionHandle<T> m_handle;
	
	ExecutionRunner(ExecutableWork<T> job, ExecutionHandle<T> handle) {
		Objects.requireNonNull(job, "job is null");
		Objects.requireNonNull(handle, "ExecutionHandle is null");
		
		m_job = job;
		m_handle = handle;
	}
	
	ExecutionRunner(ExecutableWork<T> job) {
		Objects.requireNonNull(job, "job is null");
		
		m_job = job;
		m_handle = new ExecutionHandle<>(job);
	}
	
	ExecutableWork<T> getJob() {
		return m_job;
	}
	
	ExecutionHandle<T> getHandle() {
		return m_handle;
	}
	
	@Override
	public void run() {
		if ( !m_handle.notifyStarted(Thread.currentThread()) ) {
			// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
			return;
		}
		
		// 해당 작업이 수행 작업으로 선정된 것을 알린다.
		Executors.s_logger.debug("started: {}", m_handle);
		
		// 작업을 수행한다.
		try {
			T result = m_job.executeWork();
			
			switch ( m_handle.getState() ) {
				case RUNNING:
					// 작업 종료 후에 작업이 종료되었음을 알린다.
					m_handle.notifyCompleted(result);
					Executors.s_logger.debug("completed: {}, result={}", m_handle, result);
					break;
				case CANCELLING:
					m_handle.notifyCancelled();
					Executors.s_logger.info("cancelled: {}", m_handle);
					break;
				default:
			}
			
		}
		catch ( InterruptedException | CancellationException e ) {
			m_handle.notifyCancelled();
			Executors.s_logger.info("cancelled: {}", m_handle);
		}
		catch ( Throwable e ) {
			m_handle.notifyFailed(e);
			Executors.s_logger.info("failed: {}, cause={}", m_handle, Throwables.unwrapThrowable(e));
		}
	}
}