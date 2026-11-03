package utils.func;


import java.io.Serializable;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.Throwables;
import utils.stream.FStream;
import utils.stream.FStreamable;


/**
 * 값이 있을 수도 있고 없을 수도 있는 객체를 표현하는 불변(immutable) 옵션 타입.
 * <p>
 * {@link Optional}과 달리 {@code null} 값도 '존재하는 값'으로 감쌀 수 있다
 * ({@code FOption.of(null)}은 {@link #isPresent()}가 {@code true}이고 {@link #get()}은 {@code null}을 반환한다).
 * 값이 없는 상태는 {@link #empty()}로만 표현되며, {@link FStream}/{@link Iterable}로 순회할 수 있다.
 * <p>
 * 값 객체로 비교한다. {@link #equals(Object)}와 {@link #hashCode()}는 존재 여부와 감싼 값으로 정해지므로
 * {@code HashMap}의 키나 {@code HashSet}의 원소로 쓸 수 있다.
 * 
 * @param <T>	감싸는 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public final class FOption<T> implements FStreamable<T>, Iterable<T>, Serializable {
	private static final long serialVersionUID = 1L;

	@SuppressWarnings("rawtypes")
	private static final FOption EMPTY = new FOption<>(null, false);

	/** 감싸고 있는 값. 값이 존재하는 경우에도 {@code null}일 수 있다. */
	private final T m_value;
	/** 값의 존재 여부. */
	private final boolean m_present;

	/**
	 * 주어진 객체를 감싸는 {@link FOption} 객체를 생성한다.
	 * <p>
	 * {@code value}가 {@code null}이어도 빈 객체가 아닌, {@code null} 값을 갖는 존재 상태의 객체를 생성한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param value 감쌀 객체
	 * @return {@link FOption} 객체
	 */
	public static <T> FOption<T> of(T value) {
		return new FOption<>(value, true);
	}

	/**
	 * 값이 없는 {@link FOption} 객체를 생성한다.
	 * 
	 * @param <T>	값의 타입.
	 * @return 빈 {@link FOption} 객체 (공유되는 싱글톤 인스턴스).
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
	 * @param <T>	값의 타입.
	 * @param value 감쌀 객체
	 * @return {@link FOption} 객체
	 */
	public static <T> FOption<T> ofNullable(@Nullable T value) {
		return value != null ? of(value) : empty();
	}

	/**
	 * 주어진 {@link Optional} 객체를 {@link FOption} 객체로 변환한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param opt 변환할 {@link Optional} 객체
	 * @return 변환된 {@link FOption} 객체
	 * @throws IllegalArgumentException	{@code opt}가 {@code null}인 경우.
	 */
	public static <T> FOption<T> from(Optional<T> opt) {
		utils.Preconditions.checkNotNullArgument(opt, "Optional is null");

		return opt.isPresent() ? of(opt.get()) : empty();
	}

	/**
	 * {@code FOption<? extends T>} 타입의 객체를 {@code FOption<T>} 타입으로 변환한다.
	 * <p>
	 * 새 객체를 생성하지 않고 동일 객체를 형변환하여 반환한다.
	 * 
	 * @param <T>	변환될 값의 타입.
	 * @param opt	변환할 {@link FOption} 객체.
	 * @return 형변환된 {@code opt} 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> FOption<T> narrow(FOption<? extends T> opt) {
		return (FOption<T>)opt;
	}

	// flatMap 계열의 mapper가 컨테이너 대신 null을 돌려주면 다음 호출에서 원인을 알기 어려운 NPE가 나므로 경계에서 막는다.
	static <C> C checkMapped(C mapped) {
		if ( mapped == null ) {
			throw new IllegalStateException("flatMap mapper returned null instead of a container");
		}
		return mapped;
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
	 * 값이 존재하는 경우 감싸고 있는 값을 반환하고, 그렇지 않은 경우 {@link NoSuchValueException}을 발생시킨다.
	 * 
	 * @return 감싸고 있는 값 ({@code null}일 수 있다).
	 * @throws NoSuchValueException	값이 존재하지 않는 경우.
	 */
	public T get() {
		if ( m_present ) {
			return m_value;
		}
		else {
			throw new NoSuchValueException();
		}
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
	 * <p>
	 * {@code getOrElse(Supplier)}와 오버로드되어 있으므로 {@code getOrElse(null)}은 컴파일되지 않는다(모호함).
	 * 기본값이 {@code null}이면 {@link #getOrNull()}을 쓴다.
	 * 
	 * @param elseValue 값이 존재하지 않을 경우 반환할 값
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code elseValue}
	 * @see #getOrElse
	 */
	public T getOrElse(T elseValue) {
		return (m_present) ? m_value : elseValue;
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * 
	 * @param elseSupplier 	값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return	존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @throws IllegalArgumentException	값이 존재하지 않고 {@code elseSupplier}가 {@code null}인 경우.
	 * @see #getOrElse
	 */
	public T getOrElse(Supplier<? extends T> elseSupplier) {
		if ( m_present ) {
			return m_value;
		}
		else {
			utils.Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * <p>
	 * {@code elseSupplier} 함수가 실행 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param <X>	{@code elseSupplier}가 던질 수 있는 예외 타입.
	 * @param elseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @throws X	{@code elseSupplier} 함수 실행 중 예외가 발생된 경우.
	 * @throws IllegalArgumentException	값이 존재하지 않고 {@code elseSupplier}가 {@code null}인 경우.
	 * @see #getOrElse
	 */
	public <X extends Throwable> T getOrElseThrow(CheckedSupplierX<? extends T,X> elseSupplier) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			utils.Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우
	 * 주어진 {@code thrower}가 생성한 예외를 발생시킨다.
	 * 
	 * @param <X>	발생시킬 예외 타입.
	 * @param thrower	값이 존재하지 않을 경우 발생시킬 예외를 생성하는 함수.
	 * @return 존재하는 값.
	 * @throws X	값이 존재하지 않을 경우 {@code thrower}가 생성한 예외.
	 * @throws IllegalArgumentException	값이 존재하지 않고 {@code thrower}가 {@code null}인 경우.
	 * @see #getOrElse
	 */
	public <X extends Throwable> T getOrThrow(Supplier<X> thrower) throws X {
		if ( m_present ) {
			return m_value;
		}
		else {
			utils.Preconditions.checkNotNullArgument(thrower, "throwerSupplier is null");
			throw thrower.get();
		}
	}

	/**
	 * 값이 존재하는 경우 {@code effect} 함수의 인자로 해당 객체를 전달하여 호출한다.
	 * 
	 * @param effect	값이 존재하는 경우 호출할 함수.
	 * @return	{@link FOption} 객체 자신.
	 * @throws IllegalArgumentException	{@code effect}가 {@code null}인 경우 (값의 존재 여부와 무관).
	 */
	public FOption<T> ifPresent(@NotNull Consumer<? super T> effect) {
		utils.Preconditions.checkNotNullArgument(effect, "present consumer is null");

		if ( m_present ) {
			effect.accept(m_value);
		}

		return this;
	}

	/**
	 * 값이 존재하는 경우 {@code effect} 함수의 인자로 해당 객체를 전달하여 호출한다.
	 * <p>
	 * 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param <X>	{@code effect}가 던질 수 있는 예외 타입.
	 * @param effect 값이 존재하는 경우 호출할 함수.
	 * @return {@link FOption} 객체 자신.
	 * @throws X	{@code effect} 함수 호출 중 예외가 발생된 경우.
	 * @throws IllegalArgumentException	{@code effect}가 {@code null}인 경우 (값의 존재 여부와 무관).
	 */
	public <X extends Throwable> FOption<T> ifPresentOrThrow(CheckedConsumerX<? super T,X> effect) throws X {
		utils.Preconditions.checkNotNullArgument(effect, "present consumer is null");

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
			utils.Preconditions.checkNotNullArgument(orElse, "orElse is null");

			orElse.run();
		}

		return this;
	}

	/**
	 * 값이 존재하지 않는 경우 주어진 {@code orElse} 함수를 호출한다.
	 * <p>
	 * 호출 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param <X>	{@code orElse}가 던질 수 있는 예외 타입.
	 * @param orElse 값이 존재하지 않을 경우 호출할 함수.
	 * @return {@link FOption} 객체 자신.
	 * @throws X {@code orElse} 함수 호출 중 예외가 발생된 경우.
	 */
	public <X extends Throwable> FOption<T> ifAbsentOrThrow(CheckedRunnableX<X> orElse) throws X {
		if ( !m_present ) {
			utils.Preconditions.checkNotNullArgument(orElse, "orElse is null");

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
		utils.Preconditions.checkNotNullArgument(pred, "Predicate is null");

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
		utils.Preconditions.checkNotNullArgument(pred, "Predicate is null");

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
	 * @return 값이 존재하는 경우 {@code pred} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code false}.
	 */
	public boolean test(Predicate<? super T> pred) {
		utils.Preconditions.checkNotNullArgument(pred, "Predicate is null");

		return m_present && pred.test(m_value);
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 활용하여 {@code mapper} 함수를 호출하여
	 * 그 결과 값을 감싼 {@link FOption}을 반환하고, 그렇지 않은 경우 {@link #empty()}를 반환한다.
	 * <p>
	 * {@code mapper}가 {@code null}을 반환하면 {@code null} 값을 갖는 존재 상태의 {@link FOption}을 반환한다.
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param mapper 값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 {@code mapper} 함수를 호출한 결과 값을 감싼 {@link FOption},
	 * 			그렇지 않은 경우는 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public <S> FOption<S> map(Function<? super T,? extends S> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}

	/**
	 * 데이터가 존재하는 경우 주어진 mapper ({@link CheckedFunction})를 적용시킨다.
	 * <p>
	 * Mapper 적용 중 예외가 발생되면 해당 예외를 감싸지 않고 그대로 throw 시킨다
	 * (checked 예외도 선언 없이 전파되는 sneaky throw).
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param	mapper	변형 함수
	 * @return	값이 존재하는 경우 {@code mapper} 결과 값을 감싼 {@link FOption}, 그렇지 않은 경우 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public <S> FOption<S> mapSneakily(CheckedFunction<? super T,? extends S> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

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
	 * @param <S>	매핑 결과 값의 타입.
	 * @param <X>	{@code mapper}가 던질 수 있는 예외 타입.
	 * @param mapper 값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 {@code mapper} 함수를 호출한 결과 값을 감싼 {@link FOption}, 그렇지 않은 경우는 {@link FOption#empty()}.
	 * @throws X	{@code mapper} 함수 호출 중 예외가 발생된 경우.
	 */
	public <S,X extends Throwable>
	FOption<S> mapOrThrow(CheckedFunctionX<? super T,? extends S,X> mapper) throws X {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? new FOption<>(mapper.apply(m_value), true) : empty();
	}

	/**
	 * 값이 존재하는 경우 {@code mapper}에 {@code src}와 값을 전달하여 호출한 결과를 반환하고,
	 * 그렇지 않은 경우 {@code src}를 그대로 반환한다.
	 * 
	 * @param <S>	변환 대상 객체의 타입.
	 * @param src	변환 대상 객체.
	 * @param mapper	{@code src}와 값을 받아 새 결과를 만드는 함수.
	 * @return 값이 존재하는 경우 {@code mapper.apply(src, value)}, 그렇지 않은 경우 {@code src}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public <S> S fold(S src, BiFunction<S,T,? extends S> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper BiFunction");

		return (m_present) ? mapper.apply(src, m_value) : src;
	}

	/**
	 * 값이 존재하는 경우 {@code mapper}를 호출하여 그 결과 {@link FOption}을 그대로 반환하고,
	 * 그렇지 않은 경우 {@link #empty()}를 반환한다.
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @return {@code mapper}가 반환한 {@link FOption} 또는 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	public <S> FOption<S> flatMap(Function<? super T,? extends FOption<? extends S>> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? narrow(checkMapped(mapper.apply(m_value))) : empty();
	}

	/**
	 * 값이 존재하는 경우 {@code mapper}를 호출한 결과를, 그렇지 않은 경우 {@code emptyMapper}를 호출한 결과를 반환한다.
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @param emptyMapper	값이 존재하지 않는 경우 결과를 생성할 함수.
	 * @return {@code mapper} 또는 {@code emptyMapper}가 반환한 {@link FOption}.
	 * @throws IllegalArgumentException	{@code mapper} 또는 {@code emptyMapper}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	public <S> FOption<S> flatMapOrElse(Function<? super T,? extends FOption<? extends S>> mapper,
										Supplier<? extends FOption<? extends S>> emptyMapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");
		utils.Preconditions.checkNotNullArgument(emptyMapper, "emptyMapper is null");

		return narrow(checkMapped((m_present) ? mapper.apply(m_value) : emptyMapper.get()));
	}

	/**
	 * 값이 존재하는 경우 {@code mapper}를 호출하여 그 결과를 {@link #ofNullable(Object)}로 감싸 반환한다.
	 * <p>
	 * {@link #map(Function)}과 달리 {@code mapper}가 {@code null}을 반환하면 {@link #empty()}가 반환된다.
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @return {@code mapper} 결과를 감싼 {@link FOption}. 값이 없거나 결과가 {@code null}이면 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public <S> FOption<S> mapNotNull(Function<? super T,? extends S> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return (m_present) ? FOption.ofNullable(mapper.apply(m_value)) : empty();
	}

	/**
	 * 값이 존재하는 경우 {@code mapper}를 호출하여 생성된 {@link FStream}을 반환하고,
	 * 그렇지 않은 경우 빈 {@link FStream}을 반환한다.
	 * 
	 * @param <S>	스트림 원소의 타입.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @return {@code mapper}가 반환한 {@link FStream} 또는 {@link FStream#empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	@SuppressWarnings("unchecked")
	public <S> FStream<S> flatMapFStream(Function<? super T,? extends FStream<? extends S>> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		// FStream은 원소를 내보내기만 하므로 FStream<? extends S>를 FStream<S>로 보아도 안전하다.
		return (m_present) ? (FStream<S>)checkMapped(mapper.apply(m_value)) : FStream.empty();
	}

	/**
	 * 값이 존재하는 경우 {@link CheckedFunction} {@code mapper}를 호출하여 그 결과 {@link FOption}을 반환하고,
	 * 그렇지 않은 경우 {@link #empty()}를 반환한다.
	 * <p>
	 * {@code mapper} 호출 중 발생한 예외는 감싸지 않고 그대로 throw된다 (sneaky throw).
	 * 
	 * @param <S>	매핑 결과 값의 타입.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @return {@code mapper}가 반환한 {@link FOption} 또는 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	public <S> FOption<S> flatMapSneakily(CheckedFunction<? super T,? extends FOption<? extends S>> mapper) {
		utils.Preconditions.checkNotNullArgument(mapper, "mapper is null");

		try {
			return (m_present) ? narrow(checkMapped(mapper.apply(m_value))) : empty();
		}
		catch ( Throwable e ) {
			Throwables.sneakyThrow(e);
			throw new AssertionError();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code orElse} 객체를 반환한다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 반환할 객체.
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElse} 객체.
	 */
	public FOption<T> or(FOption<? extends T> orElse) {
		if ( m_present ) {
			return this;
		}
		else {
			utils.Preconditions.checkNotNullArgument(orElse, "orElse is null");
			return narrow(orElse);
		}
	}

	/**
	 * 값이 없으면 {@code orElse}를 감싼 <b>{@code FOption}을</b> 반환한다(값이 있으면 이 객체 자신).
	 * <p>
	 * 값을 꺼내는 {@link #getOrElse(Object)}와 달리 결과가 여전히 {@code FOption}이므로 체인을 이어갈 때 쓴다.
	 * 값 자체가 필요하면 {@link #getOrElse(Object)}를 쓴다.
	 * 
	 * @param orElse 값이 존재하지 않을 경우 반환할 객체.
	 * @return	값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElse} 객체로부터
	 * 			생성된 {@code FOption} 객체. {@code orElse}가 {@code null}이면 {@link #of(Object) of(null)}처럼
	 * 			{@code null} 값을 가진 존재 상태다.
	 */
	public FOption<T> orElse(T orElse) {
		return m_present ? this : FOption.of(orElse);
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code orElseSupplier} 함수 호출 결과 값을 반환한다.
	 * 
	 * @param orElseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code orElseSupplier}로부터 생성된 값.
	 */
	public FOption<T> or(Supplier<? extends FOption<? extends T>> orElseSupplier) {
		if ( m_present ) {
			return this;
		}
		else {
			Preconditions.checkNotNullArgument(orElseSupplier, "orElseSupplier is null");
			return narrow(orElseSupplier.get());
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 그대로 반환하고, 값이 존재하지 않는 경우
	 * 주어진 {@code errorSupplier}가 생성한 예외를 발생시킨다.
	 * 
	 * @param <X>	발생시킬 예외 타입.
	 * @param errorSupplier 값이 존재하지 않을 경우 발생시킬 예외를 생성하는 함수.
	 * @return 값이 존재하는 경우 객체 자신.
	 * @throws X	값이 존재하지 않을 경우 {@code errorSupplier}가 생성한 예외.
	 * @throws IllegalArgumentException	값이 존재하지 않고 {@code errorSupplier}가 {@code null}인 경우.
	 */
	public <X extends Throwable> FOption<T> orThrow(Supplier<X> errorSupplier)
			throws X {
		if ( m_present ) {
			return this;
		}
		else {
			utils.Preconditions.checkNotNullArgument(errorSupplier, "errorSupplier is null");
			throw errorSupplier.get();
		}
	}

	/**
	 * 본 {@code FOption}가 포함한 객체로 구성된 길이 1의 {@link List} 객체를 반환한다.
	 * <p>
	 * 값이 존재하지 않는 경우는 빈 {@link List} 객체를 반환한다.
	 * 반환되는 리스트는 수정할 수 없다.
	 * 
	 * @return {@link List} 객체.
	 */
	public List<T> toList() {
		return (m_present) ? Collections.singletonList(m_value) : Collections.emptyList();
	}
	
	/**
	 * 본 객체를 {@link Optional} 객체로 변환한다.
	 * <p>
	 * 값이 존재하더라도 그 값이 {@code null}인 경우에는 {@link Optional#empty()}가 반환된다.
	 * 
	 * @return 변환된 {@link Optional} 객체.
	 */
	public Optional<T> toOptional() {
		return (m_present) ? Optional.ofNullable(m_value) : Optional.empty();
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

	/**
	 * 값이 존재하고 주어진 클래스의 인스턴스인 경우 해당 타입으로 형변환된 {@link FOption}을 반환하고,
	 * 그렇지 않은 경우 {@link #empty()}를 반환한다.
	 * <p>
	 * 존재하는 값이 {@code null}인 경우에도 {@link #empty()}가 반환된다.
	 * 
	 * @param <V>	형변환할 타입.
	 * @param cls	형변환할 대상 클래스.
	 * @return 형변환된 {@link FOption} 또는 {@link #empty()}.
	 * @throws IllegalArgumentException	{@code cls}가 {@code null}인 경우.
	 */
	public <V> FOption<V> cast(Class<? extends V> cls) {
		Preconditions.checkNotNullArgument(cls, "target class is null");

		return filter(cls::isInstance).map(cls::cast);
	}

	private class FStreamImpl implements FStream<T> {
		private boolean m_first = true;
		private boolean m_closed = false;

		// 다른 FStream 구현(AbstractFStream)처럼 닫은 뒤의 next()는 IllegalStateException이다.
		@Override public void close() {
			m_closed = true;
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
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

	// 역직렬화한 빈 객체를 공유 싱글턴(EMPTY)으로 되돌린다.
	private Object readResolve() {
		return m_present ? this : EMPTY;
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
	 * {@link #equals(Object)}와 일관된 해시 값을 반환한다.
	 * <p>
	 * 빈 객체는 {@code 0}, 값이 있는 객체는 {@code 31 + Objects.hashCode(값)}이다. 따라서 {@code null} 값을 가진
	 * 존재 상태({@code FOption.of(null)})와 빈 객체({@link #empty()})의 해시 값은 다르다.
	 *
	 * @return	해시 값.
	 */
	@Override
	public int hashCode() {
		return m_present ? 31 + Objects.hashCode(m_value) : 0;
	}

}
