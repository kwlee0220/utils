package utils.func;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * {@code null}일 수 있는 값을 {@link FOption}으로 감싸지 않고 바로 다루는 정적 도우미 모음.
 * <p>
 * 값이 {@code null}이 아니면 "있음", {@code null}이면 "없음"으로 본다(그래서 {@link FOption#of(Object) FOption.of(null)}처럼
 * "{@code null} 값을 가진 존재 상태"는 표현하지 않는다). 같은 일을 {@link FOption}으로 하려면
 * {@link FOption#ofNullable(Object)}로 감싼 뒤 인스턴스 메소드를 쓴다.
 * <p>
 * 이름 규칙은 JDK를 따른다. 대체값을 바로 주면 {@code orElse}, 만드는 함수를 주면 {@code orElseGet}이다.
 * 이름 끝의 {@code OrThrow}는 검사 예외를 던질 수 있는 함수({@code Checked*X})를 받는다는 뜻이다.
 * <p>
 * {@link Optional}을 다루는 도우미({@code orElse(Optional, Supplier)}, {@code doIfPresent}, {@code mapOrThrow},
 * {@code whenTrue}, {@code transform})도 이 클래스에 있다.
 * <p>
 * 인자 검증 규칙은 {@link FOption}과 같다. 항상 쓰는 인자(예: {@code ifNotNull}의 {@code consumer},
 * {@code map}의 {@code mapper})는 항상 검사하고, 대체(fallback) 인자(예: {@code orElseGet}의 {@code elseSupplier},
 * {@code ifNull}의 {@code action})는 쓸 때만 검사한다. 인스턴스를 생성할 수 없다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Nullables {
	private Nullables() {
		throw new AssertionError("Should not be called: class=" + Nullables.class);
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 그대로 반환하고, {@code null}이면 {@code elseValue}를 반환한다.
	 * <p>
	 * {@link java.util.Objects#requireNonNullElse(Object, Object)}와 달리 {@code elseValue}가 {@code null}이어도 된다.
	 *
	 * @param <T>		값의 타입.
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param elseValue	{@code value}가 {@code null}일 때 반환할 값. {@code null}이어도 된다.
	 * @return	{@code value}가 {@code null}이 아니면 {@code value}, 그렇지 않으면 {@code elseValue}.
	 */
	public static <T> T orElse(@Nullable T value, @Nullable T elseValue) {
		return (value != null) ? value : elseValue;
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 그대로 반환하고, {@code null}이면 {@code elseSupplier}가 만든 값을
	 * 반환한다.
	 *
	 * @param <T>			값의 타입.
	 * @param value			{@code null} 여부를 판단할 값.
	 * @param elseSupplier	{@code value}가 {@code null}일 때 반환할 값을 만드는 함수. 그때만 호출된다.
	 * @return	{@code value}가 {@code null}이 아니면 {@code value}, 그렇지 않으면 {@code elseSupplier}가 만든 값.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code elseSupplier}가 {@code null}인 경우.
	 */
	public static <T> T orElseGet(@Nullable T value, Supplier<? extends T> elseSupplier) {
		if ( value != null ) {
			return value;
		}
		else {
			Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 그대로 반환하고, {@code null}이면 {@code elseSupplier}가 만든 값을
	 * 반환한다. {@code elseSupplier}가 던진 예외는 감싸지 않고 그대로 전파된다.
	 *
	 * @param <T>			값의 타입.
	 * @param <X>			{@code elseSupplier}가 던질 수 있는 예외 타입.
	 * @param value			{@code null} 여부를 판단할 값.
	 * @param elseSupplier	{@code value}가 {@code null}일 때 반환할 값을 만드는 함수. 그때만 호출된다.
	 * @return	{@code value}가 {@code null}이 아니면 {@code value}, 그렇지 않으면 {@code elseSupplier}가 만든 값.
	 * @throws X	{@code elseSupplier}가 예외를 던진 경우.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code elseSupplier}가 {@code null}인 경우.
	 */
	public static <T,X extends Throwable>
	T getOrElseThrow(@Nullable T value, CheckedSupplierX<? extends T,X> elseSupplier) throws X {
		if ( value != null ) {
			return value;
		}
		else {
			Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 그 값으로 {@code consumer}를 호출한다.
	 *
	 * @param <T>		값의 타입.
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param consumer	{@code value}가 {@code null}이 아닐 때 호출할 함수.
	 * @throws IllegalArgumentException	{@code consumer}가 {@code null}인 경우({@code value}와 무관).
	 */
	public static <T> void ifNotNull(@Nullable T value, Consumer<? super T> consumer) {
		Preconditions.checkNotNullArgument(consumer, "consumer is null");
		if ( value != null ) {
			consumer.accept(value);
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 {@code work}를 실행한다. 값은 {@code work}에 전달하지 않는다.
	 * <p>
	 * {@link #ifNotNull(Object, Consumer)}와 이름을 나눈 것은, getter·setter가 같은 이름인 메소드 참조
	 * (예: {@code builder::name})가 {@link Runnable}과 {@link Consumer}에 모두 맞아 오버로드가 모호해지기 때문이다.
	 *
	 * @param value	{@code null} 여부를 판단할 값.
	 * @param work	{@code value}가 {@code null}이 아닐 때 실행할 작업.
	 * @throws IllegalArgumentException	{@code work}가 {@code null}인 경우({@code value}와 무관).
	 */
	public static void ifNotNullRun(@Nullable Object value, Runnable work) {
		Preconditions.checkNotNullArgument(work, "work is null");
		if ( value != null ) {
			work.run();
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 그 값으로 {@code consumer}를 호출한다. {@code consumer}가 던진
	 * 예외는 감싸지 않고 그대로 전파된다.
	 *
	 * @param <T>		값의 타입.
	 * @param <X>		{@code consumer}가 던질 수 있는 예외 타입.
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param consumer	{@code value}가 {@code null}이 아닐 때 호출할 함수.
	 * @throws X	{@code consumer}가 예외를 던진 경우.
	 * @throws IllegalArgumentException	{@code consumer}가 {@code null}인 경우({@code value}와 무관).
	 */
	public static <T,X extends Throwable>
	void ifNotNullOrThrow(@Nullable T value, CheckedConsumerX<? super T,X> consumer) throws X {
		Preconditions.checkNotNullArgument(consumer, "consumer is null");
		if ( value != null ) {
			consumer.accept(value);
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이면 {@code action}을 실행한다.
	 *
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param action	{@code value}가 {@code null}일 때 실행할 작업. 그때만 검사한다.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code action}이 {@code null}인 경우.
	 */
	public static void ifNull(@Nullable Object value, Runnable action) {
		if ( value == null ) {
			Preconditions.checkNotNullArgument(action, "action is null");
			action.run();
		}
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 {@code mapper}를 적용한 결과를, {@code null}이면 {@code null}을
	 * 반환한다.
	 *
	 * @param <T>		입력 값의 타입.
	 * @param <S>		매핑 결과 값의 타입.
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param mapper	{@code value}가 {@code null}이 아닐 때 적용할 함수.
	 * @return	{@code mapper} 적용 결과. {@code value}가 {@code null}이면 {@code null}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우({@code value}와 무관).
	 */
	public static <T,S> @Nullable S map(@Nullable T value, Function<? super T,? extends S> mapper) {
		return mapOrElse(value, mapper, (S)null);
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 {@code mapper}를 적용한 결과를, {@code null}이면 {@code elseValue}를
	 * 반환한다.
	 *
	 * @param <T>		입력 값의 타입.
	 * @param <S>		매핑 결과 값의 타입.
	 * @param value		{@code null} 여부를 판단할 값.
	 * @param mapper	{@code value}가 {@code null}이 아닐 때 적용할 함수.
	 * @param elseValue	{@code value}가 {@code null}일 때 반환할 값.
	 * @return	{@code mapper} 적용 결과. {@code value}가 {@code null}이면 {@code elseValue}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우({@code value}와 무관).
	 */
	public static <T,S> S mapOrElse(@Nullable T value, Function<? super T,? extends S> mapper, S elseValue) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		return (value != null) ? mapper.apply(value) : elseValue;
	}

	/**
	 * 주어진 {@code value}가 {@code null}이 아니면 {@code mapper}를 적용한 결과를, {@code null}이면
	 * {@code elseSupplier}가 만든 값을 반환한다.
	 *
	 * @param <T>			입력 값의 타입.
	 * @param <S>			매핑 결과 값의 타입.
	 * @param value			{@code null} 여부를 판단할 값.
	 * @param mapper		{@code value}가 {@code null}이 아닐 때 적용할 함수.
	 * @param elseSupplier	{@code value}가 {@code null}일 때 반환할 값을 만드는 함수. 그때만 호출·검사한다.
	 * @return	{@code mapper} 적용 결과. {@code value}가 {@code null}이면 {@code elseSupplier}가 만든 값.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}이거나, {@code value}가 {@code null}이고
	 * 									{@code elseSupplier}가 {@code null}인 경우.
	 */
	public static <T,S> S mapOrElseGet(@Nullable T value, Function<? super T,? extends S> mapper,
										Supplier<? extends S> elseSupplier) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		if ( value != null ) {
			return mapper.apply(value);
		}
		else {
			Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	// ----- java.util.Optional 도우미 (예전 Optionals) -----

	/**
	 * {@code opt}에 값이 존재하면 {@code opt}를, 그렇지 않으면 {@code fallback}을 호출한 결과를 반환한다.
	 * <p>
	 * {@code fallback}은 {@code opt}가 비어 있는 경우에만 호출된다.
	 * 
	 * @param <T>	값의 타입.
	 * @param opt	검사할 {@link Optional} 객체.
	 * @param fallback	{@code opt}가 비어 있는 경우 반환할 {@link Optional}을 생성하는 함수.
	 * @return {@code opt} 또는 {@code fallback}이 생성한 {@link Optional}.
	 */
	public static <T> Optional<T> orElse(Optional<T> opt, Supplier<? extends Optional<T>> fallback) {
		return opt.isPresent() ? opt : fallback.get();
	}

	/**
	 * {@code opt}에 값이 존재하는 경우 {@code effect}에 해당 값을 전달하여 호출하고, {@code opt}를 그대로 반환한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param opt	검사할 {@link Optional} 객체.
	 * @param effect	값이 존재하는 경우 호출할 함수.
	 * @return 인자로 주어진 {@code opt}.
	 */
	public static <T> Optional<T> doIfPresent(Optional<T> opt, @NotNull Consumer<? super T> effect) {
		if ( opt.isPresent() ) {
			effect.accept(opt.get());
		}
		return opt;
	}

	/**
	 * {@code opt}에 값이 존재하는 경우 {@code mapper}를 호출하여 그 결과를 {@link Optional#of(Object)}로 감싸 반환하고,
	 * 그렇지 않은 경우 {@link Optional#empty()}를 반환한다.
	 * <p>
	 * <b>주의:</b> {@code mapper}가 {@code null}을 반환하면 {@link Optional#of(Object)}에 의해
	 * {@link NullPointerException}이 발생한다.
	 * 
	 * @param <T>	입력 값의 타입.
	 * @param <S>	매핑 결과 값의 타입.
	 * @param <X>	{@code mapper}가 던질 수 있는 예외 타입.
	 * @param opt	매핑할 {@link Optional} 객체.
	 * @param mapper	값이 존재하는 경우 호출할 매핑 함수.
	 * @return {@code mapper} 결과를 감싼 {@link Optional} 또는 {@link Optional#empty()}.
	 * @throws X	{@code mapper} 함수 호출 중 예외가 발생된 경우.
	 */
	public static <T, S, X extends Throwable> Optional<S> mapOrThrow(Optional<T> opt,
			CheckedFunctionX<? super T, ? extends S, X> mapper) throws X {
		if ( opt.isPresent() ) {
			return Optional.of(mapper.apply(opt.get()));
		}
		else {
			return Optional.empty();
		}
	}

	/**
	 * 주어진 {@code flag}가 {@code true}인 경우에만 주어진 객체를 감싸는 {@link Optional} 객체를 생성한다.
	 * <p>
	 * 주어진 {@code flag}가 {@code false}인 경우에는 {@link Optional#empty()}를 반환한다.
	 * <p>
	 * {@code flag}가 {@code true}여도 {@code value}가 {@code null}이면 {@link Optional#empty()}를 반환한다
	 * ({@link Optional}은 {@code null}을 담을 수 없다).
	 * 
	 * @param <T>	값의 타입.
	 * @param flag  주어진 객체를 감쌀지 여부를 나타내는 플래그.
	 * @param value 감쌀 객체
	 * @return {@link Optional} 객체
	 */
	public static <T> Optional<T> whenTrue(boolean flag, T value) {
		return flag ? Optional.ofNullable(value) : Optional.empty();
	}

	/**
	 * {@code opt}에 값이 존재하는 경우 {@code mapper}에 {@code src}와 값을 전달하여 호출한 결과를 반환하고,
	 * 그렇지 않은 경우 {@code src}를 그대로 반환한다.
	 * 
	 * @param <T>	{@code opt} 값의 타입.
	 * @param <S>	변환 대상 객체의 타입.
	 * @param opt	검사할 {@link Optional} 객체.
	 * @param src	변환 대상 객체.
	 * @param mapper	{@code src}와 값을 받아 새 결과를 만드는 함수.
	 * @return 값이 존재하는 경우 {@code mapper.apply(src, value)}, 그렇지 않은 경우 {@code src}.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public static <T, S> S transform(Optional<T> opt, S src, BiFunction<S, T, ? extends S> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return (opt.isPresent()) ? mapper.apply(src, opt.get()) : src;
	}
}
