package utils.async.op;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.slf4j.LoggerFactory;

import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.AsyncResult;
import utils.async.StartableExecution;
import utils.stream.FStream;



/**
 * <code>FoldedAsyncExecution</code>은 복수개의 {@link StartableExecution}을 지정된
 * 순서대로 수행시키는 비동기 수행 클래스를 정의한다.
 * <p>
 * 순차 비동기 수행은 소속 비동기 수행을 등록된 순서대로 차례대로 수행시킨다. 전체 순차 비동기 수행의
 * 수행 결과는 마지막 원소 비동기 수행의 결과로 정의된다.
 * 소속 비동기 수행 수행 중 오류 또는 취소가 발생되면 전체 순차 연산이 오류가 발생되거나 취소된 것으로
 * 간주한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FoldedAsyncExecution<T,S> extends AbstractAsyncExecution<T>
										implements CancellableWork {
	private final FStream<StartableExecution<S>> m_sequence;
	private final Supplier<? extends T> m_initSupplier;
	private final BiFunction<? super T,? super S,? extends T> m_folder;
	private T m_accum;
	
	@Nullable @GuardedBy("m_aopGuard") private StartableExecution<? extends S> m_cursor = null;
	
	public static <T,S> FoldedAsyncExecution<T,S> of(FStream<StartableExecution<S>> sequence,
													Supplier<? extends T> initSupplier,
													BiFunction<? super T,? super S,? extends T> folder) {
		return new FoldedAsyncExecution<>(sequence, initSupplier, folder);
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
	FoldedAsyncExecution(FStream<StartableExecution<S>> execSeq,
							Supplier<? extends T> initSupplier,
							BiFunction<? super T,? super S,? extends T> folder) {
		Objects.requireNonNull(execSeq, "AsyncExecution sequnece");
		Objects.requireNonNull(initSupplier, "Initial Supplier");
		Objects.requireNonNull(folder, "folder");
		
		m_sequence = execSeq;
		m_initSupplier = initSupplier;
		m_folder = folder;
		
		setLogger(LoggerFactory.getLogger(FoldedAsyncExecution.class));
	}

	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}
		
		m_accum = m_initSupplier.get();
		
		// 첫번째 element를 시작시키기 위해 가상의 이전 element AsyncExecution이 종료된 효과를 발생시킨다.
		runInAsyncExecutionGuard(() -> onFinishedInGuard(AsyncResult.completed(null)));
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
		return String.format("FoldExecution[current=%s]", m_cursor);
	}
	
	private void onFinishedInGuard(AsyncResult<? extends S> result) {
		if ( result.isCompleted() ) {
			// m_sequence가 empty인 경우 notifyStarting() 만 호출된 상태이기 때문에
			// 먼저강제로 notifyStarted()를 호출해준다.
			if ( m_cursor == null ) {
				notifyStarted();
			}
			else {
				m_accum = m_folder.apply(m_accum, result.getOrNull());
			}
			
			m_sequence.next()
					.ifPresent(next -> {
						// Element Execution이 종료되는 순간 cancel()이 호출되는
						// 경우도 있기 때문에, cancel이 요청되었는가 확인할 필요가 있음.
						if ( isCancelRequested() ) {
							notifyCancelled();
						}
						else {
							m_cursor = next;
							next.whenFinished(this::onFinishedInGuard);
							next.start();
						}
					})
					.ifAbsent(() -> {
						m_cursor = null;
						
						if ( !notifyCompleted(m_accum) ) {
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