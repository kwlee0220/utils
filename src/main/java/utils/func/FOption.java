package utils.func;

import static utils.Utilities.checkNotNullArgument;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import utils.Throwables;
import utils.stream.FStream;
import utils.stream.FStreamable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class FOption<T> implements FStreamable<T>, Iterable<T>, Serializable {
	private static final long serialVersionUID = 1L;

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
		checkNotNullArgument(opt, "Optional is null");
		
		return opt.isPresent() ? of(opt.get()) : empty();
	}
	
	public static <T> FOption<T> when(boolean flag, T value) {
		return flag ? of(value) : empty();
	}
	
	public static <T> FOption<T> when(boolean flag, Supplier<T> value) {
		return flag ? of(value.get()) : empty();
	}
	
	public static FOption<Long> gtz(long value) {
		return value > 0 ? of(value) : empty();
	}
	public static FOption<Integer> gtz(int value) {
		return value > 0 ? of(value) : empty();
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
	public static <T> T getOrElse(T value, T elseValue) {
		return (value != null) ? value : elseValue;
	}
	
	public T getOrElse(Supplier<? extends T> elseSupplier) {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}
	public static <T> T getOrElse(T value, Supplier<? extends T> elseSupplier) {
		if ( value != null ) {
			return value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}
	
	public <X extends Throwable> T getOrElseThrow(CheckedSupplierX<? extends T,X> elseSupplier) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}
	
	public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(thrower, "throwerSupplier is null");
			throw thrower.get();
		}
	}
	
	public FOption<T> ifPresent(@Nonnull Consumer<? super T> effect) {
		checkNotNullArgument(effect, "present consumer is null");
		
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public <X extends Throwable> FOption<T> ifPresentOrThrow(CheckedConsumerX<? super T,X> effect) throws X {
		checkNotNullArgument(effect, "present consumer is null");
		
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> ifAbsent(Runnable orElse) {
		if ( !m_present ) {
			checkNotNullArgument(orElse, "orElse is null");
			
			orElse.run();
		}
		
		return this;
	}

	public <X extends Throwable> FOption<T> ifAbsentOrThrow(CheckedRunnableX<X> orElse) throws X {
		if ( !m_present ) {
			checkNotNullArgument(orElse, "orElse is null");

			orElse.run();
		}
		
		return this;
	}
	
	public FOption<T> filter(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");
		
		if ( m_present ) {
			return (pred.test(m_value)) ? this : empty();
		}
		else {
			return this;
		}
	}
	
	public FOption<T> filterNot(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");
		
		if ( m_present ) {
			return (!pred.test(m_value)) ? this : empty();
		}
		else {
			return this;
		}
	}

	public boolean test(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");
		
		return m_present && pred.test(m_value);
	}
	
	public <S> FOption<S> map(Function<? super T,? extends S> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}

	public static <T,S> S map(T nullable, Function<T,S> func) {
		return FOption.map(nullable, func, (S)null);
	}

	public static <T,S> S map(T nullable, Function<T,S> func, S elsePart) {
		if ( nullable != null ) {
			return func.apply(nullable);
		}
		else {
			return elsePart;
		}
	}

	public static <T,S> S map(T nullable, Function<T,S> func, Supplier<? extends S> elsePart) {
		if ( nullable != null ) {
			return func.apply(nullable);
		}
		else {
			return elsePart.get();
		}
	}
	
	/**
	 * 데이터가 존재하는 경우 주어진 mapper ({@link CheckedFunction})를 적용시킨다.
	 * <p>
	 * Mapper 적용 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param	mapper	변형 함수
	 */
	public <S> FOption<S> mapSneakily(CheckedFunction<? super T,? extends S> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		try {
			return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
		}
		catch ( Throwable e ) {
			Throwables.sneakyThrow(e);
			throw new AssertionError();
		}
	}
	
	public <S,X extends Throwable>
	FOption<S> mapOrThrow(CheckedFunctionX<? super T,? extends S,X> mapper) throws X {
		checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}
	
	public <S> S transform(S src, BiFunction<S,T,? extends S> mapper) {
		checkNotNullArgument(mapper, "mapper BiFunction");
		
		return (m_present) ? mapper.apply(src, m_value) : src;
	}
	
	public <S> FOption<S> flatMap(Function<? super T,FOption<S>> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? mapper.apply(m_value) : empty();
	}
	
	public <S> FOption<S> flatMapOrElse(Function<? super T,FOption<S>> mapper,
										Supplier<FOption<S>> emptyMapper) {
		checkNotNullArgument(mapper, "mapper is null");
		checkNotNullArgument(emptyMapper, "emptyMapper is null");
		
		return (m_present) ? mapper.apply(m_value) : emptyMapper.get();
	}
	
	public <S> FOption<S> flatMapNullable(Function<? super T,? extends S> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return (m_present) ? FOption.ofNullable(mapper.apply(m_value)) : empty();
	}
	
	public <S> FStream<S> flatMapFStream(Function<? super T,FStream<S>> mapper) {
		checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? mapper.apply(m_value) : FStream.empty();
	}
	
	public <S> FOption<S> flatMapSneakily(CheckedFunction<? super T,FOption<S>> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		try {
			return (m_present) ? mapper.apply(m_value) : empty();
		}
		catch ( Throwable e ) {
			Throwables.sneakyThrow(e);
			throw new AssertionError();
		}
	}
	
	public FOption<T> peek(Consumer<? super T> effect) {
		checkNotNullArgument(effect, "effect is null");
		
		if ( m_present ) {
			effect.accept(m_value);
		}
		
		return this;
	}
	
	public FOption<T> orElse(FOption<T> orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			checkNotNullArgument(orElse, "orElse is null");
			return orElse;
		}
	}
	
	public FOption<T> orElse(T orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			checkNotNullArgument(orElse, "orElse is null");
			return FOption.of(orElse);
		}
	}
	
	public FOption<T> orElse(Supplier<FOption<T>> orElseSupplier) {
		if ( m_present ) {
			return this;
		}
		else {
			checkNotNullArgument(orElseSupplier, "orElseSupplier is null");
			return orElseSupplier.get();
		}
	}
	
	public <X extends Throwable> FOption<T> orThrow(Supplier<X> errorSupplier)
		throws X {
		if ( m_present ) {
			return this;
		}
		else {
			checkNotNullArgument(errorSupplier, "errorSupplier is null");
			throw errorSupplier.get();
		}
	}
	
	public List<T> toList() {
		return (m_present) ? Arrays.asList(m_value) : Collections.emptyList();
	}

	@Override
	public Iterator<T> iterator() {
		return toList().iterator();
	}

	@Override
	public FStream<T> fstream() {
		return new FStreamImpl();
	}
	
	public <V> FOption<V> cast(Class<? extends V> cls) {
		checkNotNullArgument(cls, "target class is null");
		
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

	public static void run(Object nullable, Runnable work) {
		if ( Objects.nonNull(nullable) ) {
			work.run();
		}
	}

	public static <T> void accept(T nullable, Consumer<T> consumer) {
		if ( Objects.nonNull(nullable) ) {
			consumer.accept(nullable);
		}
	}
}
