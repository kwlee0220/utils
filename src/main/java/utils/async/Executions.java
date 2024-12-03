package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

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
	
	public static CompletableFutureAsyncExecution<Void> runAsync(CheckedRunnable task) {
		return runAsync(task, null);
	}
	public static CompletableFutureAsyncExecution<Void> runAsync(CheckedRunnable task, Executor exector) {
		CompletableFutureAsyncExecution<Void> exec = new CompletableFutureAsyncExecution<Void>() {
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
		exec.start();
		return exec;
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
