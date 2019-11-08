package utils.func;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import utils.Throwables;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Result<T> {
	private Result() { }
	
	public static <T> Result<T> success(T result) {
		return new Success<>(result);
	}
	
    @SuppressWarnings("unchecked")
	public static <T> Result<T> none() {
        return (None<T>) None.INSTANCE;
    }
	
	public static <T> Result<T> failure(Throwable cause) {
		return new Failure<>(cause);
	}
	
	public boolean isSuccess() {
		return false;
	}
	
	public boolean isNone() {
		return false;
	}
	
	public boolean isFailure() {
		return false;
	}
	
    public T get() {
    	throw new IllegalStateException("get() on " + this);
    }
	
	public T getOrNull() {
		return null;
	}
	
    public T getOrElse(T other) {
		return other;
    }
    
	public abstract <X extends Throwable> T getOrElseThrow(
							Function<? super Throwable, X> exceptionProvider) throws X;
	
	public T getOrElse(Supplier<? extends T> supplier) {
        Utilities.checkNotNullArgument(supplier, "supplier is null");
        
		return supplier.get();
    }

	public <X extends Throwable> T getOrElseThrow(Supplier<X> supplier) throws X {
        Utilities.checkNotNullArgument(supplier, "supplier is null");

        throw supplier.get();
    }

    @SuppressWarnings("unchecked")
    public Result<T> orElse(Result<? extends T> other) {
        Utilities.checkNotNullArgument(other, "other is null");
		return isSuccess() ? this : (Result<T>)other;
    }

    public Throwable getCause() {
        throw new IllegalStateException("getCause on " + this);
    }
	
    public Result<T> onSuccess(Consumer<? super T> action) {
        Utilities.checkNotNullArgument(action, "action is null");
        return this;
    }

    public Result<T> onNone(Runnable action) {
        Utilities.checkNotNullArgument(action, "action is null");
        return this;
    }
    
    public Result<T> onFailure(Consumer<? super Throwable> action) {
        Utilities.checkNotNullArgument(action, "action is null");
        return this;
    }
	
    public abstract <U> Result<U> map(Function<? super T, ? extends U> mapper);
	public abstract Result<T> filter(Predicate<T> predicate);
	public abstract <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper);
	
	private static class Success<T> extends Result<T> {
		private final T m_value;
		
		private Success(T result) {
			m_value = result;
		}

		@Override
		public boolean isSuccess() {
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
	        Utilities.checkNotNullArgument(supplier, "supplier is null");

			return m_value;
	    }

		@Override
		public <X extends Throwable> T getOrElseThrow(Supplier<X> supplier) throws X {
	        Utilities.checkNotNullArgument(supplier, "supplier is null");
			return m_value;
	    }

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	    	return m_value;
	    }

		@Override
	    public Result<T> onSuccess(Consumer<? super T> action) {
	        Utilities.checkNotNullArgument(action, "action is null");

            action.accept(m_value);
	        return this;
	    }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
			try {
				return success(mapper.apply(m_value));
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}

		@Override
		public Result<T> filter(Predicate<T> predicate) {
	        Utilities.checkNotNullArgument(predicate, "predicate is null");

			try {
				return predicate.test(m_value) ? this : none();
			}
			catch ( Throwable e) {
				return Result.failure(e);
			}
		}

		@Override @SuppressWarnings("unchecked")
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
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
	}
	
	private static class None<T> extends Result<T> {
        private static final None<?> INSTANCE = new None<>();
        
		private None() { }

		@Override
		public boolean isNone() {
			return true;
		}

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Utilities.checkNotNullArgument(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(new IllegalStateException("get() on None"));
	    }

		@Override
	    public Result<T> onNone(Runnable action) {
	        Utilities.checkNotNullArgument(action, "action is null");

            action.run();
	        return this;
	    }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
			return none();
		}

		@Override
		public Result<T> filter(Predicate<T> predicate) {
	        Utilities.checkNotNullArgument(predicate, "predicate is null");
			return none();
		}
		
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
			return none();
		}

        @Override
        public String toString() {
            return "None";
        }
	}
	
	private static class Failure<T> extends Result<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}

		@Override
		public boolean isFailure() {
			return true;
		}
		
		@Override
		public T get() {
			Throwables.sneakyThrow(m_cause);
			throw new AssertionError();
		}

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Utilities.checkNotNullArgument(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(m_cause);
	    }

        @Override
        public Throwable getCause() {
            return m_cause;
        }

        @Override
        public Result<T> onFailure(Consumer<? super Throwable> action) {
            Utilities.checkNotNullArgument(action, "action is null");

            action.accept(m_cause);
            return this;
        }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
	        
			return failure(m_cause);
		}

		@Override
		public Result<T> filter(Predicate<T> predicate) {
	        Utilities.checkNotNullArgument(predicate, "predicate is null");
			return failure(m_cause);
		}
		
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Utilities.checkNotNullArgument(mapper, "mapper is null");
			return failure(m_cause);
		}

        @Override
        public String toString() {
            return String.format("Failure(%s)", m_cause);
        }
	}
}
