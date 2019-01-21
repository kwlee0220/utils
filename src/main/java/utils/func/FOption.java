package utils.func;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.vavr.CheckedConsumer;
import utils.Unchecked.CheckedFunction;
import utils.Unchecked.CheckedSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class FOption<T> {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static final FOption EMPTY = new FOption(null, false);
	
	private final T m_value;
	private final boolean m_present;
	
	public static <T> FOption<T> of(T value) {
		return new FOption<>(value, true);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> FOption<T> empty() {
		return (FOption<T>)EMPTY;
	}
	
	public static <T> FOption<T> ofNullable(T value) {
		return value != null ? of(value) : empty();
	}
	
	public static <T> FOption<T> from(Optional<T> opt) {
		return opt.isPresent() ? of(opt.get()) : empty();
	}
	
	public static <T> FOption<T> when(boolean flag, T value) {
		return flag ? of(value) : empty();
	}
	
	@SuppressWarnings("unchecked")
	public static <T> FOption<T> narrow(FOption<? extends T> opt) {
		return (FOption<T>)opt;
	}
	
	private FOption(T value, boolean present) {
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
			throw new NoSuchValueException();
		}
	}
	
	public T getOrNull() {
		if ( m_present ) {
			return m_value;
		}
		else {
			return null;
		}
	}
	
	public T getOrElse(T elseValue) {
		if ( m_present ) {
			return m_value;
		}
		else {
			return elseValue;
		}
	}
	
	public T getOrElse(Supplier<? extends T> elseSupplier) {
		if ( m_present ) {
			return m_value;
		}
		else {
			return elseSupplier.get();
		}
	}
	
	public T getOrElseTE(CheckedSupplier<? extends T> elseSupplier) throws Throwable {
		if ( m_present ) {
			return m_value;
		}
		else {
			return elseSupplier.get();
		}
	}
	
	public <X extends Throwable> T getOrElseThrow(Supplier<X> thrower) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			throw thrower.get();
		}
	}
	
	public FOption<T> ifPresent(Consumer<? super T> effect) {
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> ifPresentTE(CheckedConsumer<? super T> effect) throws Throwable {
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> ifAbsent(Runnable orElse) {
		if ( !m_present ) {
			orElse.run();
		}
		
		return this;
	}
	
	public FOption<T> ifPresentOrElse(Consumer<T> present, Runnable orElse) {
		if ( m_present ) {
			present.accept(m_value);
		}
		else {
			orElse.run();
		}
		
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public FOption<T> filter(Predicate<? super T> pred) {
		if ( m_present ) {
			return (pred.test(m_value)) ? this : (FOption<T>)EMPTY;
		}
		else {
			return this;
		}
	}
	
	@SuppressWarnings("unchecked")
	public <S> FOption<S> map(Function<? super T,? extends S> mapper) {
		return (m_present) ? new FOption<>(mapper.apply(m_value), true)
							: (FOption<S>)EMPTY;
	}
	@SuppressWarnings("unchecked")
	public <S> FOption<S> mapTE(CheckedFunction<? super T,? extends S> mapper)
		throws Throwable {
		return (m_present) ? new FOption<>(mapper.apply(m_value), true)
							: (FOption<S>)EMPTY;
	}
	
	public <S> S map(S src, BiFunction<S,T,S> mapper) {
		Objects.requireNonNull(mapper, "mapper BiFunction");
		
		return (m_present) ? mapper.apply(src, m_value) : src;
	}
	
	@SuppressWarnings("unchecked")
	public <S> FOption<S> flatMap(Function<? super T,FOption<? extends S>> mapper) {
		if ( m_present ) {
			return (FOption<S>)mapper.apply(m_value);
		}
		else {
			return (FOption<S>)EMPTY;
		}
	}
	
	public FOption<T> orElse(FOption<T> orElse) {
		return (m_present) ? this : orElse;
	}
	
	public FOption<T> orElse(Supplier<FOption<T>> orElseSupplier) {
		return (m_present) ? this : orElseSupplier.get();
	}
	
	public <X extends Throwable> FOption<T> orElseThrow(Supplier<X> errorSupplier)
		throws X {
		if ( m_present ) {
			return this;
		}
		else {
			throw errorSupplier.get();
		}
	}
	
	@Override
	public String toString() {
		return String.format("FOption(%s)", m_present ? m_value : "none");
	}
	
	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		else if ( o == null || !FOption.class.equals(o.getClass()) ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		FOption<T> other = (FOption<T>)o;
		if ( m_present != other.m_present ) {
			return false;
		}
		if ( !m_present ) {
			return true;
		}
		if ( m_value == null ) {
			return other.m_value == null;
		}
		else {
			return m_value.equals(other.m_value);
		}
	}
}
