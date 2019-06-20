package utils.func;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.vavr.Value;
import io.vavr.collection.Iterator;
import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Result<T> implements Value<T> {
    public abstract Throwable getCause();
	public abstract <X extends Throwable> T getOrElseThrow(
							Function<? super Throwable, X> exceptionProvider) throws X;
	public abstract <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper);
    public abstract Result<Throwable> forEachOrException(Consumer<T> c);
	
	private Result() { }
	
	public static <T> Result<T> of(T result) {
		return result != null ? some(result) : none();
	}
	
	public static <T> Result<T> some(T result) {
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
		return this instanceof Success;
	}
	
	@Override
	public boolean isEmpty() {
		return this instanceof None;
	}
	
	@Override
	public boolean isAsync() {
		return false;
	}

	@Override
	public boolean isLazy() {
		return false;
	}

	public boolean isFailure() {
		return this instanceof Failure;
	}

	@Override
	public boolean isSingleValued() {
		return true;
	}
	
	@Override
	public T getOrNull() {
		return isSuccess() ? get() : null;
	}
	
	@Override
    public T getOrElse(T other) {
		return isSuccess() ? get() : other;
    }
	
	@Override
	public T getOrElse(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        
		return isSuccess() ? get() : supplier.get();
    }

	@Override
	public <X extends Throwable> T getOrElseThrow(Supplier<X> supplier) throws X {
        Objects.requireNonNull(supplier, "supplier is null");

        if ( isSuccess() ) {
        	return get();
        }
        else {
            throw supplier.get();
        }
    }

    @SuppressWarnings("unchecked")
    public Result<T> orElse(Result<? extends T> other) {
        Objects.requireNonNull(other, "other is null");
		return isSuccess() ? this : (Result<T>)other;
    }
	
    @Override
    public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        
        return flatMap(t -> {
        	try {
				return of(mapper.apply(t));
			}
			catch ( Exception e ) {
				return failure(e);
			}
        });
    }
	
	public Result<T> filter(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        
		return flatMap(t -> {
			try {
				return predicate.test(t) ? this : Result.none();
			}
			catch ( Throwable e) {
				return Result.failure(e);
			}
		});
	}
	
    public Result<String> forEachOrFail(Consumer<T> c) {
		Result<Throwable> r = forEachOrException(c);
		return r.map(e -> e.getMessage());
    }
	
    public Result<T> onEmpty(Runnable action) {
        Objects.requireNonNull(action, "action is null");
        
        if ( isEmpty() ) {
            action.run();
        }
        return this;
    }
    
    public Result<T> onFailure(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action, "action is null");
        
        if ( isFailure() ) {
            action.accept(getCause());
        }
        return this;
    }

/*
    
	public abstract void forEach(Consumer<T> effect);
	public abstract void forEachOrThrow(Consumer<T> effect);
	
	public static <T,U> Tuple2<Result<T>,Result<U>> unfold(Result<Tuple2<T,U>> r) {
		if ( r.isSuccess() ) {
			Tuple2<T,U> t = r.get();
			return new Tuple2<>(Result.of(t._1), Result.of(t._2));
		}
		else if ( r.isEmpty() ) {
			return new Tuple2<>(none(), none());
		}
		else {
			Throwable error = r.getFailureCause();
			return new Tuple2<>(Result.failure(error), Result.failure(error));
		}
	}
*/
	
	private static class Success<T> extends Result<T> {
		private final T m_value;
		
		private Success(T result) {
			m_value = result;
		}
		
		@Override
		public T get() {
			return m_value;
		}

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	    	return m_value;
	    }

        @Override
        public Throwable getCause() {
            throw new IllegalStateException("getCause on Success");
        }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
	        Objects.requireNonNull(mapper, "mapper is null");
	        
			try {
				return of(mapper.apply(m_value));
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}

		@Override @SuppressWarnings("unchecked")
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
	        Objects.requireNonNull(mapper, "mapper is null");
	        
			try {
				return (Result<U>)mapper.apply(m_value);
			}
			catch ( Exception e ) {
				return failure(e);
			}
		}

	    @Override
	    public Result<Throwable> forEachOrException(Consumer<T> c) {
	    	c.accept(m_value);
			return Result.none();
	    }
		
	    @Override
	    public Result<T> peek(Consumer<? super T> action) {
	        Objects.requireNonNull(action, "action is null");

            action.accept(get());
	        return this;
	    }

		@Override
		public Iterator<T> iterator() {
			return Iterator.of(m_value);
		}

        @Override
        public String stringPrefix() {
            return "Success";
        }

        @Override
        public String toString() {
            return stringPrefix() + "(" + m_value + ")";
        }
	}
	
	private static class None<T> extends Result<T> {
        private static final None<?> INSTANCE = new None<>();
        
		private None() { }
		
		@Override
		public T get() {
			throw new IllegalStateException("get() on None");
		}

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Objects.requireNonNull(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(new IllegalStateException("get() on None"));
	    }

        @Override
        public Throwable getCause() {
            throw new IllegalStateException("getCause on None");
        }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
			return none();
		}
		
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
			return none();
		}

	    @Override
	    public Result<Throwable> forEachOrException(Consumer<T> c) {
			return Result.none();
	    }
		
	    @Override
	    public Result<T> peek(Consumer<? super T> action) {
	        Objects.requireNonNull(action, "action is null");

	        return this;
	    }

		@Override
		public Iterator<T> iterator() {
			return Iterator.empty();
		}

        @Override
        public String stringPrefix() {
            return "None";
        }

        @Override
        public String toString() {
            return stringPrefix();
        }
	}
	
	private static class Failure<T> extends Result<T> {
		private final Throwable m_cause;
		
		private Failure(Throwable cause) {
			m_cause = cause;
		}
		
		public boolean isFailure() {
			return true;
		}
		
		@Override
		public T get() {
			throw Throwables.toRuntimeException(m_cause);
		}

		@Override
		public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> exceptionProvider) throws X {
	        Objects.requireNonNull(exceptionProvider, "exceptionProvider is null");

            throw exceptionProvider.apply(m_cause);
	    }

        @Override
        public Throwable getCause() {
            return m_cause;
        }

		@Override
		public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
			return failure(m_cause);
		}
		
		public <U> Result<U> flatMap(Function<? super T, ? extends Result<? extends U>> mapper) {
			return failure(m_cause);
		}

	    @Override
	    public Result<Throwable> forEachOrException(Consumer<T> c) {
			return Result.of(m_cause);
	    }
		
	    @Override
	    public Result<T> peek(Consumer<? super T> action) {
	        Objects.requireNonNull(action, "action is null");

	        return this;
	    }

		@Override
		public Iterator<T> iterator() {
			return Iterator.empty();
		}

        @Override
        public String stringPrefix() {
            return "Failure";
        }

        @Override
        public String toString() {
            return stringPrefix() + "(" + m_cause + ")";
        }
	}
}
