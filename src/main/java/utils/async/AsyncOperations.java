package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncOperations {
	public static final AsyncOperation<Void> run(final Runnable task, final Executor exector) {
		final AsyncOperation<Void> aop = new AsyncOperation<>();
		CompletableFuture.runAsync(() -> {
			try {
				if ( task instanceof ProgressReporter ) {
					((ProgressReporter)task).setProgressListener(aop.progressListener());
				}
				
				task.run();
				aop.complete(null);
			}
			catch ( Exception e ) {
				aop.completeExceptionally(e);
			}
		}, exector);
		return aop;
	}
	
	public static final AsyncOperation<Void> run(final Runnable task) {
		return run(task, ForkJoinPool.commonPool());
	}
	
	public static final <T> AsyncOperation<T> run(final Supplier<T> task, final Executor exector) {
		final AsyncOperation<T> aop = new AsyncOperation<>();
		CompletableFuture.runAsync(() -> {
			try {
				if ( task instanceof ProgressReporter ) {
					((ProgressReporter)task).setProgressListener(aop.progressListener());
				}
				aop.complete(task.get());
			}
			catch ( Exception e ) {
				aop.completeExceptionally(e);
			}
		}, exector);
		return aop;
	}
	
	public static final <T> AsyncOperation<T> run(final Supplier<T> task) {
		return run(task, ForkJoinPool.commonPool());
	}
}
