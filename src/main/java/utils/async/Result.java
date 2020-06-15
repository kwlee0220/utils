package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import utils.Utilities;
import utils.async.Execution.State;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Result<T> {
	public static <T> Result<T> completed(T value) {
		return new Completed<>(value);
	}
	
	public static <T> Result<T> failed(Throwable cause) {
		return new Failed<>(cause);
	}
	
	public static <T> Result<T> cancelled() {
		return new Cancelled<>();
	}
	
	public abstract State getState();
	
	public boolean isCompleted() {
		return getState() == State.COMPLETED;
	}

	public boolean isFailed() {
		return getState() == State.FAILED;
	}
	
	public boolean isCancelled() {
		return getState() == State.CANCELLED;
	}
	
	public abstract T get() throws ExecutionException, CancellationException;

	public T getUnchecked() {
		throw new IllegalStateException("not completed state");
	}
	
	public Throwable getCause() {
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

	
	public abstract Result<T> filter(Predicate<? super T> predicate);
    public abstract <S> Result<S> map(Function<? super T, ? extends S> mapper);
	
    public Result<T> ifCompleted(Consumer<? super T> handler) {
		return this;
    }
	
    public Result<T> ifFailed(Consumer<Throwable> handler) {
		return this;
    }
	
    public Result<T> ifCancelled(Runnable handler) {
		return this;
    }
    
    @SuppressWarnings("unchecked")
	public static <T> Result<T> narrow(Result<? extends T> result) {
    	return (Result<T>)result;
    }
	
	public static class Completed<T> extends Result<T> {
		private final T m_value;
		
		private Completed(T value) {
			m_value = value;
		}

		@Override
		public State getState() {
			return State.COMPLETED;
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
		public Result<T> filter(Predicate<? super T> predicate) {
	        Utilities.checkNotNullArgument(predicate, "predicate is null");
			return predicate.test(m_value) ? this : cancelled();
		}

		@Override
	    public <S> Result<S> map(Function<? super T, ? extends S> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
			return completed(mapper.apply(m_value));
	    }

		@Override
	    public Result<T> ifCompleted(Consumer<? super T> handler) {
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
	
	public static class Failed<T> extends Result<T> {
		private final Throwable m_cause;
		
		private Failed(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public State getState() {
			return State.FAILED;
		}

		@Override
		public T get() throws ExecutionException {
			throw new ExecutionException(m_cause);
		}

		@Override
		public Throwable getCause() {
			return m_cause;
		}

		@Override
		public Result<T> filter(Predicate<? super T> predicate) {
			return failed(m_cause);
		}

		@Override
	    public <S> Result<S> map(Function<? super T, ? extends S> mapper) {
			return failed(m_cause);
	    }

		@Override
	    public Result<T> ifFailed(Consumer<Throwable> handler) {
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
	
	public static class Cancelled<T> extends Result<T> {
		private Cancelled() { }

		@Override
		public State getState() {
			return State.CANCELLED;
		}

		@Override
		public T get() throws CancellationException {
			throw new CancellationException();
		}

		@Override
		public Result<T> filter(Predicate<? super T> predicate) {
			return cancelled();
		}

		@Override
	    public <S> Result<S> map(Function<? super T, ? extends S> mapper) {
			return cancelled();
	    }

		@Override
	    public Result<T> ifCancelled(Runnable handler) {
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
}
