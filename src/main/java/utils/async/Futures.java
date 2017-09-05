package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.vavr.control.Option;
import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Futures {
	public static Option<Cancellable> asCanceller(Object task) {
		return (task instanceof Cancellable)
				? Option.some((Cancellable)task)
				: Option.none();
	}
	
	public static CompletableFuture<Void> runAsync(Runnable task) {
		return new CancellableDelegate<>(asCanceller(task), CompletableFuture.runAsync(task));
	}
	public static CompletableFuture<Void> runAsync(Runnable task, Executor exector) {
		return new CancellableDelegate<>(asCanceller(task), CompletableFuture.runAsync(task, exector));
	}
	
	public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
		return new CancellableDelegate<>(asCanceller(task), CompletableFuture.supplyAsync(task));
	}
	public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task, Executor exector) {
		return new CancellableDelegate<>(asCanceller(task), CompletableFuture.supplyAsync(task, exector));
	}
	
	public static <T> Result<T> getResult(CompletableFuture<T> future)
		throws InterruptedException {
		try {
			return Result.some(future.get());
		}
		catch ( CancellationException e ) {
			return Result.none();
		}
		catch ( ExecutionException e ) {
			return Result.failure(e.getCause());
		}
		catch ( InterruptedException e ) {
			throw e;
		}
		catch ( Exception e ) {
			return Result.failure(e);
		}
	}
	
	public static <T> Result<T> getResult(CompletableFuture<T> future, long timeout, TimeUnit tu)
		throws InterruptedException, TimeoutException {
		try {
			return Result.some(future.get(timeout, tu));
		}
		catch ( CancellationException e ) {
			return Result.none();
		}
		catch ( ExecutionException e ) {
			return Result.failure(e.getCause());
		}
		catch ( InterruptedException | TimeoutException e ) {
			throw e;
		}
		catch ( Exception e ) {
			return Result.failure(e);
		}
	}
	
	public static boolean waitForFinished(CompletableFuture<?> future, long timeout,
											TimeUnit tu) {
		try {
			future.get(timeout, tu);
			return true;
		}
		catch ( InterruptedException | TimeoutException e ) {
			return false;
		}
		catch ( Exception e ) {
			return true;
		}
	}
}
