package utils.async;

import static utils.Preconditions.checkState;

import java.util.concurrent.CancellationException;

import org.jetbrains.annotations.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import utils.Throwables;

/**
 * Guava {@link ListenableFuture}로 수행되는 작업을 {@link Execution}으로 연결하는 추상 클래스.
 * <p>
 * 하위 클래스는 {@link #startExecution()}에서 작업을 시작하고 그 future를 반환한다.
 * future가 성공하면 완료, {@link CancellationException}으로 끝나면 취소, 그 밖의 예외로 끝나면 실패 상태로
 * 전이된다. {@link #cancelWork()}는 future를 {@code cancel(true)}로 취소한다.
 *
 * @param <T>	연산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ListenableFutureAsyncExecution<T> extends EventDrivenExecution<T>
														implements StartableExecution<T>, CancellableWork {
	private volatile ListenableFuture<? extends T> m_future;
	
	/**
	 * 하위 클래스를 위한 기본 생성자.
	 */
	public ListenableFutureAsyncExecution() { }

	/**
	 * 작업을 시작시키고, 그 결과를 나타내는 {@link ListenableFuture}를 반환한다.
	 * <p>
	 * {@link #start()}에서 상태가 {@link AsyncState#STARTING}으로 바뀐 뒤 호출된다.
	 * 반환된 future의 결과에 따라 이 execution이 완료·실패·취소 상태로 전이된다.
	 * 예외를 던지면 이 execution은 그 예외(감싼 예외는 벗겨냄)를 원인으로 실패한다.
	 *
	 * @return	시작된 작업의 future.
	 */
	protected abstract ListenableFuture<? extends T> startExecution();

	@Override
	public void start() {
		if ( notifyStarting() ) {
			try {
				m_future = startExecution();
				Futures.addCallback(m_future, m_callback, Executions.getExecutor());
				
				boolean done = notifyStarted();
				checkState(done);
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}

	@Override
	public boolean cancelWork() {
		if ( m_future != null ) {
			return m_future.cancel(true);
		}
		else {
			return true;
		}
	}

	private final FutureCallback<T> m_callback = new FutureCallback<T>() {
		@Override
		public void onSuccess(@Nullable T result) {
			notifyCompleted(result);
		}

		@Override
		public void onFailure(Throwable ex) {
			Throwable cause = Throwables.unwrapThrowable(ex);
			if ( cause instanceof CancellationException ) {
				notifyCancelled();
			}
			else {
				notifyFailed(cause);
			}
		}
	};
}