package utils.func;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class FOptional<T> {
	private final T m_value;
	private final boolean m_present;
	
	public static final <T> FOptional<T> some(T value) {
		return new FOptional<>(value, true);
	}
	
	public static final <T> FOptional<T> none() {
		return new FOptional<>(null, false);
	}
	
	public static final <T> FOptional<T> of(T value) {
		return new FOptional<>(value, value != null);
	}
	
	public static final <T> FOptional<T> from(Optional<T> opt) {
		return (opt.isPresent()) ? new FOptional<>(opt.get(), true) : new FOptional<>(null, false);
	}
	
	private FOptional(T value, boolean present) {
		m_value = value;
		m_present = present;
	}
	
	public boolean isPresent() {
		return m_present;
	}
	
	public boolean isAbsent() {
		return !m_present;
	}
	
	public T get() {
		if ( m_present ) {
			return m_value;
		}
		else {
			throw new IllegalStateException("valus is not present");
		}
	}
	
	public T getOrNull() {
		return ( m_present ) ? m_value : null;
	}
	
	public T getOrElse(T fallback) {
		return ( m_present ) ? m_value : fallback;
	}
	
	public T getOrElse(Supplier<? extends T> fallback) {
		return ( m_present ) ? m_value : fallback.get();
	}
	
	public <X extends Throwable> T getOrElseThrow(Supplier<X> fallback) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			throw fallback.get();
		}
	}
	
	@SuppressWarnings("unchecked")
	public FOptional<T> orElse(Supplier<? extends FOptional<? extends T>> supplier) {
		return ( m_present ) ? this : (FOptional<T>)supplier.get();
	}
	
	public FOptional<T> filter(Predicate<? super T> pred) {
		return (m_present) ? ((pred.test(m_value)) ? this : none()) : none();
	}
	
	public <U> FOptional<U> map(java.util.function.Function<? super T,U> mapper) {
		return (m_present) ? some(mapper.apply(m_value)) : none();
	}
	
	public FOptional<T> peek(final Consumer<? super T> consumer) {
		if ( m_present ) {
			consumer.accept(m_value);
		}
		
		return this;
	}
	
	public FOptional<T> ifPresent(final Consumer<? super T> consumer) {
		if ( m_present ) {
			consumer.accept(m_value);
		}
		
		return this;
	}
	
	public FOptional<T> ifAbsent(final Runnable consumer) {
		if ( m_present ) {
			consumer.run();
		}
		
		return this;
	}
	
	public <U> FOptional<U> flatMap(java.util.function.Function<? super T,FOptional<U>> mapper) {
		if ( m_present ) {
			FOptional<U> out = mapper.apply(m_value);
			return out.isPresent() ? some(out.m_value) : none();
		}
		else {
			return none();
		}
	}
	
	public <U> FOptional<U> flatMapOptional(java.util.function.Function<? super T,Optional<U>> mapper) {
		if ( m_present ) {
			Optional<U> out = mapper.apply(m_value);
			return out.isPresent() ? some(out.get()) : none();
		}
		else {
			return none();
		}
	}
}
