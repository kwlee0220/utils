package utils.async.op;

import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.LoggerFactory;

import net.jcip.annotations.GuardedBy;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.Result;
import utils.async.StartableExecution;
import utils.stream.FStream;



/**
 * <code>SequentialAsyncExecution</code>은 복수개의 {@link StartableExecution}을 지정된
 * 순서대로 수행시키는 비동기 수행 클래스를 정의한다.
 * <p>
 * 순차 비동기 수행은 소속 비동기 수행을 등록된 순서대로 차례대로 수행시킨다. 전체 순차 비동기 수행의
 * 수행 결과는 마지막 원소 비동기 수행의 결과로 정의된다.
 * 소속 비동기 수행 수행 중 오류 또는 취소가 발생되면 전체 순차 연산이 오류가 발생되거나 취소된 것으로
 * 간주한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SequentialAsyncExecution<T> extends AbstractAsyncExecution<T>
										implements CancellableWork {
	private final FStream<StartableExecution<?>> m_sequence;
	
	@Nullable @GuardedBy("m_aopGuard") private StartableExecution<?> m_cursor = null;
	@GuardedBy("m_aopGuard") private int m_index = -1;
	
	public static <T> SequentialAsyncExecution<T> of(FStream<StartableExecution<?>> sequence) {
		return new SequentialAsyncExecution<>(sequence);
	}
	
	/**
	 * 순차 수행 비동기 수행 객체를 생성한다.
	 * <p>
	 * 수행되는 순서는 {@link elements} 배열의 순서로 정해진다.
	 * 
	 * @param elements	순차 수행될 비동기 수행 객체 배열.
	 * @throws IllegalArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	SequentialAsyncExecution(FStream<StartableExecution<?>> execSeq) {
		Objects.requireNonNull(execSeq, "AsyncExecution sequnece");
		
		m_sequence = execSeq;
		
		setLogger(LoggerFactory.getLogger(SequentialAsyncExecution.class));
	}
	
	public StartableExecution<?> getCurrentExecution() {
		return getInAsyncExecutionGuard(() -> m_cursor);
	}
	
	public int getCurrentExecutionIndex() {
		return getInAsyncExecutionGuard(() -> m_index);
	}

	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}
		
		// 첫번째 element를 시작시키기 위해 가상의 이전 element AsyncExecution이 종료된 효과를 발생시킨다.
		onFinishedInGuard(Result.completed(null));
	}

	@Override
	public boolean cancelWork() {
		return getInAsyncExecutionGuard(() -> {
			if ( m_cursor != null ) {
				m_cursor.cancel(true);
			}
			return true;
		});
	}
	
	@Override
	public String toString() {
		return getInAsyncExecutionGuard(() ->
					String.format("Sequential[index=%d, current=%s]", m_index, m_cursor));
	}
	
	@SuppressWarnings("unchecked")
	private void onFinishedInGuard(Result<?> result) {
		if ( result.isCompleted() ) {
			// m_sequence가 empty인 경우 notifyStarting() 만 호출된 상태이기 때문에
			// 먼저강제로 notifyStarted()를 호출해준다.
			if ( m_index == -1 ) {
				notifyStarted();
			}
			
			m_sequence.next()
					.ifPresent(next -> {
						// Element Execution이 종료되는 순간 cancel()이 호출되는
						// 경우도 있기 때문에, cancel이 요청되었는가 확인할 필요가 있음.
						if ( isCancelRequested() ) {
							notifyCancelled();
						}
						else if ( isRunning() ) {
							m_cursor = next;
							++m_index;
							next.whenFinished(this::onFinishedInGuard);
							next.start();
						}
					})
					.ifAbsent(() -> {
						m_cursor = null;
						
						if ( !notifyCompleted((T)result.getOrNull()) ) {
							// 취소 요청을 했던 소속 비동기 수행이 완료될 수도 있기 때문에
							// 완료 통보가 도착해도 취소 중인지를 확인하여야 한다.
							//
							notifyCancelled();
						}
					});
		}
		else if ( result.isCancelled() ) {
			notifyCancelled();
		}
		else if ( result.isFailed() ) {
			notifyFailed(result.getCause());
		}
		else {
			throw new AssertionError();
		}
	}
}