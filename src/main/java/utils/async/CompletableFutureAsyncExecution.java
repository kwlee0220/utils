package utils.async;


import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import utils.Preconditions;
import utils.Throwables;
import utils.func.CheckedSupplier;


/**
 * {@link CompletableFuture}를 {@link Execution}으로 어댑팅하는 추상 클래스이다.
 * <p>
 * 서브클래스는 {@link #startExecution()}을 구현하여 실제 비동기 작업을 시작하고 그 결과를
 * 담을 {@code CompletableFuture}를 반환해야 한다. 본 클래스는 반환된 future의
 * 성공/실패/취소를 상위 {@link EventDrivenExecution}의 상태 머신
 * ({@code notifyCompleted}/{@code notifyFailed}/{@code notifyCancelled})으로 중계한다.
 * <p>
 * {@link #start()}는 다음 순서로 동작한다:
 * <ol>
 *   <li>{@code notifyStarting()}으로 {@code STARTING} 상태로 전이.</li>
 *   <li>{@link #startExecution()}을 호출하여 {@code CompletableFuture}를 생성.</li>
 *   <li>{@code notifyStarted()}으로 {@code RUNNING} 상태로 전이.</li>
 *   <li>future에 완료 콜백({@link CompletableFuture#whenCompleteAsync})을 등록.</li>
 * </ol>
 * 콜백 등록을 {@code RUNNING} 전이 이후에 수행하므로, future가 이미 완료된 상태이거나
 * 콜백이 즉시 발화하더라도 {@code notifyCompleted}/{@code notifyFailed}/{@code notifyCancelled}가
 * {@code STARTING} 상태에서 호출되지 않는다.
 * <p>
 * 위 과정 중 예외가 발생하면 (이미 생성된) {@code CompletableFuture}를 best-effort로
 * 취소한 뒤 {@code notifyFailed}로 실패를 전파한다.
 * <p>
 * 본 클래스는 {@link CancellableWork}를 구현하므로 {@code cancel(true)} 호출 시
 * 내부 {@code CompletableFuture}의 {@code cancel(true)}을 통해 작업을 취소한다.
 * {@code CancellationException} 형태로 종료되면 {@code CANCELLED} 상태로 전이된다.
 * {@link #cancelWork()}는 STARTING 단계에서 cancel이 도착해 {@code m_future}가 아직
 * 설정되지 않은 경우 잠시 대기 후 취소한다.
 *
 * @param <T> 비동기 작업의 결과 타입
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class CompletableFutureAsyncExecution<T> extends EventDrivenExecution<T>
											implements StartableExecution<T>, CancellableWork {
	private static final Duration CANCEL_WAIT_TIMEOUT = Duration.ofSeconds(3);

	private volatile CompletableFuture<? extends T> m_future;

	/**
	 * 비동기 작업을 시작하고 그 결과를 담을 {@link CompletableFuture}를 반환한다.
	 * <p>
	 * 본 메소드는 {@link #start()} 진행 중 {@code STARTING} 상태에서 호출된다.
	 *
	 * @return 작업 결과를 담는 {@code CompletableFuture}. {@code null}이면 안 된다.
	 */
	protected abstract CompletableFuture<? extends T> startExecution();

	@Override
	public void start() {
		if ( notifyStarting() ) {
			try {
				m_future = startExecution();
				if ( !notifyStarted() ) {
					// STARTING인 상태에서 cancel되거나 notifyFailed()로 실패한 경우
					// 여기서는 exception을 발생시키고, catch 블록에서 future를 취소한 뒤
					// notifyFailed()로 실패를 전파한다.
					throw new IllegalStateException("execution is not started");
				}

				m_future.whenCompleteAsync((ret,ex) -> {
					if ( ex == null ) {
						notifyCompleted(ret);
						return;
					}
					
					if ( ex instanceof CompletionException ) {
						ex = ex.getCause();
					}
					
					if ( ex instanceof CancellationException || ex instanceof InterruptedException ) {
						notifyCancelled();
					}
					else if ( ex instanceof TimeoutException ) {
						notifyFailed(ex);
					}
					else {
						Throwable cause = Throwables.unwrapThrowable(ex);
						notifyFailed(cause);
					}
				});
			}
			catch ( Throwable e ) {
				var future = m_future;
				if ( future != null ) {
					future.cancel(true);
				}
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
	}

	@Override
	public boolean cancelWork() {
		var future = m_future;
		if ( future == null ) {
			try {
				boolean ready = m_aopGuard.awaitCondition(() -> m_future != null,
															CANCEL_WAIT_TIMEOUT)
											.andReturn();
				if ( !ready ) {
					return false;
				}
				future = m_future;
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return future.cancel(true);
	}
	
	@Override
	public String toString() {
		var future = m_future;
		return (future == null) ? "not-started" : future.toString();
	}
	
	public static <T> CompletableFutureAsyncExecution<T> of(CompletableFuture<T> future) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return future;
			}
		};
	}
	
	public static CompletableFutureAsyncExecution<Void> runAsync(Runnable runnable) {
		return new CompletableFutureAsyncExecution<>() {
			@Override
			protected CompletableFuture<Void> startExecution() {
				return CompletableFuture.runAsync(runnable);
			}
		};
	}

	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<T> supplier) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return CompletableFuture.supplyAsync(supplier);
			}
		};
	}

	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(CheckedSupplier<T> supplier) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return CompletableFuture.supplyAsync(supplier.toSneakyThrowSupplier());
			}
		};
	}

	public static Executor getDelayedExecutor(long delay, TimeUnit unit) {
		Preconditions.checkArgument(delay >= 0, "delay must be non-negative: %d", delay);
		Preconditions.checkNotNull(unit, "unit must not be null");

		return CompletableFuture.delayedExecutor(delay, unit);
	}
}