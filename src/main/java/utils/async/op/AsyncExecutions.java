package utils.async.op;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import utils.Throwables;
import utils.async.AbstractAsyncExecution;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;
import utils.func.Lazy;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncExecutions {
	static final Logger s_logger = LoggerFactory.getLogger(AsyncExecutions.class);
	
	private static final Lazy<ListeningScheduledExecutorService> SCHEDULER
																= Lazy.of(AsyncExecutions::createScheduler);
	
	private AsyncExecutions() {
		throw new AssertionError("Should not be called: class=" + AsyncExecutions.class);
	}
	
	public static ListeningScheduledExecutorService getScheduler() {
		return SCHEDULER.get();
	}
	
	public static <T> AbstractAsyncExecution<T> nop(T result) {
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
	public static AbstractAsyncExecution<Void> nop() {
		return nop(null);
	}
	
	public static <T> AbstractAsyncExecution<T> throwAsync(Throwable cause) {
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
	
	public static <T> AbstractAsyncExecution<T> cancelAsync() {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				notifyStarted();
				CompletableFuture.runAsync(this::notifyCancelled);
			}
		};
	}
	
	public static <T> StartableExecution<T> idle(T result, Duration timeout) {
		Callable<T> act = new Callable<T>() {
			@Override
			public T call() throws Exception {
				return result;
			}
		};
		return new ListenableFutureAsyncExecution<T>() {
			@Override
			protected ListenableFuture<? extends T> startExecution() {
				ListeningScheduledExecutorService scheduler = SCHEDULER.get();
				return scheduler.schedule(act, timeout);
			}
		};
	}
	public static <T> StartableExecution<T> idle(Duration timeout) {
		return idle(null, timeout);
	}
	
	public static <T> StartableExecution<T> delayed(CheckedSupplier<T> supplier, Duration delay) {
		Callable<T> act = new Callable<T>() {
			@Override
			public T call() throws Exception {
				try {
					return supplier.get();
				}
				catch ( Throwable e ) {
					throw Throwables.toException(e);
				}
			}
		};
		return new ListenableFutureAsyncExecution<T>() {
			@Override
			protected ListenableFuture<? extends T> startExecution() {
				ListeningScheduledExecutorService scheduler = SCHEDULER.get();
				return scheduler.schedule(act, delay);
			}
		};
	}
	
	public static StartableExecution<Void> delayed(CheckedRunnable handle, Duration delay) {
		Callable<Void> act = new Callable<>() {
			@Override
			public Void call() throws Exception {
				try {
					handle.run();
					return null;
				}
				catch ( Throwable e ) {
					throw Throwables.toException(e);
				}
			}
		};
		return new ListenableFutureAsyncExecution<Void>() {
			@Override
			protected ListenableFuture<Void> startExecution() {
				ListeningScheduledExecutorService scheduler = SCHEDULER.get();
				return scheduler.schedule(act, delay);
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

	public static <T> TimedAsyncExecution<T> timed(StartableExecution<T> target, Duration timeout,
												ScheduledExecutorService scheduler) {
		return new TimedAsyncExecution<>(target, timeout, scheduler);
	}
	public static <T> TimedAsyncExecution<T> timed(StartableExecution<T> target, Duration timeout) {
		return new TimedAsyncExecution<>(target, timeout, SCHEDULER.get());
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<S>> seq,
														Supplier<? extends T> initSupplier,
														BiFunction<T,S,T> folder) {
		return new FoldedAsyncExecution<>(seq, initSupplier, folder);
	}

	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<S>> seq,
														T init, BiFunction<T,S,T> folder) {
		return new FoldedAsyncExecution<>(seq, () -> init, folder);
	}

	public static ListeningScheduledExecutorService createScheduler() {
		return MoreExecutors.listeningDecorator(Executions.getExecutor());
	}
}
