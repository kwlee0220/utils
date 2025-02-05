package utils.async;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;
import utils.func.Result;
import utils.func.UncheckedRunnable;
import utils.func.UncheckedSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Executions {
//	private static final ScheduledExecutorService EXECUTOR
//					= Lazy.wrap(Executions::createDefaultExecutor, ScheduledExecutorService.class);
	private static final ScheduledExecutorService EXECUTOR = Executions.createDefaultExecutor();
	
	private static Timer s_timer = null;
	
	private Executions() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}
	
	public static Timer getTimer() {
		if ( s_timer == null ) {
			s_timer = new Timer();
		}
		return s_timer;
	}
	
	public static ScheduledExecutorService getExecutor() {
		return EXECUTOR;
	}
	
	/**
	 * 주어진 {@link CheckedRunnable} 작업을 비동기로 실행하는 {@link CompletableFuture}를 생성한다.
	 *
	 * @param task	비동기로 실행할 작업
	 * @param exector	작업을 실행할 {@link Executor} 객체. {@code null}인 경우는 쓰레드를 새로 생성한다.
	 * @return	{@link StartableExecution} 객체.
	 */
	public static StartableExecution<Void> toExecution(CheckedRunnable task, Executor exector) {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				if ( exector != null ) {
					return CompletableFuture.runAsync(UncheckedRunnable.sneakyThrow(task), exector);
				}
				else {
					return CompletableFuture.runAsync(UncheckedRunnable.sneakyThrow(task));
				}
			}
		};
	}
	
	/**
	 * 주어진 {@link CheckedRunnable} 작업을 비동기로 실행하는 {@link CompletableFuture}를 생성한다.
	 *
	 * @param task 비동기로 실행할 작업
	 * @return {@link StartableExecution} 객체.
	 */
	public static StartableExecution<Void> toExecution(CheckedRunnable task) {
		return toExecution(task, null);
	}
	
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier) {
		return supplyAsync(supplier, null);
	}
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier,
																		@Nullable Executor exector) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return (exector != null) ? CompletableFuture.supplyAsync(supplier, exector)
											:  CompletableFuture.supplyAsync(supplier);
			}
		};
	}
	public static <T> CompletableFutureAsyncExecution<T> supplyCheckedAsync(CheckedSupplier<? extends T> supplier) {
		return supplyAsync(UncheckedSupplier.sneakyThrow(supplier));
	}
	public static <T> CompletableFutureAsyncExecution<T> supplyCheckedAsync(CheckedSupplier<? extends T> supplier,
																			@Nullable Executor exector) {
		return supplyAsync(UncheckedSupplier.sneakyThrow(supplier), exector);
	}
	
	/**
	 * 주어진 {@code Callable} 작업을 비동기로 실행하는 {@link CompletableFuture}를 생성한다.
	 * 
	 * @param task     비동기로 실행할 작업
	 * @param executor 작업을 실행할 {@link Executor} 객체. {@code null}인 경우는 기본
	 *                 {@link Executor}를 사용한다.
	 * @return {@link CompletableFuture} 객체.
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
	public static <T> CompletableFuture<T> callAsync(Callable<? extends T> task) {
		return callAsync(task, null);
	}
	
	static class FlatMapCompleteChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapCompleteChainExecution(EventDrivenExecution<? extends T> leader,
									Function<? super T,Execution<? extends S>> chain) {
			leader.whenStartedAsync(this::notifyStarted);
			leader.whenFinishedAsync(ret ->
				ret.ifSuccessful(v -> {
						Execution<S> follower = Execution.narrow(chain.apply(v));
						follower.whenStartedAsync(this::notifyStarted)
								.whenFinishedAsync(ret2 -> ret2.ifSuccessful(this::notifyCompleted)
															.ifFailed(this::notifyFailed)
															.ifNone(this::notifyCancelled));
						if ( !follower.isStarted() && follower instanceof StartableExecution ) {
							((StartableExecution<S>)follower).start();
						}
					})
					.ifFailed(this::notifyFailed)
					.ifNone(this::notifyCancelled)
			);
		}
	}
	
	static class FlatMapChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapChainExecution(Execution<T> leader,
							Function<? super Result<T>, ? extends Execution<S>> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret -> {
				Execution<S> follower = chain.apply(ret);
				follower.whenStarted(this::notifyStarted)
						.whenFinished(ret2 -> ret2.ifSuccessful(this::notifyCompleted)
													.ifFailed(this::notifyFailed)
													.ifNone(this::notifyCancelled));
				if ( !follower.isStarted() ) {
					if ( follower instanceof StartableExecution ) {
						((StartableExecution<S>)follower).start();
					}
					else {
						throw new IllegalStateException(
										String.format("follower has not been started: %s", follower));
					}
				}
			});
		}
	}
	
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
	
	private static ScheduledExecutorService createDefaultExecutor() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int numberOfThreads = Math.max(availableCores - 1, 1); // Adjust as needed

        return Executors.newScheduledThreadPool(numberOfThreads);
	}
}
