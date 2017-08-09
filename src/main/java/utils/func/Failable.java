package utils.func;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Failable<T> {
	public abstract T get();
	public abstract T orElse(T defValue);
	public abstract T orElse(Supplier<T> defValue);
	public abstract <U> Failable<U> map(Function<T,U> trans);
	
	private Failable() { }
	
	public boolean isSuccess() {
		return this instanceof Success;
	}
	
	public boolean isFailed() {
		return this instanceof Failure;
	}
	
	public boolean isEmpty() {
		return this instanceof Empty;
	}
	
	public Throwable getFailureCause() {
		throw new IllegalStateException("not Failure: this=" + this);
	}
	
	public static <T> Failable<T>  success(T result) {
		return new Success<>(result);
	}
	
	public static <T> Failable<T>  failure(Throwable cause) {
		return new Failure<>(cause);
	}
	
	public static <T> Failable<T>  empty() {
		return new Empty<>();
	}
	
	private static class Success<T> extends Failable<T> {
		private final T m_result;
		
		private Success(T result) {
			m_result = result;
		}
		
		@Override
		public T get() {
			return m_result;
		}

		@Override
		public T orElse(T defValue) {
			return m_result;
		}

		@Override
		public T orElse(Supplier<T> defValue) {
			return m_result;
		}

		@Override
		public <U> Failable<U> map(Function<T, U> trans) {
			try {
				return success(trans.apply(m_result));
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}
		
		@Override
		public String toString() {
			return String.format("Success(%s)", m_result);
		}
	}
	
	private static class Failure<T> extends Failable<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}
		
		@Override
		public T get() {
			if ( m_cause instanceof RuntimeException ) {
				throw (RuntimeException)m_cause;
			}
			else {
				throw new RuntimeException(m_cause);
			}
		}

		@Override
		public T orElse(T defValue) {
			return defValue;
		}

		@Override
		public T orElse(Supplier<T> defValue) {
			return defValue.get();
		}

		@Override
		public Throwable getFailureCause() {
			return m_cause;
		}

		@Override
		public <U> Failable<U> map(Function<T, U> trans) {
			return failure(m_cause);
		}
		
		@Override
		public String toString() {
			return String.format("Failure(%s)", m_cause);
		}
	}
	
	private static class Empty<T> extends Failable<T> {
		private Empty() { }
		
		@Override
		public T get() {
			throw new IllegalStateException("result is not available");
		}

		@Override
		public T orElse(T defValue) {
			return defValue;
		}

		@Override
		public T orElse(Supplier<T> defValue) {
			return defValue.get();
		}

		@Override
		public <U> Failable<U> map(Function<T, U> trans) {
			return empty();
		}
		
		@Override
		public String toString() {
			return "Empty";
		}
	}
}
