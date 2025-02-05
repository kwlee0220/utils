package utils.async;

import java.io.Serializable;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;

import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;
import utils.RuntimeTimeoutException;
import utils.Throwables;
import utils.func.CheckedConsumer;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardedConsumer<T> extends GuardedAction<GuardedConsumer<T>> implements Serializable, CheckedConsumer<T> {
	private static final long serialVersionUID = 1L;
	
	private final CheckedConsumer<T> m_consumer;
	
	private GuardedConsumer(Guard guard, CheckedConsumer<T> consumer) {
		super(guard);
		Preconditions.checkArgument(consumer != null, "Consumer is null");
		
		m_consumer = consumer;
	}
	
	/**
	 * 주어진 {@link Guard}와 {@link CheckedConsumer}로 {@link GuardedConsumer} 객체를 생성한다.
	 * 
	 * @param guard    {@link Guard} 객체.
	 * @param supplier {@link CheckedConsumer} 객체.
	 * @return {@link GuardedConsumer} 객체.
	 */
	public static <T> GuardedConsumer<T> from(Guard guard, CheckedConsumer<T> consumer) {
		return new GuardedConsumer<>(guard, consumer);
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
	 * @throws RuntimeInterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 * @throws RuntimeTimeoutException 대기 제한 시각을 경과한 경우.
	 * @throws RuntimeExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
	 */
	@Override
	public void accept(T input) throws RuntimeInterruptedException, RuntimeTimeoutException, RuntimeExecutionException {
		m_guard.lock();
		try {
			awaitPreconditionInGuard();
			
			try {
				m_consumer.accept(input);
				m_guard.signalAllInGuard();
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
