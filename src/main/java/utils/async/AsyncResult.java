package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import utils.Preconditions;

/**
 * 비동기 연산의 결과를 상태와 함께 표현하는 불변 객체.
 * <p>
 * 결과는 성공({@link Completed}), 실패({@link Failed}), 취소({@link Cancelled}),
 * 수행 중({@link Running}) 중 하나의 상태를 갖는다.
 *
 * @param <T>	결과 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AsyncResult<T> {
	/**
	 * 하위 클래스를 위한 기본 생성자.
	 */
	public AsyncResult() { }

	/**
	 * 성공적으로 완료된 비동기 결과를 생성한다.
	 *
	 * @param <T>	결과 값의 타입.
	 * @param value	결과 값. {@code null}도 허용된다.
	 * @return	{@link AsyncState#COMPLETED} 상태의 결과 객체.
	 */
	public static <T> AsyncResult<T> completed(T value) {
		return new Completed<>(value);
	}
	
	/**
	 * 오류로 종료된 비동기 결과를 생성한다.
	 *
	 * @param <T>	결과 값의 타입.
	 * @param cause	실패 원인 예외.
	 * @return	{@link AsyncState#FAILED} 상태의 결과 객체.
	 */
	public static <T> AsyncResult<T> failed(Throwable cause) {
		return new Failed<>(cause);
	}
	
	/**
	 * 취소된 비동기 결과를 생성한다.
	 *
	 * @param <T>	결과 값의 타입.
	 * @return	{@link AsyncState#CANCELLED} 상태의 결과 객체.
	 */
	public static <T> AsyncResult<T> cancelled() {
		return new Cancelled<>();
	}
	
	/**
	 * 아직 수행 중인(종료되지 않은) 비동기 결과를 생성한다.
	 *
	 * @param <T>	결과 값의 타입.
	 * @return	{@link AsyncState#RUNNING} 상태의 결과 객체.
	 */
	public static <T> AsyncResult<T> running() {
		return new Running<>();
	}
	
	/**
	 * 결과의 상태를 반환한다.
	 *
	 * @return	{@link AsyncState#COMPLETED}, {@link AsyncState#FAILED}, {@link AsyncState#CANCELLED},
	 * 		{@link AsyncState#RUNNING} 중 하나.
	 */
	public abstract AsyncState getState();
	
	/**
	 * 성공적으로 완료된 결과인지 여부를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#COMPLETED}이면 {@code true}.
	 */
	public boolean isCompleted() {
		return getState() == AsyncState.COMPLETED;
	}

	/**
	 * 오류로 종료된 결과인지 여부를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#FAILED}이면 {@code true}.
	 */
	public boolean isFailed() {
		return getState() == AsyncState.FAILED;
	}
	
	/**
	 * 취소된 결과인지 여부를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#CANCELLED}이면 {@code true}.
	 */
	public boolean isCancelled() {
		return getState() == AsyncState.CANCELLED;
	}
	
	/**
	 * 아직 수행 중인 결과인지 여부를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#RUNNING}이면 {@code true}.
	 */
	public boolean isRunning() {
		return getState() == AsyncState.RUNNING;
	}
	
	/**
	 * 종료된 결과인지 여부를 반환한다. {@link AsyncState#RUNNING}이 아닌 모든 상태를 종료로 본다.
	 *
	 * @return	상태가 {@link AsyncState#RUNNING}이 아니면 {@code true}.
	 */
	public boolean isFinished() {
		return getState() != AsyncState.RUNNING;
	}
	
	/**
	 * 결과 값을 반환한다. 성공 상태가 아니면 상태에 따라 예외를 던진다.
	 *
	 * @return	결과 값.
	 * @throws ExecutionException	{@link AsyncState#FAILED} 상태인 경우. 실패 원인을 cause로 갖는다.
	 * @throws CancellationException	{@link AsyncState#CANCELLED} 상태인 경우.
	 * @throws TimeoutException	{@link AsyncState#RUNNING} 상태인 경우.
	 */
	public abstract T get() throws ExecutionException, CancellationException, TimeoutException;

	/**
	 * 결과 값을 검사 예외 없이 반환한다.
	 *
	 * @return	결과 값.
	 * @throws IllegalStateException	{@link AsyncState#COMPLETED} 상태가 아닌 경우.
	 */
	public T getUnchecked() {
		throw new IllegalStateException("not completed state");
	}
	
	/**
	 * 실패 원인 예외를 반환한다.
	 * <p>
	 * <b>주의:</b> {@link AsyncState#RUNNING} 상태에서는 예외를 던지지 않고 새 {@link TimeoutException}
	 * 객체를 반환한다.
	 *
	 * @return	실패 원인 예외.
	 * @throws IllegalStateException	{@link AsyncState#FAILED} 또는 {@link AsyncState#RUNNING} 상태가 아닌 경우.
	 */
	public Throwable getFailureCause() {
		throw new IllegalStateException("not FAILED state: state=" + getState());
	}
	
	/**
	 * 성공 상태이면 결과 값을, 그 밖의 상태이면 {@code null}을 반환한다.
	 *
	 * @return	결과 값 또는 {@code null}.
	 */
	public T getOrNull() {
		return null;
	}
	
    /**
     * 성공 상태이면 결과 값을, 그 밖의 상태이면 {@code other}를 반환한다.
     *
     * @param other	성공 상태가 아닐 때 반환할 값.
     * @return	결과 값 또는 {@code other}.
     */
    public T getOrElse(T other) {
    	return other;
    }
	
	/**
	 * 성공 상태이면 결과 값을, 그 밖의 상태이면 {@code supplier}가 만든 값을 반환한다.
	 * 성공 상태에서는 {@code supplier}를 검사하거나 호출하지 않는다.
	 *
	 * @param supplier	성공 상태가 아닐 때 반환할 값을 만드는 함수.
	 * @return	결과 값 또는 {@code supplier}가 만든 값.
	 * @throws IllegalArgumentException	성공 상태가 아니고 {@code supplier}가 {@code null}인 경우.
	 */
	public T getOrElse(Supplier<? extends T> supplier) {
        Preconditions.checkNotNullArgument(supplier, "supplier is null");

		return supplier.get();
    }

	/**
	 * 성공 결과 값을 주어진 조건으로 거른다.
	 * <p>
	 * 성공 상태에서 조건을 만족하지 않으면 취소된 결과를 반환한다.
	 * 실패·취소·수행 중 상태이면 같은 상태의 결과를 반환하고 조건은 호출하지 않는다.
	 *
	 * @param predicate	조건.
	 * @return	걸러진 결과.
	 * @throws IllegalArgumentException	성공 상태이고 {@code predicate}가 {@code null}인 경우.
	 */
	public abstract AsyncResult<T> filter(Predicate<? super T> predicate);
    /**
     * 성공 결과 값을 주어진 함수로 변환한다.
     * <p>
     * 실패·취소·수행 중 상태이면 같은 상태의 결과를 반환하고 함수는 호출하지 않는다.
     *
     * @param <S>	변환된 값의 타입.
     * @param mapper	변환 함수.
     * @return	변환된 결과.
     * @throws IllegalArgumentException	성공 상태이고 {@code mapper}가 {@code null}인 경우.
     */
    public abstract <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper);
	
    /**
     * 성공 상태이면 결과 값으로 {@code handler}를 호출한다. 그 밖의 상태에서는 아무것도 하지 않는다.
     *
     * @param handler	결과 값을 받을 함수.
     * @return	이 객체.
     * @throws IllegalArgumentException	성공 상태이고 {@code handler}가 {@code null}인 경우.
     */
    public AsyncResult<T> ifCompleted(Consumer<? super T> handler) {
		return this;
    }
	
    /**
     * 실패 상태이면 실패 원인으로 {@code handler}를 호출한다. 그 밖의 상태에서는 아무것도 하지 않는다.
     *
     * @param handler	실패 원인을 받을 함수.
     * @return	이 객체.
     * @throws IllegalArgumentException	실패 상태이고 {@code handler}가 {@code null}인 경우.
     */
    public AsyncResult<T> ifFailed(Consumer<Throwable> handler) {
		return this;
    }
	
    /**
     * 취소 상태이면 {@code handler}를 호출한다. 그 밖의 상태에서는 아무것도 하지 않는다.
     *
     * @param handler	취소 시 호출할 작업.
     * @return	이 객체.
     * @throws IllegalArgumentException	취소 상태이고 {@code handler}가 {@code null}인 경우.
     */
    public AsyncResult<T> ifCancelled(Runnable handler) {
		return this;
    }
    
    /**
     * 결과 값의 타입을 상위 타입으로 바꾼다. 객체는 그대로이고 형변환만 한다.
     *
     * @param <T>	바꿀 결과 값 타입.
     * @param result	대상 결과.
     * @return	형변환된 {@code result}.
     */
    @SuppressWarnings("unchecked")
	public static <T> AsyncResult<T> narrow(AsyncResult<? extends T> result) {
    	return (AsyncResult<T>)result;
    }
	
	/**
	 * 성공적으로 완료된 결과.
	 * <p>
	 * <b>주의:</b> {@link #equals(Object)}는 값이 {@code null}이면 {@link NullPointerException}을 던진다.
	 *
	 * @param <T>	결과 값의 타입.
	 */
	public static class Completed<T> extends AsyncResult<T> {
		private final T m_value;
		
		private Completed(T value) {
			m_value = value;
		}

		@Override
		public AsyncState getState() {
			return AsyncState.COMPLETED;
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
	    public T getOrElse(T other) {
			return m_value;
	    }
		
		@Override
		public T getOrElse(Supplier<? extends T> supplier) {
			return m_value;
	    }

		@Override
		public AsyncResult<T> filter(Predicate<? super T> predicate) {
	        Preconditions.checkNotNullArgument(predicate, "predicate is null");
			return predicate.test(m_value) ? this : cancelled();
		}

		@Override
	    public <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper) {
	        Preconditions.checkNotNullArgument(mapper, "mapper is null");
	        
			return completed(mapper.apply(m_value));
	    }

		@Override
	    public AsyncResult<T> ifCompleted(Consumer<? super T> handler) {
	        Preconditions.checkNotNullArgument(handler, "handler is null");

			handler.accept(m_value);
			return this;
	    }
	    
	    @Override
	    public String toString() {
			return String.format("completed(%s)", m_value);
	    }
		
		@Override
		public int hashCode() {
			return Objects.hash(getState(), m_value);
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Completed.class ) {
				return false;
			}
			
			@SuppressWarnings("unchecked")
			Completed<T> other = (Completed<T>)obj;
			return m_value.equals(other.m_value);
		}
	}
	
	/**
	 * 오류로 종료된 결과.
	 *
	 * @param <T>	결과 값의 타입.
	 */
	public static class Failed<T> extends AsyncResult<T> {
		private final Throwable m_cause;
		
		private Failed(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public AsyncState getState() {
			return AsyncState.FAILED;
		}

		@Override
		public T get() throws ExecutionException {
			throw new ExecutionException(m_cause);
		}

		@Override
		public Throwable getFailureCause() {
			return m_cause;
		}

		@Override
		public AsyncResult<T> filter(Predicate<? super T> predicate) {
			return failed(m_cause);
		}

		@Override
	    public <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper) {
			return failed(m_cause);
	    }

		@Override
	    public AsyncResult<T> ifFailed(Consumer<Throwable> handler) {
	        Preconditions.checkNotNullArgument(handler, "handler is null");

			handler.accept(m_cause);
			return this;
	    }
	    
	    @Override
	    public String toString() {
			return String.format("failed(%s)", m_cause);
	    }
		
		@Override
		public int hashCode() {
			return Objects.hash(getState(), m_cause);
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Failed.class ) {
				return false;
			}
			
			@SuppressWarnings("unchecked")
			Failed<T> other = (Failed<T>)obj;
			return m_cause.equals(other.m_cause);
		}
	}
	
	/**
	 * 취소된 결과.
	 *
	 * @param <T>	결과 값의 타입.
	 */
	public static class Cancelled<T> extends AsyncResult<T> {
		private Cancelled() { }

		@Override
		public AsyncState getState() {
			return AsyncState.CANCELLED;
		}

		@Override
		public T get() throws CancellationException {
			throw new CancellationException();
		}

		@Override
		public AsyncResult<T> filter(Predicate<? super T> predicate) {
			return cancelled();
		}

		@Override
	    public <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper) {
			return cancelled();
	    }

		@Override
	    public AsyncResult<T> ifCancelled(Runnable handler) {
	        Preconditions.checkNotNullArgument(handler, "handler is null");

			handler.run();
			return this;
	    }
	    
	    @Override
	    public String toString() {
			return String.format("cancelled");
	    }
		
		@Override
		public int hashCode() {
			return Objects.hash(getState());
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Cancelled.class ) {
				return false;
			}
			
			return true;
		}
    }
	
	/**
	 * 아직 종료되지 않은 결과.
	 * <p>
	 * {@link #get()}은 {@link TimeoutException}을 던지고, {@link #getFailureCause()}는 새
	 * {@link TimeoutException} 객체를 반환한다.
	 *
	 * @param <T>	결과 값의 타입.
	 */
	public static class Running<T> extends AsyncResult<T> {
		/**
		 * 수행 중 상태의 결과 객체를 생성한다. 보통은 {@link AsyncResult#running()}을 사용한다.
		 */
		public Running() { }
		@Override
		public AsyncState getState() {
			return AsyncState.RUNNING;
		}

		@Override
		public T get() throws TimeoutException {
			throw new TimeoutException();
		}

		@Override
		public Throwable getFailureCause() {
			return new TimeoutException();
		}

		@Override
		public AsyncResult<T> filter(Predicate<? super T> predicate) {
			return this;
		}

		@Override
		public <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper) {
			return AsyncResult.running();
		}
	    
	    @Override
	    public String toString() {
			return String.format("running");
	    }
		
		@Override
		public int hashCode() {
			return Objects.hash(getState());
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Running.class ) {
				return false;
			}
			
			return true;
		}
	}
}
