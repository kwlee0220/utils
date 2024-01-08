package utils.func;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.Throwables;
import utils.Utilities;
import utils.stream.FStream;
import utils.stream.FStreamable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Try<T> extends FStreamable<T> {
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
	
	public static <T> Try<T> get(CheckedSupplier<T> supplier) {
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
	
	/**
	 * 주어진 {@link CheckedRunnable}를 실행시킨다.
	 * <p>
	 * {@link CheckedRunnable} 수행이 성공적으로 완료되는 경우는  {@link Success}가
	 * 반환되고, 예외가 발생된 경우는 {@link Failure}가 반환된다.
	 * 
	 * @param task	수행시킬 태스크.
	 * @return	태스크 수행 성공 여부.
	 */
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
	
	public boolean isSuccessful();
	public boolean isFailed();
	public Throwable getCause();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 발생된 예외가 발생된다.
	 * 
	 * @return	수행 결과
	 */
	public T get();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, {@code null}이 반환된다.
	 * 
	 * @return	수행 결과. 수행이 실패된 경우에는 {@code null}
	 */
	public T getOrNull();
	
	public T getUnchecked();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 인자로 전달된 대체 객체가 반환됨.
	 * 
	 * @param elseValue	예외 발생으로 수행 결과가 없는 경우 반환될 객체.
	 * @return	수행 결과. 수행이 실패된 경우에는 {@code null}
	 */
	public T getOrElse(T elseValue);
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 인자로 전달된 {@link Supplier#get()} 을 수행시킨
	 * 결과가 반환된다. 
	 * 
	 * @param elseSupplier	예외 발생으로 수행 결과가 없는 경우 반환될 객체를 제공할 {@link Supplier} 객체.
	 * @return	수행 결과. 수행이 실패된 경우에는 {@code null}
	 */
	public T getOrElse(Supplier<? extends T> elseSupplier);
	
	/**
	 * 작업 수행 결과에 {@code mapper}를 적용시킨 값으로 구성된 {@link Try} 객체를 반환한다.
	 * <p>
	 * 예외 발생으로 수행 결과가 없는 경우는 {@link Failure} 객체가 반환된다.
	 * 
	 * @param <S>	출력 타입
	 * @param mapper	수행 결과 값에 반영시킬 {@link Function}.
	 * @return	{@link Function} 적용 결과가 반영된 {@link Try} 객체.
	 */
	public <S> Try<S> map(Function<? super T, ? extends S> mapper);
	public <S> Try<S> tryMap(CheckedFunction<? super T, ? extends S> mapper);
	public Try<T> mapFailure(Function<Throwable,Throwable> mapper);
	
	public <S> Try<S> flatMap(Function<? super T, Try<? extends S>> mapper);
	
	public Try<T> ifSuccessful(Consumer<? super T> action);
	public Try<T> ifFailed(Consumer<Throwable> handler);
	
	public Try<T> recover(Function<Throwable,Try<? extends T>> recovery);
	
	public FOption<T> toFOption();
	
	public static final class Success<T> implements Try<T> {
		private final T m_value;
		
		private Success(T value) {
			m_value = value;
		}

		@Override
		public boolean isSuccessful() {
			return true;
		}

		@Override
		public boolean isFailed() {
			return false;
		}

		@Override
		public T get() {
			return m_value;
		}
		
		public T getUnchecked() {
			return m_value;
		}

		@Override
		public T getOrNull() {
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
			throw new IllegalStateException();
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
		public Try<T> ifSuccessful(Consumer<? super T> action) {
			action.accept(m_value);
			return this;
		}

		@Override
		public Try<T> ifFailed(Consumer<Throwable> handler) {
			return this;
		}

		@Override
		public Try<T> recover(Function<Throwable, Try<? extends T>> recovery) {
			return this;
		}

		@Override
		public FStream<T> fstream() {
			return FStream.of(m_value);
		}

		@Override
		public FOption<T> toFOption() {
			return FOption.of(m_value);
		}
		
		@Override
		public String toString() {
			return String.format("Success(%s)", m_value);
		}
	}
	
	public static final class Failure<T> implements Try<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public boolean isSuccessful() {
			return false;
		}

		@Override
		public boolean isFailed() {
			return true;
		}

		@Override
		public T get() {
			Throwables.sneakyThrow(m_cause);
			throw new AssertionError();
		}
		
		public T getUnchecked() {
			throw Throwables.toRuntimeException(m_cause);
		}

		@Override
		public T getOrNull() {
			return null;
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
		public Try<T> ifSuccessful(Consumer<? super T> action) { 
			return this;
		}

		@Override
		public Try<T> ifFailed(Consumer<Throwable> handler) {
			handler.accept(m_cause);
			return this;
		}

		@Override
		public Try<T> recover(Function<Throwable, Try<? extends T>> recovery) {
			return Try.narrow(recovery.apply(m_cause));
		}

		@Override
		public FStream<T> fstream() {
			return FStream.empty();
		}

		@Override
		public FOption<T> toFOption() {
			return FOption.empty();
		}
		
		@Override
		public String toString() {
			return String.format("Failure(%s)", m_cause);
		}
	}
}
