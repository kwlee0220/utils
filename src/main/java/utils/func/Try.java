package utils.func;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.Throwables;
import utils.Utilities;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Try<T> {
	public static <T> Success<T> success(T value) {
		return new Success<>(value);
	}
	
	public static <T> Failure<T> failure(Throwable cause) {
		return new Failure<>(cause);
	}
	
    @SuppressWarnings("unchecked")
    public static <T> Try<T> narrow(Try<? extends T> t) {
        return (Try<T>) t;
    }
	
	public static <T> Try<T> supply(CheckedSupplier<T> supplier) {
		try {
			return success(supplier.get());
		}
		catch ( Throwable e ) {
			return failure(e);
		}
	}
	
	public static <T> Supplier<Try<T>> lift(CheckedSupplier<T> supplier) {
		return () -> {
			try {
				return success(supplier.get());
			}
			catch ( Throwable e ) {
				return failure(e);
			}
		};
	}
	
	public static Try<Void> run(CheckedRunnable task) {
		try {
			task.run();
			return success(null);
		}
		catch ( Throwable e ) {
			return failure(e);
		}
	}
	
	public static Supplier<Try<Void>> lift(CheckedRunnable task) {
		return () -> {
			try {
				task.run();
				return success(null);
			}
			catch ( Throwable e ) {
				return failure(e);
			}
		};
	}
	
	public static <T,R> Function<T,Try<R>> lift(CheckedFunction<T,R> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedFunction is null");
		
		return (input) -> {
			try {
				return success(checked.apply(input));
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		};
	}
	
	public boolean isSuccess();
	public boolean isFailure();
	public Throwable getCause();
	
	public T get();
	public <X extends Throwable> T get(Class<X> thrCls) throws X;
	public T getOrNull();
	public T getOrRTE();
	public T getOrElse(T candidate);
	public T getOrElse(Supplier<? extends T> suppl);
	
	public <S> Try<S> map(Function<? super T, ? extends S> mapper);
	public <S> Try<S> tryMap(CheckedFunction<? super T, ? extends S> mapper);
	public Try<T> mapFailure(Function<Throwable,Throwable> mapper);
	
	public <S> Try<S> flatMap(Function<? super T, Try<? extends S>> mapper);
	
	public void onSuccess(Consumer<? super T> action);
	public void onFailure(Consumer<Throwable> handler);
	
	public Try<T> recover(Function<Throwable,Try<? extends T>> recovery);
	
	public FStream<T> toFStream();
	public FOption<T> toFOption();
	
	public static class Success<T> implements Try<T> {
		private final T m_value;
		
		private Success(T value) {
			m_value = value;
		}

		@Override
		public boolean isSuccess() {
			return true;
		}

		@Override
		public boolean isFailure() {
			return false;
		}

		@Override
		public T get() {
			return m_value;
		}

		@Override
		public <X extends Throwable> T get(Class<X> thrCls) throws X {
			return m_value;
		}

		@Override
		public T getOrNull() {
			return m_value;
		}

		@Override
		public T getOrRTE() {
			return m_value;
		}

		@Override
		public T getOrElse(T candidate) {
			return m_value;
		}

		@Override
		public T getOrElse(Supplier<? extends T> suppl) {
			return m_value;
		}

		@Override
		public Throwable getCause() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S> Try<S> map(Function<? super T, ? extends S> mapper) {
			return Try.success(mapper.apply(m_value));
		}

		@Override
		public <S> Try<S> tryMap(CheckedFunction<? super T, ? extends S> mapper) {
			try {
				return Try.success(mapper.apply(m_value));
			}
			catch ( Throwable e ) {
				return Try.failure(e);
			}
		}

		@Override
		public Try<T> mapFailure(Function<Throwable, Throwable> mapper) {
			return this;
		}

		@Override
		public <S> Try<S> flatMap(Function<? super T, Try<? extends S>> mapper) {
			return Try.narrow(mapper.apply(m_value));
		}

		@Override
		public void onSuccess(Consumer<? super T> action) {
			action.accept(m_value);
		}

		@Override
		public void onFailure(Consumer<Throwable> handler) { }

		@Override
		public Try<T> recover(Function<Throwable, Try<? extends T>> recovery) {
			return this;
		}

		@Override
		public FStream<T> toFStream() {
			return FStream.of(m_value);
		}

		@Override
		public FOption<T> toFOption() {
			return FOption.of(m_value);
		}
	}
	
	public static class Failure<T> implements Try<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public boolean isFailure() {
			return true;
		}

		@Override
		public T get() {
			return Throwables.sneakyThrow(m_cause);
		}

		@Override
		public <X extends Throwable> T get(Class<X> thrCls) throws X {
			Throwables.throwIfInstanceOf(m_cause, thrCls);
			throw Throwables.toRuntimeException(m_cause);
		}

		@Override
		public T getOrNull() {
			return null;
		}

		@Override
		public T getOrRTE() {
			throw Throwables.toRuntimeException(m_cause);
		}

		@Override
		public T getOrElse(T candidate) {
			return candidate;
		}

		@Override
		public T getOrElse(Supplier<? extends T> suppl) {
			return suppl.get();
		}

		@Override
		public Throwable getCause() {
			return m_cause;
		}

		@Override
		public <S> Try<S> map(Function<? super T, ? extends S> mapper) {
			return failure(m_cause);
		}

		@Override
		public <S> Try<S> tryMap(CheckedFunction<? super T, ? extends S> mapper) {
			return failure(m_cause);
		}

		@Override
		public Try<T> mapFailure(Function<Throwable, Throwable> mapper) {
			return failure(mapper.apply(m_cause));
		}

		@Override
		public <S> Try<S> flatMap(Function<? super T, Try<? extends S>> mapper) {
			return failure(m_cause);
		}

		@Override
		public void onSuccess(Consumer<? super T> action) { }

		@Override
		public void onFailure(Consumer<Throwable> handler) {
			handler.accept(m_cause);
		}

		@Override
		public Try<T> recover(Function<Throwable, Try<? extends T>> recovery) {
			return Try.narrow(recovery.apply(m_cause));
		}

		@Override
		public FStream<T> toFStream() {
			return FStream.empty();
		}

		@Override
		public FOption<T> toFOption() {
			return FOption.empty();
		}
	}
}
