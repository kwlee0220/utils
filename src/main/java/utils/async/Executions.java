package utils.async;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;
import utils.func.UncheckedRunnable;
import utils.func.UncheckedSupplier;
import utils.thread.Timer;


/**
 * 비동기 실행({@link Execution} / {@link CompletableFuture})을 생성하기 위한 정적 팩토리 모음.
 * <p>
 * 작업 형태에 따라 다음 메소드를 제공한다.
 * <ul>
 *   <li>{@link CheckedRunnable} → {@link StartableExecution}: {@link #toExecution(CheckedRunnable)},
 *       {@link #toExecution(CheckedRunnable, Executor)}</li>
 *   <li>{@link Supplier} → {@link CompletableFutureAsyncExecution}: {@link #supplyAsync(Supplier)},
 *       {@link #supplyAsync(Supplier, Executor)}</li>
 *   <li>{@link CheckedSupplier} → {@link CompletableFutureAsyncExecution}: {@link #supplyCheckedAsync(CheckedSupplier)},
 *       {@link #supplyCheckedAsync(CheckedSupplier, Executor)}</li>
 *   <li>{@link Callable} → {@link CompletableFuture}(raw): {@link #callAsync(Callable)},
 *       {@link #callAsync(Callable, Executor)}</li>
 * </ul>
 * 또한 라이브러리 공용으로 사용되는 {@link ScheduledExecutorService}와 {@link Timer} 인스턴스를
 * {@link #getExecutor()}, {@link #getTimer()}로 노출한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Executions {
	private static final ScheduledExecutorService EXECUTOR = Executions.createDefaultExecutor();
	
	private static Timer s_timer = null;
	
	private Executions() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}
	
	/**
	 * 라이브러리 공용 {@link Timer} 인스턴스를 반환한다.
	 * <p>
	 * 주로 {@link EventDrivenExecution#setTimeout(long, java.util.concurrent.TimeUnit)} 등
	 * {@link Execution}에 타임아웃을 부착하는 데 사용된다. 최초 호출 시 lazy 생성된다.
	 *
	 * @return 공용 {@link Timer} 객체.
	 */
	public static Timer getTimer() {
		if ( s_timer == null ) {
			s_timer = new Timer();
		}
		return s_timer;
	}

	/**
	 * 라이브러리 공용 {@link ScheduledExecutorService} 인스턴스를 반환한다.
	 * <p>
	 * 사용 가능한 CPU 코어 수보다 하나 작은 (최소 1) 크기의 스케줄링 스레드 풀이며,
	 * 비동기 실행/콜백/타이머 디스패치 등에 공용으로 사용된다.
	 *
	 * @return 공용 {@link ScheduledExecutorService} 객체.
	 */
	public static ScheduledExecutorService getExecutor() {
		return EXECUTOR;
	}
	
	/**
	 * 주어진 {@link CheckedRunnable} 작업을 비동기로 실행하는 {@link StartableExecution}을 생성한다.
	 * <p>
	 * 반환된 객체는 명시적인 {@link StartableExecution#start()} 호출 전에는 작업이 시작되지 않는다.
	 * 내부적으로 {@link CompletableFuture#runAsync(Runnable, Executor)}을 사용하며,
	 * 작업 도중 발생한 checked exception은 {@link UncheckedRunnable#sneakyThrow}를 통해 전파된다.
	 *
	 * @param task     비동기로 실행할 작업.
	 * @param executor 작업을 실행할 {@link Executor} 객체. {@code null}이면 {@link CompletableFuture}의
	 *                 기본 비동기 풀(공용 {@code ForkJoinPool})을 사용한다.
	 * @return {@link StartableExecution} 객체.
	 */
	public static StartableExecution<Void> toExecution(CheckedRunnable task, Executor executor) {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				if ( executor != null ) {
					return CompletableFuture.runAsync(UncheckedRunnable.sneakyThrow(task), executor);
				}
				else {
					return CompletableFuture.runAsync(UncheckedRunnable.sneakyThrow(task));
				}
			}
		};
	}
	
	/**
	 * 주어진 {@link CheckedRunnable} 작업을 기본 비동기 풀에서 실행하는 {@link StartableExecution}을 생성한다.
	 * <p>
	 * {@link #toExecution(CheckedRunnable, Executor) toExecution(task, null)}과 동일하다.
	 *
	 * @param task 비동기로 실행할 작업.
	 * @return {@link StartableExecution} 객체.
	 */
	public static StartableExecution<Void> toExecution(CheckedRunnable task) {
		return toExecution(task, null);
	}
	
	public static void rethrowAsyncException(Throwable ex) throws InterruptedException, CancellationException,
																TimeoutException, ExecutionException {
		if ( ex instanceof InterruptedException ie ) {
			Thread.currentThread().interrupt();
			throw ie;
		}
		else if ( ex instanceof CancellationException ce ) {
			throw ce;
		}
		else if ( ex instanceof TimeoutException te ) {
			throw te;
		}
		else if ( ex instanceof Error error ) {
			throw error;
		}
		throw new ExecutionException(ex);
	}
	
	/**
	 * 주어진 {@link Supplier} 작업을 기본 비동기 풀에서 실행하는 {@link CompletableFutureAsyncExecution}을 생성한다.
	 * <p>
	 * {@link #supplyAsync(Supplier, Executor) supplyAsync(supplier, null)}과 동일하다.
	 *
	 * @param <T>      작업 결과 타입.
	 * @param supplier 비동기로 실행할 작업.
	 * @return {@link CompletableFutureAsyncExecution} 객체.
	 */
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier) {
		return supplyAsync(supplier, null);
	}
	/**
	 * 주어진 {@link Supplier} 작업을 비동기로 실행하는 {@link CompletableFutureAsyncExecution}을 생성한다.
	 * <p>
	 * 반환된 객체는 명시적인 {@link StartableExecution#start()} 호출 전에는 작업이 시작되지 않는다.
	 * 내부적으로 {@link CompletableFuture#supplyAsync(Supplier, Executor)}을 사용한다.
	 *
	 * @param <T>      작업 결과 타입.
	 * @param supplier 비동기로 실행할 작업.
	 * @param executor 작업을 실행할 {@link Executor} 객체. {@code null}이면 {@link CompletableFuture}의
	 *                 기본 비동기 풀(공용 {@code ForkJoinPool})을 사용한다.
	 * @return {@link CompletableFutureAsyncExecution} 객체.
	 */
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier,
																		@Nullable Executor executor) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return (executor != null) ? CompletableFuture.supplyAsync(supplier, executor)
											:  CompletableFuture.supplyAsync(supplier);
			}
		};
	}
	/**
	 * Checked exception을 던질 수 있는 {@link CheckedSupplier} 작업을 비동기로 실행한다.
	 * <p>
	 * {@link UncheckedSupplier#sneakyThrow}를 통해 checked exception을 그대로 전파한 뒤
	 * {@link #supplyAsync(Supplier)}로 위임한다.
	 *
	 * @param <T>      작업 결과 타입.
	 * @param supplier 비동기로 실행할 작업.
	 * @return {@link CompletableFutureAsyncExecution} 객체.
	 */
	public static <T> CompletableFutureAsyncExecution<T> supplyCheckedAsync(CheckedSupplier<? extends T> supplier) {
		return supplyAsync(supplier.toSneakyThrowSupplier());
	}
	/**
	 * Checked exception을 던질 수 있는 {@link CheckedSupplier} 작업을 주어진 {@link Executor}에서 비동기로 실행한다.
	 * <p>
	 * {@link UncheckedSupplier#sneakyThrow}를 통해 checked exception을 그대로 전파한 뒤
	 * {@link #supplyAsync(Supplier, Executor)}로 위임한다.
	 *
	 * @param <T>      작업 결과 타입.
	 * @param supplier 비동기로 실행할 작업.
	 * @param executor 작업을 실행할 {@link Executor} 객체. {@code null}이면 {@link CompletableFuture}의
	 *                 기본 비동기 풀을 사용한다.
	 * @return {@link CompletableFutureAsyncExecution} 객체.
	 */
	public static <T> CompletableFutureAsyncExecution<T> supplyCheckedAsync(CheckedSupplier<? extends T> supplier,
																			@Nullable Executor executor) {
		return supplyAsync(UncheckedSupplier.sneakyThrow(supplier), executor);
	}
	
	/**
	 * 주어진 {@link Callable} 작업을 비동기로 실행하는 {@link CompletableFuture}를 생성한다.
	 * <p>
	 * {@link #toExecution(CheckedRunnable, Executor)} / {@link #supplyAsync(Supplier, Executor)}와 달리
	 * {@link Execution} 래퍼를 만들지 않고 표준 {@link CompletableFuture}를 직접 반환한다.
	 * {@link Callable#call()}이 던진 checked exception은 {@link CompletionException}으로 감싸 전파되며,
	 * {@link Error}/{@link RuntimeException}은 그대로 전파된다.
	 *
	 * @param <T>      작업 결과 타입.
	 * @param task     비동기로 실행할 작업. {@code null}이면 안 된다.
	 * @param executor 작업을 실행할 {@link Executor} 객체. {@code null}이면 {@link CompletableFuture}의
	 *                 기본 비동기 풀을 사용한다.
	 * @return 작업의 결과를 담은 {@link CompletableFuture} 객체.
	 * @throws IllegalArgumentException {@code task}가 {@code null}인 경우.
	 */
	public static <T> CompletableFuture<T> callAsync(Callable<? extends T> task, @Nullable Executor executor) {
		Preconditions.checkArgument(task != null, "task is null");
		
		Supplier<T> supplier = () -> {
			try {
				return task.call();
			}
			catch ( Error | RuntimeException e ) {
				throw e;
			}
			catch ( Throwable e ) {
				throw new CompletionException(e);
			}
		};
		return (executor != null) ? CompletableFuture.supplyAsync(supplier, executor)
									: CompletableFuture.supplyAsync(supplier);
	}
	/**
	 * 주어진 {@link Callable} 작업을 기본 비동기 풀에서 실행하는 {@link CompletableFuture}를 생성한다.
	 * <p>
	 * {@link #callAsync(Callable, Executor) callAsync(task, null)}과 동일하다.
	 *
	 * @param <T>  작업 결과 타입.
	 * @param task 비동기로 실행할 작업. {@code null}이면 안 된다.
	 * @return 작업의 결과를 담은 {@link CompletableFuture} 객체.
	 * @throws IllegalArgumentException {@code task}가 {@code null}인 경우.
	 */
	public static <T> CompletableFuture<T> callAsync(Callable<? extends T> task) {
		return callAsync(task, null);
	}

	/**
	 * 한 {@link Execution}의 결과에 변환 함수를 연결하는 내부 구현 클래스.
	 * <p>
	 * {@link Execution#map(Function)}의 백엔드로 사용된다. {@code leader}의
	 * 시작/종료 이벤트를 받아 동일한 라이프사이클을 자신에게 그대로 전파하되,
	 * 성공 결과에만 {@code chain} 함수를 적용한다. {@code chain} 적용 중 예외가
	 * 발생하면 그 예외로 실패 처리한다.
	 *
	 * @param <T> 원본 결과 타입.
	 * @param <S> 변환 후 결과 타입.
	 */
	static class MapChainExecution<T,S> extends EventDrivenExecution<S> {
		MapChainExecution(Execution<? extends T> leader,
									Function<? super T,? extends S> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret ->
				ret.ifSuccessful(v -> {
						try {
							notifyCompleted(chain.apply(v));
						}
						catch ( Throwable e ) {
							notifyFailed(e);
						}
					})
					.ifFailed(this::notifyFailed)
					.ifNone(this::notifyCancelled)
		);}
	}
	
	/**
	 * 라이브러리 공용 {@link ScheduledExecutorService}를 생성한다.
	 * <p>
	 * 사용 가능한 CPU 코어 수에서 1을 뺀 값(최소 1)을 풀 크기로 사용한다.
	 *
	 * @return 새로 생성된 {@link ScheduledExecutorService}.
	 */
	private static ScheduledExecutorService createDefaultExecutor() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int numberOfThreads = Math.max(availableCores - 1, 1); // Adjust as needed

        return Executors.newScheduledThreadPool(numberOfThreads);
	}
}
