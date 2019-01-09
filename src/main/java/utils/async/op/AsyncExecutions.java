package utils.async.op;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.async.AbstractAsyncExecution;
import utils.async.AsyncExecution;
import utils.async.FutureBasedAsyncExecution;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncExecutions {
	static final Logger s_logger = LoggerFactory.getLogger(AsyncExecutions.class);
	
	private AsyncExecutions() {
		throw new AssertionError("Should not be called: class=" + AsyncExecutions.class);
	}
	
	public static <T> AsyncExecution<T> idle(T result, long delay, TimeUnit unit,
											ScheduledExecutorService scheduler) {
		return new FutureBasedAsyncExecution<T>() {
			@Override
			protected Future<? extends T> getFuture() {
				return scheduler.schedule(() -> {
					if ( notifyCompleted(result) ) {
						return result;
					}
					if ( notifyCancelled() ) {
						return null;
					}
					return null;
				}, delay, unit);
			}
		};
	}
	
	public static AsyncExecution<Void> idle(long delay, TimeUnit unit,
											ScheduledExecutorService scheduler) {
		return AsyncExecutions.<Void>idle(null, delay, unit, scheduler);
	}
	
	public static <T> AsyncExecution<T> nop(T result) {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				if ( !notifyStarted() ) {
					return;
				}
				if ( notifyCompleted(result) ) {
					return;
				}
				if ( notifyCancelled() ) {
					return;
				}
			}
		};
	}
	
	public static AsyncExecution<Void> nop() {
		return nop(null);
	}
	
	public static <T> AsyncExecution<T> failure(Throwable cause) {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				if ( !notifyStarted() ) {
					return;
				}
				CompletableFuture.runAsync(() -> notifyFailed(cause));
			}
		};
	}
	
	public static <T> AsyncExecution<T> cancelled() {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				notifyStarted();
				CompletableFuture.runAsync(this::notifyCancelled);
			}
		};
	}

	public static <T> SequentialAsyncExecution<T> sequential(
												FStream<AsyncExecution<?>> sequence) {
		return new SequentialAsyncExecution<>(sequence);
	}

	public static <T> SequentialAsyncExecution<T> sequential(
											List<AsyncExecution<T>> elms) {
		return new SequentialAsyncExecution<>(FStream.of(elms));
	}

	@SafeVarargs
	public static <T> SequentialAsyncExecution<T> sequential(
											AsyncExecution<? extends T>... sequence) {
		return new SequentialAsyncExecution<>(FStream.of(sequence));
	}

	public static <T> ConcurrentAsyncExecution concurrent(AsyncExecution<?>... elements) {
		return new ConcurrentAsyncExecution(elements);
	}

	public static <T> BackgroundedAsyncExecution<T> backgrounded(AsyncExecution<T> fg,
																AsyncExecution<?> bg) {
		return new BackgroundedAsyncExecution<>(fg, bg);
	}

	public static <T> TimedAsyncExecution<T> timed(AsyncExecution<T> target, long timeout,
												TimeUnit unit, ScheduledExecutorService scheduler) {
		return new TimedAsyncExecution<>(target, timeout, unit, scheduler);
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<AsyncExecution<T>> seq,
														Supplier<S> initSupplier,
														BiFunction<S,T,S> folder) {
		return new FoldedAsyncExecution<>(seq, initSupplier, folder);
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<AsyncExecution<T>> seq,
														S init, BiFunction<S,T,S> folder) {
		return new FoldedAsyncExecution<>(seq, () -> init, folder);
	}
}
