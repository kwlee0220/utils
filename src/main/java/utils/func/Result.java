package utils.func;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.Throwables;

/**
 * 어떤 연산의 수행 결과를 표현하는 클래스이다.
 * <p>
 * {@code Result}는 다음 세 가지 상태 중 하나를 가진다.
 * <ul>
 *   <li><b>성공(Success)</b>: 연산이 정상적으로 완료되어 결과 값을 가진 상태. 결과 값은 {@code null}일 수도 있다.</li>
 *   <li><b>취소(Cancelled)</b>: 연산이 취소되었거나 결과 값이 존재하지 않는 상태({@link #filter(Predicate)}에서
 *       술어를 만족하지 않은 경우 포함). 값을 꺼내려 하면 {@link CancellationException}이 난다.</li>
 *   <li><b>실패(Failure)</b>: 연산 도중 예외가 발생한 상태로, 발생한 예외 원인을 가진다.</li>
 * </ul>
 * {@code map}/{@code filter}/{@code flatMap} 연산을 통해 결과를 변형/연결할 수 있으며,
 * 변형 도중 예외가 발생하면 자동으로 실패 상태로 전환된다. {@link #toTry()}로 {@link Try}로 바꿀 수 있다
 * (None은 {@link CancellationException}을 원인으로 하는 실패가 된다).
 *
 * @param <T>	연산 결과 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Result<T> {
	private Result() { }

	/**
	 * 주어진 값을 결과로 갖는 성공(Success) 상태의 {@code Result}를 생성한다.
	 *
	 * @param <T>		결과 값 타입.
	 * @param result	결과 값. {@code null}도 허용된다.
	 * @return	성공 상태의 {@code Result} 객체.
	 */
	public static <T> Result<T> success(T result) {
		return new Success<>(result);
	}

	/**
	 * 결과 값이 존재하지 않는 취소(Cancelled) 상태의 {@code Result}를 반환한다.
	 *
	 * @param <T>	결과 값 타입.
	 * @return	없음 상태의 {@code Result} 객체.
	 */
    @SuppressWarnings("unchecked")
	public static <T> Result<T> cancelled() {
        return (Cancelled<T>) Cancelled.INSTANCE;
    }

	/**
	 * 주어진 예외를 원인으로 갖는 실패(Failure) 상태의 {@code Result}를 생성한다.
	 *
	 * @param <T>	결과 값 타입.
	 * @param cause	실패 원인 예외. 원인을 알 수 없는 실패면 {@code null}일 수 있다(예: 원인 없이
	 * 				{@code notifyFailed(null)}로 끝난 {@code EventDrivenExecution}의 결과).
	 * @return	실패 상태의 {@code Result} 객체.
	 */
	public static <T> Result<T> failure(@Nullable Throwable cause) {
		return new Failure<>(cause);
	}

	/**
	 * 이 결과가 성공(Success) 상태인지 여부를 반환한다.
	 *
	 * @return	성공 상태이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isSuccessful() {
		return false;
	}

	/**
	 * 이 결과가 취소(Cancelled) 상태인지 여부를 반환한다.
	 *
	 * @return	없음 상태이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isCancelled() {
		return false;
	}

	/**
	 * 이 결과가 실패(Failure) 상태인지 여부를 반환한다.
	 *
	 * @return	실패 상태이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isFailed() {
		return false;
	}

	/**
	 * 결과 값을 반환한다.
	 *
	 * @return	성공 상태인 경우의 결과 값.
	 * @throws ExecutionException	실패(Failure) 상태인 경우. 실패 원인 예외가 cause로 설정된다.
	 * @throws CancellationException	취소(Cancelled) 상태인 경우.
	 */
	public abstract T get() throws ExecutionException, CancellationException;

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 비검사 예외를 던진다.
	 * <p>
	 * {@link #get()}과 달리 실패 원인을 {@link ExecutionException}으로 감싸지 않는다. 실패 원인이
	 * {@link RuntimeException}·{@link Error}이면 그대로, 검사 예외이면 {@link RuntimeException}으로 감싸서 던진다
	 * ({@link Throwables#toRuntimeException(Throwable)}).
	 *
	 * @return	성공 상태인 경우의 결과 값.
	 * @throws CancellationException	취소(Cancelled) 상태인 경우.
	 * @see #get()
	 */
	public T getUnchecked() {
		if ( isSuccessful() ) {
			return getOrNull();
		}
		else if ( isCancelled() ) {
			throw new CancellationException();
		}
		else {
			throw Throwables.toRuntimeException(getCause());
		}
	}

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 {@code null}을 반환한다.
	 *
	 * @return	성공 상태인 경우의 결과 값. 그렇지 않으면 {@code null}.
	 */
	public T getOrNull() {
		return null;
	}

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 주어진 기본 값을 반환한다.
	 * <p>
	 * {@code getOrElse(Supplier)}와 오버로드되어 있으므로 {@code getOrElse(null)}은 컴파일되지 않는다(모호함).
	 * 기본값이 {@code null}이면 {@link #getOrNull()}을 쓴다.
	 *
	 * @param other	성공 상태가 아닌 경우 반환할 기본 값.
	 * @return	성공 상태인 경우의 결과 값. 그렇지 않으면 {@code other}.
	 */
    public T getOrElse(T other) {
		return other;
    }

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 주어진 함수가 생성한 예외를 던진다.
	 *
	 * @param <X>				던질 예외 타입.
	 * @param exceptionProvider	실패 원인을 입력받아 던질 예외를 생성하는 함수.
	 * 							취소(Cancelled) 상태인 경우에는 {@link CancellationException}이 인자로 전달된다.
	 * @return	성공 상태인 경우의 결과 값.
	 * @throws X	성공 상태가 아닌 경우 {@code exceptionProvider}가 생성한 예외.
	 */
	public abstract <X extends Throwable> T getOrThrow(
							Function<? super Throwable, X> exceptionProvider) throws X;

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 주어진 공급자가 생성한 값을 반환한다.
	 *
	 * @param supplier	성공 상태가 아닌 경우 반환할 값을 생성하는 공급자.
	 * @return	성공 상태인 경우의 결과 값. 그렇지 않으면 {@code supplier}가 생성한 값.
	 */
	public T getOrElse(Supplier<? extends T> supplier) {
        Preconditions.checkNotNullArgument(supplier, "supplier is null");

		return supplier.get();
    }

	/**
	 * 결과 값을 반환하되, 성공 상태가 아닌 경우 주어진 공급자가 생성한 예외를 던진다.
	 *
	 * @param <X>		던질 예외 타입.
	 * @param supplier	성공 상태가 아닌 경우 던질 예외를 생성하는 공급자.
	 * @return	성공 상태인 경우의 결과 값.
	 * @throws X	성공 상태가 아닌 경우 {@code supplier}가 생성한 예외.
	 */
	public <X extends Throwable> T getOrThrow(Supplier<X> supplier) throws X {
        Preconditions.checkNotNullArgument(supplier, "supplier is null");

        throw supplier.get();
    }

	/**
	 * 이 결과가 성공 상태이면 자신을, 그렇지 않으면 주어진 {@code other}를 반환한다.
	 *
	 * @param other	이 결과가 성공 상태가 아닐 때 반환할 대체 {@code Result}.
	 * @return	이 결과가 성공 상태이면 자신, 그렇지 않으면 {@code other}.
	 */
    @SuppressWarnings("unchecked")
    public Result<T> orElse(Result<? extends T> other) {
        Preconditions.checkNotNullArgument(other, "other is null");
		return isSuccessful() ? this : (Result<T>)other;
    }

	/**
	 * 실패 원인 예외를 반환한다.
	 *
	 * @return	실패(Failure) 상태인 경우의 원인 예외.
	 * @throws IllegalStateException	실패 상태가 아닌 경우.
	 */
    public Throwable getCause() {
        throw new IllegalStateException("getCause on " + this);
    }

	/**
	 * 이 결과가 성공 상태인 경우 주어진 동작을 결과 값에 대해 수행한다.
	 *
	 * @param action	성공 상태일 때 결과 값에 대해 수행할 동작.
	 * @return	메소드 연결(chaining)을 위한 이 {@code Result} 객체.
	 */
    public Result<T> ifSuccessful(Consumer<? super T> action) {
        Preconditions.checkNotNullArgument(action, "action is null");
        return this;
    }

	/**
	 * 이 결과가 없음 상태인 경우 주어진 동작을 수행한다.
	 *
	 * @param action	없음 상태일 때 수행할 동작.
	 * @return	메소드 연결(chaining)을 위한 이 {@code Result} 객체.
	 */
    public Result<T> ifCancelled(Runnable action) {
        Preconditions.checkNotNullArgument(action, "action is null");
        return this;
    }

	/**
	 * 이 결과가 실패 상태인 경우 주어진 동작을 실패 원인 예외에 대해 수행한다.
	 *
	 * @param action	실패 상태일 때 원인 예외에 대해 수행할 동작.
	 * @return	메소드 연결(chaining)을 위한 이 {@code Result} 객체.
	 */
    public Result<T> ifFailed(Consumer<? super Throwable> action) {
        Preconditions.checkNotNullArgument(action, "action is null");
        return this;
    }

	/**
	 * 이 결과를 {@link Try}로 변환한다.
	 * <p>
	 * 성공 상태는 성공 {@code Try}로, 실패 상태는 실패 {@code Try}로 변환되며,
	 * 없음 상태는 {@link CancellationException}을 원인으로 갖는 실패 {@code Try}로 변환된다.
	 *
	 * @return	변환된 {@code Try} 객체.
	 */
    public Try<T> toTry() {
    	if ( isSuccessful() ) {
    		return Try.success(getUnchecked());
    	}
    	else if ( isFailed() ) {
    		return Try.failure(getCause());
    	}
    	else if ( isCancelled() ) {
    		return Try.failure(new CancellationException());
    	}
    	else {
    		throw new AssertionError();
    	}
    }
	
	/**
	 * 성공 상태인 경우 결과 값에 주어진 함수를 적용한 새 {@code Result}를 반환한다.
	 * <p>
	 * 성공 상태가 아니면 상태가 그대로 유지된 새 {@code Result}를 반환하며,
	 * 함수 적용 도중 예외가 발생하면 그 예외를 원인으로 갖는 실패 상태로 전환된다.
	 *
	 * @param <U>		변환된 결과 값 타입.
	 * @param mapper	결과 값에 적용할 변환 함수.
	 * @return	변환된 {@code Result} 객체.
	 */
    public abstract <U> Result<U> map(Function<? super T, ? extends U> mapper);
	/**
	 * 성공 상태인 경우 결과 값에 주어진 술어를 적용하여 결과를 걸러낸다.
	 * <p>
	 * 술어가 {@code true}를 반환하면 자신을, {@code false}를 반환하면 취소(Cancelled) 상태를 반환한다.
	 * 성공 상태가 아니면 상태가 그대로 유지되며, 술어 평가 도중 예외가 발생하면
	 * 그 예외를 원인으로 갖는 실패 상태로 전환된다.
	 *
	 * @param predicate	결과 값에 적용할 술어.
	 * @return	걸러낸 {@code Result} 객체.
	 */
	public abstract Result<T> filter(Predicate<? super T> predicate);
	/**
	 * 성공 상태인 경우 결과 값에 주어진 함수를 적용하여 그 결과로 얻은 {@code Result}를 반환한다.
	 * <p>
	 * 성공 상태가 아니면 상태가 그대로 유지된 새 {@code Result}를 반환하며,
	 * 함수 적용 도중 예외가 발생하면 그 예외를 원인으로 갖는 실패 상태로 전환된다.
	 *
	 * @param <U>		변환된 결과 값 타입.
	 * @param mapper	결과 값에 적용할, {@code Result}를 반환하는 변환 함수.
	 * @return	변환된 {@code Result} 객체.
	 */
	public abstract <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper);

	/**
	 * {@code Result}의 타입 매개변수를 상위 타입으로 변환한다.
	 *
	 * @param <T>		변환 대상 타입.
	 * @param result	변환할 {@code Result} 객체.
	 * @return	타입이 변환된 {@code Result} 객체. (입력 객체와 동일한 객체이다.)
	 */
    @SuppressWarnings("unchecked")
	public static <T> Result<T> narrow(Result<? extends T> result) {
    	return (Result<T>)result;
    }
	
	private static class Success<T> extends Result<T> {
		private final T m_value;
		
		private Success(T result) {
			m_value = result;
		}

		@Override
		public boolean isSuccessful() {
			return true;
		}
		
		@Override
		public T get() {
			return m_value;
		}

		@Override
		public T getOrNull() {
			return m_value;
		}

		@Override
	    public T getOrElse(T other) {
			return m_value;
	    }

		@Override
		public T getOrElse(Supplier<? extends T> supplier) {
	        Preconditions.checkNotNullArgument(supplier, "supplier is null");

			return m_value;
	    }

		@Override
		public <X extends Throwable> T getOrThrow(Supplier<X> supplier) throws X {
	        Preconditions.checkNotNullArgument(supplier, "supplier is null");
			return m_value;
	    }

		@Override
		public <X extends Throwable> T getOrThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	    	return m_value;
	    }

		@Override
	    public Result<T> ifSuccessful(Consumer<? super T> action) {
	        Preconditions.checkNotNullArgument(action, "action is null");

            action.accept(m_value);
	        return this;
	    }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
	        
			try {
				return success(mapper.apply(m_value));
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}

		@Override
		public Result<T> filter(Predicate<? super T> predicate) {
	        Preconditions.checkNotNullArgument(predicate, "predicate is null");

			try {
				return predicate.test(m_value) ? this : cancelled();
			}
			catch ( Exception e ) {
				return Result.failure(e);
			}
		}

		@Override @SuppressWarnings("unchecked")
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
	        
			try {
				return (Result<U>)mapper.apply(m_value);
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}

        @Override
        public String toString() {
            return String.format("Success(%s)", m_value);
        }
        
        @Override
        public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			else if ( obj == null  || obj.getClass() != Success.class ) {
				return false;
			}
			
			Success<?> other = (Success<?>)obj;
			return Objects.equals(m_value, other.m_value);
        }
        
        @Override
        public int hashCode() {
        	return Objects.hash(getClass(), m_value);
        }
	}
	
	private static class Cancelled<T> extends Result<T> {
        private static final Cancelled<?> INSTANCE = new Cancelled<>();
        
		private Cancelled() { }

		@Override
		public boolean isCancelled() {
			return true;
		}
		
		@Override
		public T get() throws CancellationException {
			throw new CancellationException();
		}

		@Override
		public <X extends Throwable> T getOrThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Preconditions.checkNotNullArgument(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(new CancellationException());
	    }

		@Override
	    public Result<T> ifCancelled(Runnable action) {
	        Preconditions.checkNotNullArgument(action, "action is null");

            action.run();
	        return this;
	    }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
	        
			return cancelled();
		}

		@Override
		public Result<T> filter(Predicate<? super T> predicate) {
	        Preconditions.checkNotNullArgument(predicate, "predicate is null");
			return cancelled();
		}
		
		@Override
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
			return cancelled();
		}

        @Override
        public String toString() {
            return "Cancelled";
        }
        
        @Override
        public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			else if ( obj == null  || obj.getClass() != Cancelled.class ) {
				return false;
			}
			
			return true;
        }
        
        @Override
        public int hashCode() {
        	return Objects.hash(getClass());
        }
	}
	
	private static class Failure<T> extends Result<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public boolean isFailed() {
			return true;
		}
		
		@Override
		public T get() throws ExecutionException {
			throw new ExecutionException(m_cause);
		}

		@Override
		public <X extends Throwable> T getOrThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Preconditions.checkNotNullArgument(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(m_cause);
	    }

        @Override
        public Throwable getCause() {
            return m_cause;
        }

        @Override
        public Result<T> ifFailed(Consumer<? super Throwable> action) {
            Preconditions.checkNotNullArgument(action, "action is null");

            action.accept(m_cause);
            return this;
        }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
	        
			return failure(m_cause);
		}

		@Override
		public Result<T> filter(Predicate<? super T> predicate) {
	        Preconditions.checkNotNullArgument(predicate, "predicate is null");
			return failure(m_cause);
		}
		
		@Override
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
			return failure(m_cause);
		}

        @Override
        public String toString() {
            return String.format("Failure(%s)", m_cause);
        }
        
        @Override
        public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			else if ( obj == null  || obj.getClass() != Failure.class ) {
				return false;
			}
			
			Failure<?> other = (Failure<?>)obj;
			return Objects.equals(m_cause, other.m_cause);
        }
        
        @Override
        public int hashCode() {
        	return Objects.hash(getClass(), m_cause);
        }
	}
}
