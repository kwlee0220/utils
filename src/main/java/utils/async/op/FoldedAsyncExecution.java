package utils.async.op;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.StartableExecution;
import utils.func.Result;
import utils.stream.FStream;



/**
 * <code>FoldedAsyncExecution</code>은 복수개의 {@link StartableExecution}을 지정된
 * 순서대로 수행시키면서 그 결과들을 누적(fold)하는 비동기 수행 클래스를 정의한다.
 * <p>
 * 소속 비동기 수행을 스트림 순서대로 하나씩 차례로 수행시키고, 각 수행이 성공하면 그 결과를
 * 누적 함수로 누적 값에 반영한다. 전체 수행의 결과는 마지막 누적 값이다(소속 수행이 없으면 초기 값).
 * 소속 비동기 수행 수행 중 오류 또는 취소가 발생되면 전체 연산이 오류가 발생되거나 취소된 것으로
 * 간주한다.
 *
 * @param <T>	누적 값(곧 전체 수행 결과)의 타입.
 * @param <S>	소속 비동기 수행의 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class FoldedAsyncExecution<T,S> extends AbstractAsyncExecution<T>
										implements CancellableWork {
	private final FStream<StartableExecution<S>> m_sequence;
	private final Supplier<? extends T> m_initSupplier;
	private final BiFunction<? super T,? super S,? extends T> m_folder;
	private T m_accum;
	
	@Nullable @GuardedBy("m_aopGuard") private StartableExecution<? extends S> m_cursor = null;
	
	/**
	 * 순차 수행하며 결과를 누적하는 비동기 수행 객체를 생성한다.
	 * <p>
	 * 반환된 객체는 {@link #start()}를 호출해야 시작된다. 시작 시 {@code initSupplier}로 초기 누적 값을 만들고,
	 * 소속 수행이 성공할 때마다 {@code folder(누적 값, 결과)}로 누적 값을 갱신한다.
	 * <p>
	 * <b>주의:</b> {@code initSupplier}가 {@link #start()}에서 예외를 던지면 그 예외가 호출자에게 전파되고,
	 * 수행 상태는 {@link utils.async.AsyncState#STARTING}에 머문다.
	 *
	 * @param <T>	누적 값의 타입.
	 * @param <S>	소속 비동기 수행의 결과 타입.
	 * @param sequence	순서대로 수행할 비동기 수행 객체 스트림.
	 * @param initSupplier	초기 누적 값을 만드는 함수.
	 * @param folder	누적 값과 소속 수행 결과로 새 누적 값을 만드는 함수.
	 * @return	시작되지 않은 비동기 수행 객체.
	 * @throws IllegalArgumentException	인자 중 하나라도 {@code null}인 경우.
	 */
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
		Preconditions.checkNotNullArgument(execSeq, "AsyncExecution sequnece is null");
		Preconditions.checkNotNullArgument(initSupplier, "Initial Supplier is null");
		Preconditions.checkNotNullArgument(folder, "folder function is null");
		
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
		runInAsyncExecutionGuard(() -> onFinishedInGuard(Result.success(null)));
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
	
	private void onFinishedInGuard(Result<? extends S> result) {
		if ( result.isSuccessful() ) {
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
							next.whenFinishedAsync(this::onFinishedInGuard);
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