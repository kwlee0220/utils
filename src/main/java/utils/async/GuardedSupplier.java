package utils.async;

import java.io.Serializable;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;

import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;
import utils.RuntimeTimeoutException;
import utils.Throwables;
import utils.func.CheckedSupplier;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardedSupplier<T> extends GuardedAction<GuardedSupplier<T>> implements Serializable, CheckedSupplier<T> {
	private static final long serialVersionUID = 1L;
	
	private final CheckedSupplier<T> m_supplier;
	
	private GuardedSupplier(Guard guard, CheckedSupplier<T> supplier) {
		super(guard);
		Preconditions.checkArgument(supplier != null, "Supplier is null");
		
        m_supplier = supplier;
	}
	
	/**
	 * 주어진 {@link Guard}와 {@link CheckedSupplier}로 {@link GuardedSupplier} 객체를 생성한다.
	 * 
	 * @param guard    {@link Guard} 객체.
	 * @param supplier {@link CheckedSupplier} 객체.
	 * @return {@link GuardedSupplier} 객체.
	 */
	public static <T> GuardedSupplier<T> from(Guard guard, CheckedSupplier<T> supplier) {
		return new GuardedSupplier<>(guard, supplier);
	}
	
	/**
	 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
	 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
	 * <p>
	 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
	 * {@link RuntimeExecutionException}을 발생시킨다.
	 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * 
	 * @return 작업의 결과 값.
	 * @throws RuntimeInterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
	 * @throws RuntimeTimeoutException 		대기 제한 시각을 경과한 경우.
	 * @throws RuntimeExecutionException	작업 ({@code work}) 수행 중 예외가 발생한 경우.
	 */
	@Override
	public T get() throws RuntimeInterruptedException, RuntimeTimeoutException, RuntimeExecutionException {
		m_guard.lock();
		try {
			awaitPreconditionInGuard();
			
			try {
				T result = m_supplier.get();
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
