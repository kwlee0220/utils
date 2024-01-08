package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AsyncResult<T> {
	public static <T> AsyncResult<T> completed(T value) {
		return new Completed<>(value);
	}
	
	public static <T> AsyncResult<T> failed(Throwable cause) {
		return new Failed<>(cause);
	}
	
	public static <T> AsyncResult<T> cancelled() {
		return new Cancelled<>();
	}
	
	public static <T> AsyncResult<T> running() {
		return new Running<>();
	}
	
	public abstract AsyncState getState();
	
	public boolean isCompleted() {
		return getState() == AsyncState.COMPLETED;
	}

	public boolean isFailed() {
		return getState() == AsyncState.FAILED;
	}
	
	public boolean isCancelled() {
		return getState() == AsyncState.CANCELLED;
	}
	
	public boolean isRunning() {
		return getState() == AsyncState.RUNNING;
	}
	
	public boolean isFinished() {
		return getState() != AsyncState.RUNNING;
	}
	
	public abstract T get() throws ExecutionException, CancellationException, TimeoutException;

	public T getUnchecked() {
		throw new IllegalStateException("not completed state");
	}
	
	public Throwable getFailureCause() {
		throw new IllegalStateException("not FAILED state: state=" + getState());
	}
	
	public T getOrNull() {
		return null;
	}
	
    public T getOrElse(T other) {
    	return other;
    }
	
	public T getOrElse(Supplier<? extends T> supplier) {
        Utilities.checkNotNullArgument(supplier, "supplier is null");

		return supplier.get();
    }

	public abstract AsyncResult<T> filter(Predicate<? super T> predicate);
    public abstract <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper);
	
    public AsyncResult<T> ifCompleted(Consumer<? super T> handler) {
		return this;
    }
	
    public AsyncResult<T> ifFailed(Consumer<Throwable> handler) {
		return this;
    }
	
    public AsyncResult<T> ifCancelled(Runnable handler) {
		return this;
    }
    
    @SuppressWarnings("unchecked")
	public static <T> AsyncResult<T> narrow(AsyncResult<? extends T> result) {
    	return (AsyncResult<T>)result;
    }
	
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
	        Utilities.checkNotNullArgument(predicate, "predicate is null");
			return predicate.test(m_value) ? this : cancelled();
		}

		@Override
	    public <S> AsyncResult<S> map(Function<? super T, ? extends S> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
			return completed(mapper.apply(m_value));
	    }

		@Override
	    public AsyncResult<T> ifCompleted(Consumer<? super T> handler) {
	        Utilities.checkNotNullArgument(handler, "handler is null");

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
	        Utilities.checkNotNullArgument(handler, "handler is null");

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
	        Utilities.checkNotNullArgument(handler, "handler is null");

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
	
	public static class Running<T> extends AsyncResult<T> {
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
