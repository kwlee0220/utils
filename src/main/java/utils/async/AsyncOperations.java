package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncOperations {
	public static final CompletableFuture<Void> run(final Runnable task,
													final Executor exector) {
		CompletableFuture<Void> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				task.run();
				promise.complete(null);
			}
			catch ( Exception e ) {
				promise.completeExceptionally(e);
			}
		}, exector);
		return promise;
	}
	
	public static final CompletableFuture<Void> run(final Runnable task) {
		return run(task, ForkJoinPool.commonPool());
	}
	
	public static final <P> Progress<P,Void> run(final ProgressiveRunnable<P> task,
													final Executor exector) {
		final Progress<P,Void> aop = new Progress<P,Void>(task.getInitialValue());
		CompletableFuture.runAsync(() -> {
			try {
				task.setProgressListener(aop.progressListener());
				task.run();
				aop.complete(null);
			}
			catch ( Exception e ) {
				aop.completeExceptionally(e);
			}
		}, exector);
		return aop;
	}
	
	public static final <P> Progress<P,Void> run(final ProgressiveRunnable<P> task) {
		return run(task, ForkJoinPool.commonPool());
	}
	
	public static final <T> CompletableFuture<T> run(final Supplier<T> task,
														final Executor exector) {
		CompletableFuture<T> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				promise.complete(task.get());
			}
			catch ( Exception e ) {
				promise.completeExceptionally(e);
			}
		}, exector);
		return promise;
	}
	
	public static final <T> CompletableFuture<T> run(final Supplier<T> task) {
		return run(task, ForkJoinPool.commonPool());
	}
	
	public static final <P,T> Progress<P,T> run(final ProgressiveSupplier<P,T> task,
												final Executor exector) {
		final Progress<P,T> aop = new Progress<>(task.getInitialValue());
		CompletableFuture.runAsync(() -> {
			try {
				task.setProgressListener(aop.progressListener());
				aop.complete(task.get());
			}
			catch ( Exception e ) {
				aop.completeExceptionally(e);
			}
		}, exector);
		return aop;
	}
	
	public static final <P,T> Progress<P,T> run(final ProgressiveSupplier<P,T> task) {
		return run(task, ForkJoinPool.commonPool());
	}

	public static final <T> CompletableFuture<Void> run(final Consumer<T> task,
														final T data,
														final Executor exector) {
		CompletableFuture<Void> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				task.accept(data);
				promise.complete(null);
			}
			catch ( Exception e ) {
				promise.completeExceptionally(e);
			}
		}, exector);
		return promise;
	}
	
	public static final <T> CompletableFuture<Void> run(final Consumer<T> task,
														final T data) {
		return run(task, data, ForkJoinPool.commonPool());
	}
	
	public static final <P,T> Progress<P,Void> run(final ProgressiveConsumer<P,T> task,
													final T data,
													final Executor exector) {
		final Progress<P,Void> aop = new Progress<P,Void>(task.getInitialValue());
		CompletableFuture.runAsync(() -> {
			try {
				task.setProgressListener(aop.progressListener());
				task.accept(data);
				aop.complete(null);
			}
			catch ( Exception e ) {
				aop.completeExceptionally(e);
			}
		}, exector);
		return aop;
	}
	
	public static final <P,T> Progress<P,Void> run(final ProgressiveConsumer<P,T> task,
													final T data) {
		return run(task, data, ForkJoinPool.commonPool());
	}
	
	public static final <P,I,O> CompletableFuture<O> run(final Function<I,O> task,
														final I data,
														final Executor exector) {
		CompletableFuture<O> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				promise.complete(task.apply(data));
			}
			catch ( Exception e ) {
				promise.completeExceptionally(e);
			}
		}, exector);
		return promise;
	}
	
	public static final <P,I,O> CompletableFuture<O> run(final Function<I,O> task,
															final I data) {
		return run(task, data, ForkJoinPool.commonPool());
	}
	
	public static final <P,I,O> CompletableFuture<O> run(final ProgressiveFunction<P,I,O> task,
														final I data,
														final Executor exector) {
		final Progress<P,O> progress = new Progress<>(task.getInitialValue());
		CompletableFuture.runAsync(() -> {
			try {
				task.setProgressListener(progress.progressListener());
				progress.complete(task.apply(data));
			}
			catch ( Exception e ) {
				progress.completeExceptionally(e);
			}
		}, exector);
		return progress;
	}
	
	public static final <P,I,O> CompletableFuture<O> run(final ProgressiveFunction<P,I,O> task,
															final I data) {
		return run(task, data, ForkJoinPool.commonPool());
	}
}
