package utils.async;

import java.io.Serializable;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;

import utils.RuntimeExecutionException;
import utils.Throwables;
import utils.func.CheckedFunction;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardedFunction<T,R> extends GuardedAction<GuardedFunction<T,R>> implements Serializable, CheckedFunction<T,R> {
	private static final long serialVersionUID = 1L;
	
	private final CheckedFunction<T,R> m_func;
	
	private GuardedFunction(Guard guard, CheckedFunction<T,R> func) {
		super(guard);
		Preconditions.checkArgument(func != null, "Function is null");
		
		m_func = func;
	}
	
	/**
	 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
	 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
	 * <p>
	 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
	 * {@link RuntimeExecutionException}을 발생시킨다.
	 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * 
	 * @param input	작업에 입력되는 값.
	 * @return 작업의 결과 값.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
	 * @throws RuntimeExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
	 */
	@Override
	public R apply(T input) throws InterruptedException, TimeoutException, RuntimeExecutionException {
		m_guard.lock();
		try {
			awaitPreconditionInGuard();
			
			try {
				R result = m_func.apply(input);
				m_guard.signalAllInGuard();
				
				return result;
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
				throw new RuntimeExecutionException(cause);
			}
		}
		finally {
			m_guard.unlock();
		}
	}
}
