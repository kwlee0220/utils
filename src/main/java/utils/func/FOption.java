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

import com.google.common.base.Preconditions;

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

	/**
	 * 주어진 객체를 감싸는 {@link FOption} 객체를 생성한다.
	 * 
	 * @param value 감쌀 객체
	 * @return {@link FOption} 객체
	 */
	public static <T> FOption<T> of(T value) {
		return new FOption<>(value, true);
	}

	/**
	 * 값이 없는 {@link FOption} 객체를 생성한다.
	 * 
	 * @return 빈 {@link FOption} 객체
	 */
	@SuppressWarnings("unchecked")
	public static <T> FOption<T> empty() {
		return (FOption<T>)EMPTY;
	}

	/**
	 * 주어진 객체를 감싸는 {@link FOption} 객체를 생성한다.
	 * <p>
	 * 주어진 객체가 <code>null</code>인 경우 빈 {@link FOption} 객체를 생성한다.
	 * 
	 * @param value 감쌀 객체
	 * @return {@link FOption} 객체
	 */
	public static <T> FOption<T> ofNullable(T value) {
		return value != null ? of(value) : empty();
	}

	/**
	 * 주어진 {@link Optional} 객체를 {@link FOption} 객체로 변환한다.
	 * 
	 * @param opt 변환할 {@link Optional} 객체
	 * @return 변환된 {@link FOption} 객체
	 */
	public static <T> FOption<T> from(Optional<T> opt) {
		checkNotNullArgument(opt, "Optional is null");

		return opt.isPresent() ? of(opt.get()) : empty();
	}

	/**
	 * 주어진 {@code flag}가 {@code true}인 경우에만 주어진 객체를 감싸는 {@link FOption} 객체를 생성한다.
	 * <p>
	 * 주어진 {@code flag}가 {@code false}인 경우에는 {@link FOption.empty()}를 반환한다.
	 * 
	 * @param flag	주어진 객체를 감쌀지 여부를 나타내는 플래그.
	 * @param value 감쌀 객체
	 * @return {@link FOption} 객체
	 */
	public static <T> FOption<T> when(boolean flag, T value) {
		return flag ? of(value) : empty();
	}

	/**
	 * 주어진 {@code flag}가 {@code true}인 경우에만 주어진 {@link Supplier}로부터 객체를 생성하여
	 * 감싸는 {@link FOption} 객체를 생성한다.
	 * <p>
	 * 주어진 {@code flag}가 {@code false}인 경우에는 {@link FOption.empty()}를 반환한다.
	 * 
	 * @param flag      주어진 객체를 감쌀지 여부를 나타내는 플래그.
	 * @param value		감쌀 객체를 생성하는 함수
	 * @return {@link FOption} 객체
	 * @see #when(boolean, Object)
	 */
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

	/**
	 * 값이 존재하는지 여부를 반환한다.
	 * 
	 * @return 값이 존재하는 경우에는 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isPresent() {
		return m_present;
	}

	/**
	 * 값이 존재하지 않는지 여부를 반환한다.
	 * 
	 * @return 값이 존재하지 않는 경우에는 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isAbsent() {
		return !m_present;
	}

	/**
	 * 값이 존재하는 경우 해당	Optional 객체를 반환하고, 그렇지 않은 경우 빈 Optional 객체를 반환한다.
	 * 
	 * @return 값이 존재하는 경우 해당	FOption 객체, 그렇지 않은 경우 {@link FOption#empty()} 객체.
	 */
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

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 {@code null} 값을 반환한다.
	 * 
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 {@code null}.
	 */
	public T getOrNull() {
		return (m_present) ? m_value : null;
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseValue} 값을 반환한다.
	 * 
	 * @param elseValue 값이 존재하지 않을 경우 반환할 값
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code elseValue}
	 * @see #getOrElse
	 */
	public T getOrElse(T elseValue) {
		return (m_present) ? m_value : elseValue;
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}이 아닌 경우 해당 객체를 반환하고,
	 * 그렇지 않은 경우 주어진 {@code elseValue} 값을 반환한다.
	 * 
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param elseValue 값이 존재하지 않을 경우 반환할 값
	 * @return	{@code value}  값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code elseValue}
	 * @see #getOrElse
	 */
	public static <T> T getOrElse(T value, T elseValue) {
		return (value != null) ? value : elseValue;
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * 
	 * @param elseValue 	값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return	존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @see #getOrElse
	 */
	public T getOrElse(Supplier<? extends T> elseSupplier) {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * {@code value} 값이 {@code null}이 아닌 경우는 해당 객체를 반환하고,
	 * 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * 
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param elseValue 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return	존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @see #getOrElse
	 */
	public static <T> T getOrElse(T value, Supplier<? extends T> elseSupplier) {
		if ( value != null ) {
			return value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * <p>
	 * {@code elseSupplier} 함수가 실행 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param elseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @see #getOrElse
	 */
	public <X extends Throwable> T getOrElseThrow(CheckedSupplierX<? extends T,X> elseSupplier) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * <p>
	 * {@code elseSupplier} 함수가 실행 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param value        {@code null} 여부를 판단할 값.
	 * @param elseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @throws X	{@code elseSupplier} 함수 실행 중 예외가 발생된 경우.
	 * @see #getOrElse
	 */
	public static <T,X extends Throwable> T getOrElseThrow(T value, CheckedSupplierX<? extends T,X> elseSupplier) throws X {
		if ( value != null ) {
			return value;
		}
		else {
			checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우
	 * 주어진 {@code thrower}를 생성한 예외를 발생시킨다.
	 * 
	 * @param thrower	값이 존재하지 않을 경우 발생시킬 예외를 생성하는 함수.
	 * @return 존재하는 값.
	 * @throws X	값이 존재하지 않을 경우 발생시킬 예외.
	 * @see #getOrElse
	 */
	public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			checkNotNullArgument(thrower, "throwerSupplier is null");
			throw thrower.get();
		}
	}

	/**
	 * 값이 존재하는 경우 {@code effect} 함수의 인자로 해당 객체를 전달하여 호출한다.
	 * 
	 * @param effect	값이 존재하는 경우 호출할 함수.
	 * @return	{@link FOption} 객체 자신.
	 */
	public FOption<T> ifPresent(@Nonnull Consumer<? super T> effect) {
		checkNotNullArgument(effect, "present consumer is null");

		if ( m_present ) {
			effect.accept(m_value);
		}

		return this;
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}이 아닌 경우 주어진 {@code effect} 함수를 호출한다.
	 * 
	 * @param value  {@code null} 여부를 판단할 값.
	 * @param effect 값이 존재하는 경우 호출할 함수.
	 */
	public static <T> void ifPresent(T value, @Nonnull Consumer<? super T> effect) {
		if ( value != null ) {
			effect.accept(value);
		}
	}

	/**
	 * 값이 존재하는 경우 {@code effect} 함수의 인자로 해당 객체를 전달하여 호출한다.
	 * <p>
	 * 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param effect 값이 존재하는 경우 호출할 함수.
	 * @return {@link FOption} 객체 자신.
	 * @throws X	{@code effect} 함수 호출 중 예외가 발생된 경우.
	 */
	public <X extends Throwable> FOption<T> ifPresentOrThrow(CheckedConsumerX<? super T,X> effect) throws X {
		checkNotNullArgument(effect, "present consumer is null");

		if ( m_present ) {
			effect.accept(m_value);
		}

		return this;
	}

	/**
	 * 값이 존재하지 않는 경우 주어진 {@code orElse} 함수를 호출한다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 호출할 함수.
	 * @return {@link FOption} 객체 자신.
	 */
	public FOption<T> ifAbsent(Runnable orElse) {
		if ( !m_present ) {
			checkNotNullArgument(orElse, "orElse is null");

			orElse.run();
		}

		return this;
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}인 경우 주어진 {@code nullAction} 함수를 호출한다.
	 * 
	 * @param value      {@code null} 여부를 판단할 값.
	 * @param nullAction 값이 존재하지 않을 경우 호출할 함수.
	 */
	public static <T> void ifAbsent(T value, @Nonnull Runnable nullAction) {
		FOption.ofNullable(value).ifAbsent(nullAction);
	}

	/**
	 * 값이 존재하지 않는 경우 주어진 {@code orElse} 함수를 호출한다.
	 * <p>
	 * 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 호출할 함수.
	 * @return {@link FOption} 객체 자신.
	 * @throws X {@code orElse} 함수 호출 중 예외가 발생된 경우.
	 */
	public <X extends Throwable> FOption<T> ifAbsentOrThrow(CheckedRunnableX<X> orElse) throws X {
		if ( !m_present ) {
			checkNotNullArgument(orElse, "orElse is null");

			orElse.run();
		}

		return this;
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code pred} 함수를 호출하여
	 * 결과 값이 {@code true}인 경우 객체 자신을 반환하고,
	 * 그렇지 않은 경우 빈 {@link FOption#empty()} 객체를 반환한다.
	 * 
	 * @param pred 값이 존재하는 경우 호출할 {@link Predicate} 함수.
	 * @return 값이 존재하는 경우 객체 자신, 그렇지 않은 경우는 빈 {@link FOption#empty()} 객체.
	 */
	public FOption<T> filter(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");

		if ( m_present ) {
			return (pred.test(m_value)) ? this : empty();
		}
		else {
			return this;
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code pred} 함수를 호출하여
	 * 결과 값이 {@code false}인 경우 객체 자신을 반환하고,
	 * 그렇지 않은 경우 빈 {@link FOption#empty()} 객체를 반환한다.
	 * 
	 * @param pred 값이 존재하는 경우 호출할 {@link Predicate} 함수.
	 * @return 값이 존재하는 경우 객체 자신, 그렇지 않은 경우는 빈 {@link FOption#empty()} 객체.
	 */
	public FOption<T> filterNot(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");

		if ( m_present ) {
			return (!pred.test(m_value)) ? this : empty();
		}
		else {
			return this;
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code pred} 함수를 호출하여
	 * 그 결과 값을 반환하고, 그렇지 않은 경우 {@code false}를 반환한다.
	 * 
	 * @param pred 값이 존재하는 경우 호출할 {@link Predicate} 함수.
	 * @return 값이 존재하는 경우 @code pred} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code false}.
	 */
	public boolean test(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "Predicate is null");

		return m_present && pred.test(m_value);
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code mapper} 함수를 호출하여
	 * 그 결과 값을 반환하고, 그렇지 않은 경우 객체 자신을 반환한다.
	 * 
	 * @param pred 값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 @code mapper} 함수를 호출한 결과 값,
	 * 			그렇지 않은 경우는 객체 자신을 반환한다.
	 */
	public <S> FOption<S> map(Function<? super T,? extends S> mapper) {
		checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}

	/**
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우는 해당 객체를 활용하여 {@code mapper} 함수를 호출하여
	 * 그 결과 값을 포함한	{@link FOption} 객체를 반환하고, 그렇지 않은 경우 {@code null}을 반환한다.
	 * <p>
	 * {@code mapper} 함수 호출 반환 값이 {@code null}인 경우는 {@code null}을 갖는 {@link FOption} 객체를 반환한다.
	 * 이 값은 {@code FOption#empty()}와는 구별된다
	 * 
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param mapper   값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 @code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code null}.
	 */
	public static <T,S> S map(T nullable, Function<T,S> mapper) {
		return FOption.mapOrElse(nullable, mapper, (S)null);
	}

	/**
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우 해당 객체를 활용하여 {@code mapper} 함수를 호출하여,
	 * 그 결과 값을 포함한	{@link FOption} 객체를 반환하고, 그렇지 않은 경우 {@code elsePart} 값을 반환한다.
	 * <p>
	 * {@code mapper} 함수 호출 반환 값이 {@code null}인 경우는
	 * {@code null}을 갖는 {@link FOption} 객체를 반환한다.
	 * 이 값은 {@code FOption#empty()}와는 구별된다
	 * 
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param mapper   값이 존재하는 경우 호출할 매핑 함수.
	 * @param elsePart 값이 존재하지 않을 경우 반환할 값.
	 * @return 값이 존재하는 경우 @code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code elsePart}.
	 */
	public static <T,S> S mapOrElse(T nullable, Function<T,S> mapper, S elsePart) {
		if ( nullable != null ) {
			return mapper.apply(nullable);
		}
		else {
			return elsePart;
		}
	}

	/**
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우 해당 객체를 활용하여 {@code mapper} 함수를 호출하여,
	 * 그 결과 값을 포함한	{@link FOption} 객체를 반환하고,
	 * 그렇지 않은 경우 {@code elsePart} 함수 결과 값을 생성하여 반환한다.
	 * <p>
	 * {@code mapper} 함수 호출 반환 값이 {@code null}인 경우는 {@code null}을 갖는 {@link FOption} 객체를 반환한다.
	 * 이 값은 {@code FOption#empty()}와는 구별된다
	 * 
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param mapper   값이 존재하는 경우 호출할 매핑 함수.
	 * @param elsePart 값이 존재하지 않을 경우 반환할 값 생성 함수.
	 * @return 값이 존재하는 경우 @code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code elsePart}.
	 */
	public static <T,S> S mapOrSupply(T nullable, Function<T,S> func, Supplier<? extends S> elsePart) {
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

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code mapper} 함수를 호출하여
	 * 그 결과 값을 포함한	{@link FOption} 객체를 반환하고,
	 * 그렇지 않은 경우 {@link FOption#empty()} 값을 반환한다.
	 * <p>
	 * {@code mapper} 함수 호출 반환 값이 {@code null}인 경우는 {@code null}을 갖는 {@link FOption} 객체를 반환한다.
	 * 이 값은 {@code FOption#empty()}와는 구별된다
	 * {@code mapper} 함수 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param mapper 값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 @code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@link FOption#empty()}.
	 * @throws X	{@code mapper} 함수 호출 중 예외가 발생된 경우.
	 */
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

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code orElse} 객체를 반환한다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 반환할 객체.
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElse} 객체.
	 */
	public FOption<T> orElse(FOption<T> orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			checkNotNullArgument(orElse, "orElse is null");
			return orElse;
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우 주어진
	 * {@code orElse} 객체를 wrapping한 {@code FOption} 객체를 반환한다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 반환할 객체.
	 * @return	값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElse} 객체로부터
	 * 			생성된 {@code FOption} 객체.
	 */
	public FOption<T> orElse(T orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			Preconditions.checkArgument(orElse != null, "orElse is null");
			return FOption.of(orElse);
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code orElseSupplier} 함수 호출 결과 값을 반환한다.
	 * 
	 * @param orElseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElseSupplier}로부터 생성된 값.
	 */
	public FOption<T> orElse(Supplier<FOption<T>> orElseSupplier) {
		if ( m_present ) {
			return this;
		}
		else {
			Preconditions.checkArgument(orElseSupplier != null, "orElseSupplier is null");
			return orElseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code orElseSupplier} 함수 호출 결과
	 * 값을 반환한다.
	 * <p>
	 * {@code orElseSupplier} 함수 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param orElseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElseSupplier}로부터 생성된 값.
	 * @throws X	{@code orElseSupplier} 함수 호출 중 예외가 발생된 경우.
	 */
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

	/**
	 * 본 {@code FOption}가 포함한 객체로 구성된 길이 1의 {@link List} 객체를 반환한다.
	 * <p>
	 * 값이 존재하지 않는 경우는 빈 {@link List} 객체를 반환한다.
	 * 
	 * @return {@link List} 객체.
	 */
	public List<T> toList() {
		return (m_present) ? Arrays.asList(m_value) : Collections.emptyList();
	}

	/**
	 * 본 {@code FOption}가 포함한 객체를 방문하는 {@link Iterator} 객체를 반환한다.
	 * <p>
	 * 값이 존재하지 않는 경우는 빈 {@link Iterator} 객체를 반환한다.
	 * 
	 * @return {@link Iterator} 객체.
	 */
	@Override
	public Iterator<T> iterator() {
		return toList().iterator();
	}

	@Override
	public FStream<T> fstream() {
		return new FStreamImpl();
	}

	public <V> FOption<V> cast(Class<? extends V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");

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

	/**
	 * 주어진 {@code nullable}이 {@code null}이 아닌 경우는 주어진 {@code work}를 실행한다.
	 * 
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param work     객체가 {@code null}이 아닌 경우 실행할 작업.
	 */
	public static void run(Object nullable, Runnable work) {
		if ( nullable != null ) {
			work.run();
		}
	}

	/**
	 * 주어진 {@code nullable}이 {@code null}이 아닌 경우는 주어진 {@code consumer}를 실행한다.
	 * 
	 * @param nullable	{@code null} 여부를 판단할 객체.
	 * @param consumer	객체가 {@code null}이 아닌 경우 실행할 작업.
	 */
	public static <T> void accept(T nullable, Consumer<T> consumer) {
		if ( nullable != null ) {
			consumer.accept(nullable);
		}
	}

	/**
	 * 주어진 {@code nullable}이 {@code null}이 아닌 경우는 주어진 {@code consumer}를 실행한다.
	 * <p>
	 * {@code consumer} 실행 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param consumer 객체가 {@code null}이 아닌 경우 실행할 작업.
	 * @throws X {@code consumer} 실행 중 예외가 발생된 경우.
	 */
	public static <T,X extends Throwable>
	void acceptOrThrow(T nullable, CheckedConsumerX<? super T,X> consumer) throws X {
		if ( Objects.nonNull(nullable) ) {
			consumer.accept(nullable);
		}
	}
}
