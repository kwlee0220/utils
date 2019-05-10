package utils.async.op;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.async.AbstractAsyncExecution;
import utils.async.AbstractThreadedExecution;
import utils.async.FutureBasedAsyncExecution;
import utils.async.StartableExecution;
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
	
	public static <T> StartableExecution<T> from(Callable<T> work) {
		return new AbstractThreadedExecution<T>() {
			@Override
			protected T executeWork() throws Exception {
				return work.call();
			}
		};
	}
	
	public static <T> StartableExecution<T> idle(T result, long delay, TimeUnit unit,
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
	
	public static StartableExecution<Void> idle(long delay, TimeUnit unit,
											ScheduledExecutorService scheduler) {
		return AsyncExecutions.<Void>idle(null, delay, unit, scheduler);
	}
	
	public static <T> StartableExecution<T> nop(T result) {
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
	
	public static StartableExecution<Void> nop() {
		return nop(null);
	}
	
	public static <T> StartableExecution<T> failure(Throwable cause) {
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
	
	public static <T> StartableExecution<T> cancelled() {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				notifyStarted();
				CompletableFuture.runAsync(this::notifyCancelled);
			}
		};
	}

	public static <T> SequentialAsyncExecution<T> sequential(
												FStream<StartableExecution<?>> sequence) {
		return new SequentialAsyncExecution<>(sequence);
	}

	public static <T> SequentialAsyncExecution<T> sequential(
											List<StartableExecution<T>> elms) {
		return new SequentialAsyncExecution<>(FStream.from(elms));
	}

	@SafeVarargs
	public static <T> SequentialAsyncExecution<T> sequential(
											StartableExecution<? extends T>... sequence) {
		return new SequentialAsyncExecution<>(FStream.of(sequence));
	}

	public static <T> ConcurrentAsyncExecution concurrent(StartableExecution<?>... elements) {
		return new ConcurrentAsyncExecution(elements);
	}

	public static <T> BackgroundedAsyncExecution<T> backgrounded(StartableExecution<T> fg,
																StartableExecution<?> bg) {
		return new BackgroundedAsyncExecution<>(fg, bg);
	}

	public static <T> TimedAsyncExecution<T> timed(StartableExecution<T> target, long timeout,
												TimeUnit unit, ScheduledExecutorService scheduler) {
		return new TimedAsyncExecution<>(target, timeout, unit, scheduler);
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<T>> seq,
														Supplier<S> initSupplier,
														BiFunction<S,T,S> folder) {
		return new FoldedAsyncExecution<>(seq, initSupplier, folder);
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<T>> seq,
														S init, BiFunction<S,T,S> folder) {
		return new FoldedAsyncExecution<>(seq, () -> init, folder);
	}
}
