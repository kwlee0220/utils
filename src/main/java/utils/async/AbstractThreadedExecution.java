package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractThreadedExecution<T> extends AbstractAsyncExecution<T> {
	private boolean m_isDaemonThread = true;
	
	public boolean isDaemonThread() {
		return m_isDaemonThread;
	}
	public void setDaemonThread(boolean isDaemon) {
		m_isDaemonThread = isDaemon;
	}
	
	/**
	 * 비동기적으로 작업을 수행한다.
	 * <p>
	 * 본 메소드는 별도로 생성된 쓰레드에 의해 수행되고, 함수 호출이 종료될 때까지 대기된다.
	 * 함수 수행이 예외 발생없이 반환된 경우는 작업이 완료된 것으로 간주된다.
	 * <p>
	 * 메소드 수행 중 발생하는 예외에 따라 비동기 작업의 결과가 결정된다.
	 * <dl>
	 * 	<dt>InterruptedException</dt>
	 * 	<dd>비동기 작업이 수행 중단된 것으로 간주된다.
	 * 		{@link Execution#isCancelled()} 가 {@code true} 가 됨.</dd>
	 * 	<dt>CancellationException</dt>
	 * 	<dd>비동기 작업이 수행 중단된 것으로 간주된다.
	 * 		{@link Execution#isCancelled()} 가 {@code true} 가 됨.</dd>
	 * </dl>
	 * 	<dt>Exception</dt>
	 * 	<dd>비동기 작업이 수행 중에 오류 발생으로 실패한 것으로 간주된다.
	 * 		{@link Execution#isFailed()} 가 {@code true} 가 됨.</dd>
	 * </dl>
	 * 
	 * @return	수행 작업의 결과
	 * @throws InterruptedException	작업 수행중 수행 쓰레드가 중단된 경우
	 * @throws CancellationException	작업 수행이 중단된 경우
	 * @throws Exception	작업 수행 중 오류 발생으로 작업 실패된 경우
	 */
	protected abstract T executeWork() throws InterruptedException, CancellationException, Exception;
	
	public T run() throws CancellationException, InterruptedException, ExecutionException {
		notifyStarted();
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			if ( notifyCompleted(result) ) {
				return result;
			}
			if ( notifyCancelled() ) {
				throw new CancellationException();
			}
			
			return poll().get();
		}
		catch ( InterruptedException | CancellationException e ) {
			notifyCancelled();
			throw e;
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			Throwables.throwIfInstanceOf(e, ExecutionException.class);
			
			throw new ExecutionException(cause);
		}
	}
	
	@Override
	public final void start() {
		notifyStarting();
		
		Runnable work = asRunnable();
		Executor exector = getExecutor();
		if ( exector != null ) {
			exector.execute(work);
		}
		else {
			Thread thread = new Thread(work);
			thread.setDaemon(m_isDaemonThread);
			thread.start();
		}
	}
	
	private Runnable asRunnable() {
		return new Runner();
	}

	private class Runner implements Runnable {
		@Override
		public void run() {
			try {
				notifyStarted();
			}
			catch ( Exception e ) {
				// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
				return;
			}
			
			try {
				T result = executeWork();
				if ( notifyCompleted(result) ) {
					return;
				}
				if ( notifyCancelled() ) {
					return;
				}
			}
			catch ( InterruptedException | CancellationException e ) {
				notifyCancelled();
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}
}
