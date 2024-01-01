package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.func.CheckedRunnable;
import utils.func.Lazy;
import utils.func.UncheckedRunnable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Executions {
	private static final ScheduledExecutorService EXECUTOR
					= Lazy.wrap(Executions::createDefaultExecutor, ScheduledExecutorService.class);
	
	private Executions() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}
	
	public static ScheduledExecutorService getExecutor() {
		return EXECUTOR;
	}
	
	public static CompletableFutureAsyncExecution<Void> runAsync(CheckedRunnable task) {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				UncheckedRunnable urunnable = UncheckedRunnable.sneakyThrow(task);
				return CompletableFuture.runAsync(urunnable);
			}
		};
	}
	public static CompletableFutureAsyncExecution<Void> runAsync(CheckedRunnable task, Executor exector) {
		return new CompletableFutureAsyncExecution<Void>() {
			@Override
			protected CompletableFuture<? extends Void> startExecution() {
				return CompletableFuture.runAsync(UncheckedRunnable.sneakyThrow(task), exector);
			}
		};
	}
	
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return CompletableFuture.supplyAsync(supplier);
			}
		};
	}
	public static <T> CompletableFutureAsyncExecution<T> supplyAsync(Supplier<? extends T> supplier,
																		Executor exector) {
		return new CompletableFutureAsyncExecution<T>() {
			@Override
			protected CompletableFuture<? extends T> startExecution() {
				return CompletableFuture.supplyAsync(supplier, exector);
			}
		};
	}
	
	static class FlatMapCompleteChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapCompleteChainExecution(EventDrivenExecution<? extends T> leader,
									Function<? super T,Execution<? extends S>> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret ->
				ret.ifCompleted(v -> {
						Execution<S> follower = Execution.narrow(chain.apply(v));
						follower.whenStarted(this::notifyStarted)
								.whenFinished(ret2 -> ret2.ifCompleted(this::notifyCompleted)
														.ifFailed(this::notifyFailed)
														.ifCancelled(this::notifyCancelled));
						if ( !follower.isStarted() && follower instanceof StartableExecution ) {
							((StartableExecution<S>)follower).start();
						}
					})
					.ifFailed(this::notifyFailed)
					.ifCancelled(this::notifyCancelled)
			);
		}
	}
	
	static class FlatMapChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapChainExecution(Execution<T> leader,
							Function<AsyncResult<T>, Execution<S>> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret -> {
				Execution<S> follower = chain.apply(ret);
				follower.whenStarted(this::notifyStarted)
						.whenFinished(ret2 -> ret2.ifCompleted(this::notifyCompleted)
													.ifFailed(this::notifyFailed)
													.ifCancelled(this::notifyCancelled));
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
							Function<AsyncResult<? extends T>,? extends S> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret -> {
				try {
					notifyCompleted(chain.apply(ret));
				}
				catch ( Throwable e ) {
					notifyFailed(e);
				}
			});
		}
	}
	
	static class MapCompleteChainExecution<T,S> extends EventDrivenExecution<S> {
		MapCompleteChainExecution(Execution<? extends T> leader,
									Function<? super T,? extends S> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret ->
				ret.ifCompleted(v -> {
						try {
							notifyCompleted(chain.apply(v));
						}
						catch ( Throwable e ) {
							notifyFailed(e);
						}
					})
					.ifFailed(this::notifyFailed)
					.ifCancelled(this::notifyCancelled)
		);}
	}
	
	private static final ScheduledExecutorService createDefaultExecutor() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int numberOfThreads = Math.max(availableCores - 1, 1); // Adjust as needed

        return Executors.newScheduledThreadPool(numberOfThreads);
	}
}
