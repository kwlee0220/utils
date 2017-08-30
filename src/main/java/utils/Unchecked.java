package utils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedConsumer;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Unchecked {
	public static Runnable liftIE(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable ignored ) { }
		};
	}
	public static Runnable liftRTE(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable e ) {
				throw ExceptionUtils.toRuntimeException(e);
			}
		};
	}
	public static Supplier<Try<Void>> lift(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		return () -> {
			try {
				task.run();
				return Try.of(() -> null);
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	public static boolean runIE(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		try {
			task.run();
			return true;
		}
		catch ( Throwable ignored ) {
			return false;
		}
	}
	public static void runRTE(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		try {
			task.run();
		}
		catch ( Throwable e ) {
			throw ExceptionUtils.toRuntimeException(e);
		}
	}
	public static Try<Void> tryToRun(CheckedRunnable task) {
		Preconditions.checkArgument(task != null, "Runnable is null");
		
		try {
			task.run();
			return Try.of(() -> null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}

// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	public static <T> Supplier<T> liftRTE(CheckedSupplier<T> suppl) {
		Preconditions.checkArgument(suppl != null, "Supplier is null");
		
		return () -> {
			try {
				return suppl.get();
			}
			catch ( Throwable e ) {
				throw ExceptionUtils.toRuntimeException(e);
			}
		};
	}
	public static <T> Supplier<Try<T>> lift(CheckedSupplier<T> suppl) {
		Preconditions.checkArgument(suppl != null, "Supplier is null");
		
		return () -> tryToSupply(suppl);
	}
	public static <T> Try<T> tryToSupply(CheckedSupplier<T> suppl) {
		Preconditions.checkArgument(suppl != null, "Supplier is null");
		
		try {
			return Try.of(() -> suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}

// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	public static <T> Consumer<T> liftIE(CheckedConsumer<T> consumer) {
		Preconditions.checkArgument(consumer != null, "Consumer is null");
		
		return (t) -> {
			try {
				consumer.accept(t);
			}
			catch ( Throwable ignored ) { }
		};
	}
	public static <T> Consumer<T> liftRTE(CheckedConsumer<T> consumer) {
		Preconditions.checkArgument(consumer != null, "Consumer is null");
		
		return (t) -> {
			try {
				consumer.accept(t);
			}
			catch ( Throwable e ) {
				throw ExceptionUtils.toRuntimeException(e);
			}
		};
	}
	public static <T> Function<T,Try<Void>> lift(CheckedConsumer<T> consumer) {
		Preconditions.checkArgument(consumer != null, "Consumer is null");
		
		return (t) -> {
			try {
				return Try.of(() -> {
					consumer.accept(t);
					return null;
				});
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}

// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	public static <T1,R> Function<T1,R> liftRTE(CheckedFunction1<T1,R> func) {
		Preconditions.checkArgument(func != null, "Function is null");
		
		return (t) -> {
			try {
				return func.apply(t);
			}
			catch ( Throwable e ) {
				throw ExceptionUtils.toRuntimeException(e);
			}
		};
	}

// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	public static Runnable toRunnable(CheckedRunnable task,
									CheckedConsumer<Throwable> errorHandler) {
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable e ) {
				liftRTE(errorHandler).accept(e);
			}
		};
	}

// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	@FunctionalInterface
	public static interface CheckedSupplier<T> {
		public T get() throws Throwable;
	}
}
