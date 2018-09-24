package utils.async;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import utils.async.AsyncExecution.State;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Result<T> {
	private T m_value;
	private Throwable m_cause;
	private State m_state;
	
	public static <T> Result<T> completed(T value) {
		Result<T> r = new Result<>();
		r.m_value = value;
		r.m_state = State.COMPLETED;
		return r;
	}
	
	public static <T> Result<T> failed(Throwable cause) {
		Result<T> r = new Result<>();
		r.m_cause = cause;
		r.m_state = State.FAILED;
		return r;
	}
	
	public static <T> Result<T> cancelled() {
		Result<T> r = new Result<>();
		r.m_state = State.CANCELLED;
		return r;
	}
	
	private Result(T value) {
		m_value = value;
		m_cause = null;
		m_state = State.COMPLETED;
	}
	
	private Result(Throwable cause) {
		m_value = null;
		m_cause = cause;
		m_state = State.FAILED;
	}
	
	private Result() { }
	
	public boolean isCompleted() {
		return m_state == State.COMPLETED;
	}

	public boolean isFailed() {
		return m_state == State.FAILED;
	}
	
	public boolean isCancelled() {
		return m_state == State.CANCELLED;
	}
	
	public T get() {
		switch ( m_state ) {
			case COMPLETED:
				return m_value;
			default:
				throw new IllegalStateException("not COMPLETED state: state=" + m_state);
		}
	}
	
	public Throwable getCause() {
		switch ( m_state ) {
			case FAILED:
				return m_cause;
			default:
				throw new IllegalStateException("not CANCELLED state: state=" + m_state);
		}
	}
	
	public T getOrNull() {
		switch ( m_state ) {
			case COMPLETED:
				return m_value;
			default:
				return null;
		}
	}
	
    public T getOrElse(T other) {
		switch ( m_state ) {
			case COMPLETED:
				return m_value;
			default:
				return other;
		}
    }
	
	public T getOrElse(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        
		switch ( m_state ) {
			case COMPLETED:
				return m_value;
			default:
				return supplier.get();
		}
    }

//	@Override
//	public <X extends Throwable> T getOrElseThrow(Supplier<X> supplier) throws X {
//        Objects.requireNonNull(supplier, "supplier is null");
//
//        if ( isSuccess() ) {
//        	return get();
//        }
//        else {
//            throw supplier.get();
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    public Result<T> orElse(Result<? extends T> other) {
//        Objects.requireNonNull(other, "other is null");
//		return isSuccess() ? this : (Result<T>)other;
//    }
	
    public <S> Result<S> map(Function<? super T, ? extends S> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        
		switch ( m_state ) {
			case COMPLETED:
				return completed(mapper.apply(m_value));
			case FAILED:
				return failed(m_cause);
			case CANCELLED:
				return cancelled();
			default:
				throw new AssertionError();
		}
    }
	
	public Result<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        
		switch ( m_state ) {
			case COMPLETED:
				return predicate.test(m_value) ? this : cancelled();
			case FAILED:
				return failed(m_cause);
			case CANCELLED:
				return cancelled();
			default:
				throw new AssertionError();
		}
	}
	
    public Result<T> ifCompleted(Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
		switch ( m_state ) {
			case COMPLETED:
				handler.accept(m_value);
			default:
		}
		
		return this;
    }
	
    public Result<T> ifFailed(Consumer<Throwable> handler) {
        Objects.requireNonNull(handler, "handler is null");
        
		switch ( m_state ) {
			case FAILED:
				handler.accept(m_cause);
			default:
		}
		
		return this;
    }
	
    public Result<T> ifCancelled(Runnable handler) {
        Objects.requireNonNull(handler, "handler is null");
        
		switch ( m_state ) {
			case CANCELLED:
				handler.run();
			default:
		}
		
		return this;
    }
    
    @Override
    public String toString() {
		switch ( m_state ) {
			case COMPLETED:
				return String.format("completed(%s)", m_value);
			case FAILED:
				return String.format("failed(%s)", m_cause);
			case CANCELLED:
				return String.format("cancelled", m_cause);
			default:
				throw new AssertionError();
		}
    }
}
