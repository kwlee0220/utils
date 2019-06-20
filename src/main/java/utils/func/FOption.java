package utils.func;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.vavr.CheckedConsumer;
import utils.Utilities;
import utils.stream.FStream;
import utils.stream.FStreamable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class FOption<T> implements FStreamable<T> {
	@SuppressWarnings("rawtypes")
	private static final FOption EMPTY = new FOption<>(null, false);
	
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
		Utilities.checkNotNullArgument(opt, "Optional is null");
		
		return opt.isPresent() ? of(opt.get()) : empty();
	}
	
	public static <T> FOption<T> when(boolean flag, T value) {
		return flag ? of(value) : empty();
	}
	
	public static <T> FOption<T> when(boolean flag, Supplier<T> value) {
		return flag ? of(value.get()) : empty();
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
	
	public T getUnchecked() {
		return m_value;
	}
	
	public T getOrNull() {
		return (m_present) ? m_value : null;
	}
	
	public T getOrElse(T elseValue) {
		return (m_present) ? m_value : elseValue;
	}
	
	public T getOrElse(Supplier<? extends T> elseSupplier) {
		if ( m_present ) {
			return m_value;
		}
		else {
			Utilities.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}
	
	public <X extends Throwable> T getOrElseThrow(Supplier<X> thrower) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			Utilities.checkNotNullArgument(thrower, "throwerSupplier is null");
			throw thrower.get();
		}
	}
	
	public FOption<T> ifPresent(Consumer<? super T> effect) {
		Utilities.checkNotNullArgument(effect, "present consumer is null");
		
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> ifPresentTE(CheckedConsumer<? super T> effect) throws Throwable {
		Utilities.checkNotNullArgument(effect, "present consumer is null");
		
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> ifAbsent(Runnable orElse) {
		if ( !m_present ) {
			Utilities.checkNotNullArgument(orElse, "orElse is null");
			
			orElse.run();
		}
		
		return this;
	}
	
	public FOption<T> ifPresentOrElse(Consumer<T> present, Runnable orElse) {
		if ( m_present ) {
			present.accept(m_value);
		}
		else {
			Utilities.checkNotNullArgument(orElse, "orElse is null");
			
			orElse.run();
		}
		
		return this;
	}
	
	public FOption<T> filter(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "Predicate is null");
		
		if ( m_present ) {
			return (pred.test(m_value)) ? this : empty();
		}
		else {
			return this;
		}
	}

	public boolean test(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "Predicate is null");
		
		return m_present && pred.test(m_value);
	}
	
	public <S> FOption<S> map(Function<? super T,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}
	
	public <S,X extends Throwable> FOption<S> mapTE(CheckedFunctionX<? super T,? extends S,X> mapper) throws X {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}
	
	public <S> S transform(S src, BiFunction<S,T,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper BiFunction");
		
		return (m_present) ? mapper.apply(src, m_value) : src;
	}
	
	public <S> FOption<S> flatMap(Function<? super T,FOption<S>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? mapper.apply(m_value) : empty();
	}
	
	public <S> FOption<S> flatMapTry(Function<? super T,Try<S>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? mapper.apply(m_value).toFOption() : empty();
	}
	
	public FOption<T> orElse(FOption<T> orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			Utilities.checkNotNullArgument(orElse, "orElse is null");
			return orElse;
		}
	}
	
	public FOption<T> orElse(Supplier<FOption<T>> orElseSupplier) {
		if ( m_present ) {
			return this;
		}
		else {
			Utilities.checkNotNullArgument(orElseSupplier, "orElseSupplier is null");
			return orElseSupplier.get();
		}
	}
	
	public <X extends Throwable> FOption<T> orElseThrow(Supplier<X> errorSupplier)
		throws X {
		if ( m_present ) {
			return this;
		}
		else {
			Utilities.checkNotNullArgument(errorSupplier, "errorSupplier is null");
			throw errorSupplier.get();
		}
	}

	@Override
	public FStream<T> fstream() {
		return new FStreamImpl();
	}
	
	public <V> FOption<V> cast(Class<? extends V> cls) {
		Utilities.checkNotNullArgument(cls, "target class is null");
		
		return filter(cls::isInstance).map(cls::cast);
	}
	
	private class FStreamImpl implements FStream<T> {
		private boolean m_first = true;
		
		@Override public void close() throws Exception { }

		@Override
		public FOption<T> next() {
			if ( m_first ) {
				m_first = false;
				return FOption.this;
			}
			else {
				return empty();
			}
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
