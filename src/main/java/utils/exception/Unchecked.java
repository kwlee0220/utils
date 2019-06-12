package utils.exception;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.vavr.control.Try;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Unchecked {
	
// ******************************************************************************
// ****************************** Runnable **************************************
// ******************************************************************************
	
	public static <T> Runnable liftIE(CheckedRunnable checked) {
		return new UncheckedRunnable<>(checked, new IgnoreErrorHandler<>());
	}
	
	public static <T> Runnable liftRTE(CheckedRunnable checked) {
		return new UncheckedRunnable<>(checked, new ToRuntimeErrorHandler<>());
	}
	
	public static <R> Runnable lift(CheckedRunnable checked, ExceptionHandler<Void,R> handler) {
		return new UncheckedRunnable<>(checked, handler);
	}
	
	public static <T> Supplier<Try<Void>> lift(CheckedRunnable checked) {
		Utilities.checkNotNullArgument(checked, "CheckedRunnable is null");
		
		return () -> {
			try {
				checked.run();
				return Try.success(null);
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	public static void runRTE(CheckedRunnable checked) {
		Utilities.checkNotNullArgument(checked, "CheckedRunnable is null");
		
		try {
			checked.run();
		}
		catch ( Throwable e ) {
			throw Throwables.toRuntimeException(e);
		}
	}
	
	private static class UncheckedRunnable<R> implements Runnable {
		private final CheckedRunnable m_checked;
		private final ExceptionHandler<Void,R> m_handler;
		
		UncheckedRunnable(CheckedRunnable checked, ExceptionHandler<Void,R> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public void run() {
			try {
				m_checked.run();
			}
			catch ( Throwable e ) {
				m_handler.handle(new ExceptionCase<>(null, e));
			}
		}
	}
	

// ******************************************************************************
// ****************************** Consumer **************************************
// ******************************************************************************
	
	public static <T> Consumer<T> liftIE(CheckedConsumer<T> checked) {
		return new UncheckedConsumer<>(checked, new IgnoreErrorHandler<>());
	}
	
	public static <T> Consumer<T> liftRTE(CheckedConsumer<T> checked) {
		return new UncheckedConsumer<>(checked, new ToRuntimeErrorHandler<>());
	}
	
	public static <T,R> Consumer<T> lift(CheckedConsumer<T> checked,
										ExceptionHandler<T,R> handler) {
		return new UncheckedConsumer<>(checked, handler);
	}
	
	public static <T> Function<T,Try<Void>> lift(CheckedConsumer<T> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedConsumer is null");
		
		return (data) -> {
			try {
				checked.accept(data);
				return Try.success(null);
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	private static class UncheckedConsumer<T,R> implements Consumer<T> {
		private final CheckedConsumer<T> m_checked;
		private final ExceptionHandler<T,R> m_handler;
		
		UncheckedConsumer(CheckedConsumer<T> checked, ExceptionHandler<T,R> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public void accept(T data) {
			try {
				m_checked.accept(data);
			}
			catch ( Throwable e ) {
				m_handler.handle(new ExceptionCase<>(data, e));
			}
		}
	}
	

// ******************************************************************************
// ****************************** Supplier **************************************
// ******************************************************************************
	
	public static <T> Supplier<T> liftIE(CheckedSupplier<T> checked) {
		return new UncheckedSupplier<>(checked, new IgnoreErrorHandler<>());
	}
	
	public static <T> Supplier<T> liftRTE(CheckedSupplier<T> checked) {
		return new UncheckedSupplier<>(checked, new ToRuntimeErrorHandler<>());
	}
	
	public static <T> Supplier<T> lift(CheckedSupplier<T> checked,
										ExceptionHandler<Void,T> handler) {
		return new UncheckedSupplier<>(checked, handler);
	}
	
	public static <T> Supplier<Try<T>> lift(CheckedSupplier<T> suppl) {
		Utilities.checkNotNullArgument(suppl, "CheckedSupplier is null");
		
		return () -> {
			try {
				return Try.of(() -> suppl.get());
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	public static <T> Try<T> supply(CheckedSupplier<T> suppl) {
		Utilities.checkNotNullArgument(suppl, "CheckedSupplier is null");
		
		try {
			return Try.of(() -> suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	public static <T> T supplyRTE(CheckedSupplier<T> supplier) {
		Utilities.checkNotNullArgument(supplier, "CheckedSupplier is null");
		
		try {
			return supplier.get();
		}
		catch ( Throwable e ) {
			throw Throwables.toRuntimeException(e);
		}
	}
	

	private static class UncheckedSupplier<S,T> implements Supplier<T> {
		private final CheckedSupplier<T> m_checked;
		private final ExceptionHandler<S,T> m_handler;
		
		UncheckedSupplier(CheckedSupplier<T> checked, ExceptionHandler<S,T> handler) {
			m_checked = checked;
			m_handler = handler;
		}
	
		@Override
		public T get() {
			try {
				return m_checked.get();
			}
			catch ( Throwable e ) {
				m_handler.handle(new ExceptionCase<>(null, e));
				return null;
			}
		}
	}


// ******************************************************************************
// ****************************** Function **************************************
// ******************************************************************************
	
	public static <T,R> Function<T,R> liftIE(CheckedFunction<T,R> checked) {
		return new UncheckedFunction<>(checked, new IgnoreErrorHandler<>());
	}

	public static <T,R> Function<T,R> liftRTE(CheckedFunction<T,R> checked) {
		return new UncheckedFunction<>(checked, new ToRuntimeErrorHandler<>());
	}
	
	public static <T,R> Function<T,Try<R>> lift(CheckedFunction<T,R> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedFunction is null");
		
		return (input) -> {
			try {
				return Try.of(() -> checked.apply(input));
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	public static <T,R> Function<T,R> lift(CheckedFunction<T,R> checked,
											ExceptionHandler<T,R> handler) {
		Utilities.checkNotNullArgument(checked, "CheckedFunction is null");
		
		return new UncheckedFunction<>(checked, handler);
	}
	
	private static class UncheckedFunction<T,R> implements Function<T,R> {
		private final CheckedFunction<T,R> m_checked;
		private final ExceptionHandler<T,R> m_handler;
		
		UncheckedFunction(CheckedFunction<T,R> checked, ExceptionHandler<T,R> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public R apply(T input) {
			try {
				return m_checked.apply(input);
			}
			catch ( Throwable e ) {
				m_handler.handle(new ExceptionCase<>(input, e));
				return null;
			}
		}
	}
	
	
// ******************************************************************************
// ****************************** Predicate *************************************
// ******************************************************************************
	
	public static <T> Predicate<T> liftRTE(CheckedPredicate<T> checked) {
		return new UncheckedPredicate<>(checked, new ToRuntimeErrorHandler<>());
	}
	
	public static <T> Function<T,Try<Boolean>> lift(CheckedPredicate<T> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedPredicate is null");
		
		return (input) -> {
			try {
				checked.test(input);
				return Try.success(null);
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	private static class UncheckedPredicate<T,R> implements Predicate<T> {
		private final CheckedPredicate<T> m_checked;
		private final ExceptionHandler<T,R> m_handler;
		
		UncheckedPredicate(CheckedPredicate<T> checked, ExceptionHandler<T,R> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public boolean test(T input) {
			try {
				return m_checked.test(input);
			}
			catch ( Throwable e ) {
				m_handler.handle(new ExceptionCase<>(input, e));
				return false;
			}
		}
	}
}
