package utils.async;

import java.io.Serializable;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;

import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;
import utils.RuntimeTimeoutException;
import utils.Throwables;
import utils.func.CheckedRunnable;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardedRunnable extends GuardedAction<GuardedRunnable> implements Serializable, CheckedRunnable {
	private static final long serialVersionUID = 1L;
	
	private final CheckedRunnable m_task;
	
	private GuardedRunnable(Guard guard, CheckedRunnable runnable) {
		super(guard);
		Preconditions.checkArgument(runnable != null, "Runnable is null");
		
        m_task = runnable;
	}
	
	/**
	 * 주어진 {@link Guard}와 {@link CheckedRunnable}로 {@link GuardedRunnable} 객체를 생성한다.
	 * 
	 * @param guard    {@link Guard} 객체.
	 * @param supplier {@link CheckedRunnable} 객체.
	 * @return {@link GuardedSupplier} 객체.
	 */
	public static <T> GuardedRunnable from(Guard guard, CheckedRunnable task) {
		return new GuardedRunnable(guard, task);
	}
	
	/**
	 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
	 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
	 * <p>
	 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
	 * {@link RuntimeExecutionException}을 발생시킨다.
	 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * 
	 * @throws RuntimeInterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 * @throws RuntimeTimeoutException 대기 제한 시각을 경과한 경우.
	 * @throws RuntimeExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
	 */
	public void run() throws RuntimeInterruptedException, RuntimeTimeoutException, RuntimeExecutionException {
		m_guard.lock();
		try {
			awaitPreconditionInGuard();
			
			try {
				m_task.run();
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
				throw new RuntimeExecutionException(cause);
			}

			m_guard.signalAllInGuard();
		}
		finally {
			m_guard.unlock();
		}
	}
}
