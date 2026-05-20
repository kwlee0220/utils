package utils.func;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.Preconditions;
import utils.Throwables;
import utils.stream.FStream;
import utils.stream.FStreamable;

/**
 * 작업 수행의 성공/실패를 두 가지 변형 {@link Success}/{@link Failure}로 표현하는 결과 컨테이너.
 * <p>
 * 예외를 던질 수 있는 작업은 다음 정적 헬퍼들로 래핑하여 {@code Try}로 캡슐화할 수 있다:
 * <ul>
 *   <li>{@link #get(CheckedSupplier)} — {@link CheckedSupplier} 호출 결과 캡슐화.</li>
 *   <li>{@link #run(CheckedRunnable)} — {@link CheckedRunnable} 실행 결과 캡슐화.</li>
 *   <li>{@link #accept(Object, CheckedConsumer)} — {@link CheckedConsumer} 적용 결과 캡슐화.</li>
 *   <li>{@link #lift(CheckedSupplier)} / {@link #lift(CheckedRunnable)} /
 *       {@link #lift(CheckedFunction)} — checked 함수형을 {@code Try} 반환형으로 끌어올림.</li>
 * </ul>
 * 위 헬퍼들은 모두 {@link Exception}만 캡슐화하며 {@link Error}는 그대로 전파한다.
 * <p>
 * 추가로 {@link #map(Function)} / {@link #flatMap(Function)} / {@link #recover(Function)} 등의 연산을
 * 제공하여 모나드처럼 체이닝할 수 있으며, 최종적으로 {@link #get()} / {@link #getOrElse(Object)} /
 * {@link #getOrThrow(Supplier)} 등으로 값을 추출하거나 {@link #toFOption()}으로 {@link FOption}으로 변환할 수 있다.
 *
 * @param <T> 성공 시의 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Try<T> extends FStreamable<T> {
	/**
	 * 주어진 값으로 성공 {@link Try}를 생성한다.
	 *
	 * @param <T>   값 타입.
	 * @param value 성공 결과 값. {@code null}도 허용.
	 * @return {@link Success} 인스턴스.
	 */
	public static <T> Success<T> success(T value) {
		return new Success<>(value);
	}

	/**
	 * 주어진 예외로 실패 {@link Try}를 생성한다.
	 *
	 * @param <T>   값 타입.
	 * @param cause 실패 원인. {@code null}이면 {@link IllegalArgumentException}.
	 * @return {@link Failure} 인스턴스.
	 * @throws IllegalArgumentException {@code cause}가 {@code null}인 경우.
	 */
	public static <T> Failure<T> failure(Throwable cause) {
		Preconditions.checkNotNullArgument(cause, "cause is null");
		return new Failure<>(cause);
	}

	/**
	 * 공변 타입을 좁히는 캐스트 헬퍼. {@code Try<? extends T>}를 {@code Try<T>}로 안전하게 변환한다.
	 *
	 * @param <T> 좁힐 대상 타입.
	 * @param t   원본 {@link Try}.
	 * @return 동일 인스턴스를 {@code Try<T>} 타입으로 본 뷰.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Try<T> narrow(Try<? extends T> t) {
		return (Try<T>) t;
	}

	/**
	 * 주어진 입력에 {@link CheckedConsumer}를 적용하고 결과를 {@link Try}로 감싸 반환한다.
	 * <p>
	 * consumer 수행이 성공하면 {@link Success}({@code null})를, {@link Exception}을 던지면
	 * {@link Failure}를 반환한다. {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param <T>      입력 타입.
	 * @param input    consumer에 전달할 입력값.
	 * @param consumer 적용할 {@link CheckedConsumer}. {@code null} 불가.
	 * @return 성공 시 {@code Success(null)}, 실패 시 {@code Failure(cause)}.
	 * @throws IllegalArgumentException {@code consumer}가 {@code null}인 경우.
	 */
	public static <T> Try<Void> accept(T input, CheckedConsumer<T> consumer) {
		Preconditions.checkNotNullArgument(consumer, "CheckedConsumer is null");

		try {
			consumer.accept(input);
			return success(null);
		}
		catch ( Exception e ) {
			return failure(e);
		}
	}

	/**
	 * 주어진 {@link CheckedSupplier}를 호출하고 결과를 {@link Try}로 감싸 반환한다.
	 * <p>
	 * supplier 수행이 성공하면 {@link Success}({@code value})를, {@link Exception}을 던지면
	 * {@link Failure}를 반환한다. {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param <T>      결과 타입.
	 * @param supplier 호출할 {@link CheckedSupplier}. {@code null} 불가.
	 * @return 성공 시 결과 값으로 구성된 {@code Success}, 실패 시 {@code Failure(cause)}.
	 * @throws IllegalArgumentException {@code supplier}가 {@code null}인 경우.
	 */
	public static <T> Try<T> get(CheckedSupplier<T> supplier) {
		Preconditions.checkNotNullArgument(supplier, "CheckedSupplier is null");

		try {
			return success(supplier.get());
		}
		catch ( Exception e ) {
			return failure(e);
		}
	}

	/**
	 * 주어진 {@link CheckedSupplier}를 {@code Supplier<Try<T>>}로 변환한다.
	 * <p>
	 * 반환된 supplier는 호출될 때마다 원본을 실행하여 {@link Try}로 감싼 결과를 반환한다.
	 * {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param <T>      결과 타입.
	 * @param supplier 변환 대상 {@link CheckedSupplier}. {@code null} 불가.
	 * @return {@link Try}로 감싼 결과를 반환하는 {@link Supplier}.
	 * @throws IllegalArgumentException {@code supplier}가 {@code null}인 경우.
	 */
	public static <T> Supplier<Try<T>> lift(CheckedSupplier<T> supplier) {
		Preconditions.checkNotNullArgument(supplier, "CheckedSupplier is null");

		return () -> {
			try {
				return success(supplier.get());
			}
			catch ( Exception e ) {
				return failure(e);
			}
		};
	}
	
	/**
	 * 주어진 {@link CheckedRunnable}을 실행하고 결과를 {@link Try}로 감싸 반환한다.
	 * <p>
	 * task 수행이 성공적으로 완료되면 {@link Success}({@code null})를, {@link Exception}을 던지면
	 * {@link Failure}를 반환한다. {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param task 수행시킬 태스크. {@code null} 불가.
	 * @return 성공 시 {@code Success(null)}, 실패 시 {@code Failure(cause)}.
	 * @throws IllegalArgumentException {@code task}가 {@code null}인 경우.
	 */
	public static Try<Void> run(CheckedRunnable task) {
		Preconditions.checkNotNullArgument(task, "CheckedRunnable is null");

		try {
			task.run();
			return success(null);
		}
		catch ( Exception e ) {
			return failure(e);
		}
	}
	
	/**
	 * 주어진 {@link CheckedRunnable}을 {@code Supplier<Try<Void>>}로 변환한다.
	 * <p>
	 * 반환된 supplier는 호출될 때마다 task를 실행하여 {@link Try}로 감싼 결과를 반환한다.
	 * {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param task 변환 대상 {@link CheckedRunnable}. {@code null} 불가.
	 * @return 실행 결과를 {@code Try<Void>}로 감싸 반환하는 {@link Supplier}.
	 * @throws IllegalArgumentException {@code task}가 {@code null}인 경우.
	 */
	public static Supplier<Try<Void>> lift(CheckedRunnable task) {
		Preconditions.checkNotNullArgument(task, "CheckedRunnable is null");

		return () -> {
			try {
				task.run();
				return success(null);
			}
			catch ( Exception e ) {
				return failure(e);
			}
		};
	}

	/**
	 * 주어진 {@link CheckedFunction}을 {@code Function<T, Try<R>>}로 변환한다.
	 * <p>
	 * 반환된 함수는 적용 시 입력에 대해 원본 함수를 실행하여 {@link Try}로 감싼 결과를 반환한다.
	 * {@link Error}는 캡슐화하지 않고 그대로 전파한다.
	 *
	 * @param <T>     입력 타입.
	 * @param <R>     출력 타입.
	 * @param checked 변환 대상 {@link CheckedFunction}. {@code null} 불가.
	 * @return {@code Function<T, Try<R>>}.
	 * @throws IllegalArgumentException {@code checked}가 {@code null}인 경우.
	 */
	public static <T,R> Function<T,Try<R>> lift(CheckedFunction<T,R> checked) {
		Preconditions.checkNotNullArgument(checked, "CheckedFunction is null");

		return (input) -> {
			try {
				return success(checked.apply(input));
			}
			catch ( Exception e ) {
				return Try.failure(e);
			}
		};
	}
	
	/**
	 * 현 결과가 성공인지 여부를 반환한다.
	 *
	 * @return 성공이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isSuccessful();

	/**
	 * 현 결과가 실패인지 여부를 반환한다.
	 *
	 * @return 실패이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isFailed();
	
	/**
	 * 현 객체가 실패인 경우 실패를 유발한 예외를 반환한다.
	 *
	 * @return	실패를 유발한 예외 객체.
	 * @throws IllegalStateException 현 객체가 성공인 경우.
	 */
	public Throwable getCause();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 원본 예외가 다시 던져진다.
	 *
	 * @return 수행 결과.
	 */
	public T get();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우 {@code null}이 반환된다.
	 *
	 * @return 수행 결과. 수행이 실패한 경우에는 {@code null}.
	 */
	public T getOrNull();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 원인 예외를 {@link RuntimeException}으로 감싸 던진다.
	 *
	 * @return 수행 결과.
	 * @throws RuntimeException 수행이 실패한 경우.
	 */
	public T getUnchecked();
	
	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 인자로 전달된 대체 객체가 반환된다.
	 *
	 * @param elseValue	예외 발생으로 수행 결과가 없는 경우 반환될 객체.
	 * @return	수행 결과. 수행이 실패된 경우에는 {@code elseValue}.
	 */
	public T getOrElse(T elseValue);

	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, 인자로 전달된 {@link Supplier#get()}을 수행시킨
	 * 결과가 반환된다.
	 *
	 * @param elseSupplier	예외 발생으로 수행 결과가 없는 경우 반환될 객체를 제공할 {@link Supplier} 객체.
	 * @return	수행 결과. 수행이 실패된 경우에는 {@code elseSupplier.get()}.
	 */
	public T getOrElse(Supplier<? extends T> elseSupplier);

	/**
	 * 작업 수행 결과를 반환한다.
	 * <p>
	 * 예외 발생으로 수행이 실패한 경우, {@code thrower}가 제공한 예외를 던진다.
	 *
	 * @param <X>     던질 예외 타입.
	 * @param thrower 실패 시 던질 예외를 제공하는 {@link Supplier}.
	 * @return 수행 결과.
	 * @throws X 수행이 실패된 경우 {@code thrower}가 제공한 예외.
	 */
	public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X;
	
	/**
	 * 작업 수행 결과가 실패한 경우, 실패 원인 ({@link Throwable})을 인자로 {@code recovery}를 호출한
	 * 결과로 대체된 {@link Try} 객체를 반환한다.
	 * <p>
	 * {@code recovery}가 정상 반환하면 {@link Try#success(Object)}, 예외를 던지면
	 * {@link Try#failure(Throwable)}로 감싸진다. 현재 객체가 성공인 경우는 그대로 반환한다.
	 *
	 * @param recovery	실패 원인을 받아 대체 값을 산출하는 {@link Function}.
	 * @return	수행 결과가 실패한 경우 {@code recovery}의 결과로 대체된 {@link Try} 객체.
	 */
	public Try<T> recover(Function<Throwable,T> recovery);

	/**
	 * 작업 수행 결과가 실패한 경우, {@code recovery}를 호출한 결과로 대체된 {@link Try} 객체를 반환한다.
	 * <p>
	 * {@link #recover(Function)}와 달리 실패 원인을 인자로 받지 않는다. {@code recovery}가
	 * 정상 반환하면 {@link Try#success(Object)}, 예외를 던지면 {@link Try#failure(Throwable)}로 감싸진다.
	 * 현재 객체가 성공인 경우는 그대로 반환한다.
	 *
	 * @param recovery	실패 시 호출되는 대체 값 공급자.
	 * @return	수행 결과가 실패한 경우 {@code recovery}의 결과로 대체된 {@link Try} 객체.
	 */
	public Try<T> recover(CheckedSupplier<T> recovery);
	
	/**
	 * 작업 수행 결과에 {@code mapper}를 적용시킨 값으로 구성된 {@link Try} 객체를 반환한다.
	 * <p>
	 * 본 객체가 {@link Success}이면 결과 값에 {@code mapper}를 적용한 {@link Success}를,
	 * {@link Failure}이면 원래 예외를 그대로 가진 {@link Failure}를 반환한다.
	 * {@code mapper} 자체가 예외를 던지면 그 예외는 캡슐화되지 않고 호출자에게 그대로 전파된다.
	 *
	 * @param <S>    출력 타입.
	 * @param mapper 수행 결과 값에 적용할 {@link Function}.
	 * @return {@code mapper} 적용 결과로 구성된 {@link Try} 객체.
	 */
	public <S> Try<S> map(Function<? super T, ? extends S> mapper);
	
	/**
	 * 작업 수행 결과가 실패한 경우, {@code mapper}를 적용시킨 예외로 대체된 {@link Try} 객체를 반환한다.
	 * <p>
	 * 수행 결과가 성공인 경우는 원래 수행 결과를 그대로 반환한다.
	 *
	 * @param mapper	수행 결과가 실패한 경우 대체할 예외를 생성하는 {@link Function}.
	 * @return	수행 결과가 실패한 경우 {@code mapper}의 결과로 대체된 {@link Try} 객체.
	 */
	public Try<T> mapFailure(Function<Throwable,Throwable> mapper);
	
	/**
	 * 작업 수행 결과에 {@code mapper}를 적용시킨 값으로 구성된 {@link Try} 객체를 반환한다.
	 * <p>
	 * 본 객체가 {@link Success}인 경우는 {@code mapper}를 적용시킨 결과로 반환되며,
	 * {@link Failure}인 경우는 원래 예외를 그대로 반환한다.
	 *
	 * @param <S>		출력 타입.
	 * @param mapper	현재 값을 변형시킬 {@link Function}.
	 * @return	{@code mapper}를 적용시킨 값으로 구성된 {@link Try} 객체.
	 */
	public <S> Try<S> flatMap(Function<? super T, Try<? extends S>> mapper);
	
	/**
	 * 현 객체가 성공인 경우에만 주어진 {@link Consumer}를 적용시킨다.
	 * <p>
	 * 현 객체가 실패인 경우는 아무런 동작도 하지 않는다.
	 *
	 * @param action	현 객체가 성공인 경우 적용시킬 {@link Consumer}.
	 * @return	현 객체.
	 */
	public Try<T> ifSuccessful(Consumer<? super T> action);
	
	/**
	 * 현 객체가 실패인 경우에만 주어진 {@link Consumer}를 적용시킨다.
	 * <p>
	 * 현 객체가 성공인 경우는 아무런 동작도 하지 않는다.
	 *
	 * @param handler	현 객체가 실패인 경우 적용시킬 {@link Consumer}.
	 * @return	현 객체.
	 */
	public Try<T> ifFailed(Consumer<Throwable> handler);
	
	/**
	 * 현 객체의 성공/실패 여부에 따라 {@link FOption} 객체를 반환한다.
	 * <p>
	 * 현 객체가 성공인 경우는 {@link FOption#of}에 성공 결과가 포함되며,
	 * 실패인 경우는 {@link FOption#empty()}가 반환된다.
	 *
	 * @return	현 객체의 성공/실패 여부에 따른 {@link FOption} 객체
	 */
	public FOption<T> toFOption();
	
	/**
	 * 성공한 작업 결과를 담는 {@link Try} 구현. 결과 값을 보관하며 {@link #getCause()}는
	 * {@link IllegalStateException}을 던진다.
	 *
	 * @param <T> 결과 타입.
	 */
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

		@Override
		public T getUnchecked() {
			return m_value;
		}

		@Override
		public T getOrNull() {
			return m_value;
		}

		@Override
		public T getOrElse(T elseValue) {
			return m_value;
		}

		@Override
		public T getOrElse(Supplier<? extends T> elseSupplier) {
			return m_value;
		}

		@Override
		public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X {
			return m_value;
		}

		@Override
		public Throwable getCause() {
			throw new IllegalStateException("Try is Success; no cause available");
		}

		@Override
		public <S> Try<S> map(Function<? super T, ? extends S> mapper) {
			return Try.success(mapper.apply(m_value));
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
		public Try<T> recover(Function<Throwable, T> recovery) {
			return this;
		}

		@Override
		public Try<T> recover(CheckedSupplier<T> recovery) {
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
	
	/**
	 * 실패한 작업의 예외를 담는 {@link Try} 구현. {@link #get()}은 sneaky-throw로 원본 예외를
	 * 다시 던지며, {@link #getUnchecked()}는 RuntimeException으로 감싸서 던진다.
	 *
	 * @param <T> 결과 타입 (실제 값은 없음).
	 */
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
			throw new AssertionError("unreachable: sneakyThrow should have thrown");
		}

		@Override
		public T getUnchecked() {
			throw Throwables.toRuntimeException(m_cause);
		}

		@Override
		public T getOrNull() {
			return null;
		}

		@Override
		public T getOrElse(T elseValue) {
			return elseValue;
		}

		@Override
		public T getOrElse(Supplier<? extends T> elseSupplier) {
			return elseSupplier.get();
		}

		@Override
		public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X {
			throw thrower.get();
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
		public Try<T> recover(Function<Throwable, T> recovery) {
			return Try.get(() -> recovery.apply(m_cause));
		}

		@Override
		public Try<T> recover(CheckedSupplier<T> recovery) {
			return Try.get(recovery);
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
