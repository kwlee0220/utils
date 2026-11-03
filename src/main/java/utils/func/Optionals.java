package utils.func;


import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import utils.Preconditions;

/**
 * {@code null} 가능 값과 {@link Optional} 객체를 다루는 정적 유틸리티 함수 모음.
 * <p>
 * 인스턴스를 생성할 수 없다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Optionals {
	private Optionals() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}이 아닌 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진
	 * {@code elseValue} 값을 반환한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param value     {@code null} 여부를 판단할 값.
	 * @param elseValue 값이 존재하지 않을 경우 반환할 값
	 * @return {@code value} 값이 존재하는 경우 해당 객체, 그렇지 않은 경우 주어진 {@code elseValue}
	 * @see #getOrElse
	 */
	public static <T> T getOrElse(T value, T elseValue) {
		return (value != null) ? value : elseValue;
	}

	/**
	 * {@code value} 값이 {@code null}이 아닌 경우는 해당 객체를 반환하고, 그렇지 않은 경우 주어진
	 * {@code elseSupplier}로부터 값을 반환한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param value        {@code null} 여부를 판단할 값.
	 * @param elseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code elseSupplier}가 {@code null}인 경우.
	 * @see #getOrElse
	 */
	public static <T> T getOrElse(T value, Supplier<? extends T> elseSupplier) {
		if ( value != null ) {
			return value;
		}
		else {
			Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * 값이 존재하는 경우 해당 객체를 반환하고, 그렇지 않은 경우 주어진 {@code elseSupplier}로부터 값을 반환한다.
	 * <p>
	 * {@code elseSupplier} 함수가 실행 중 예외가 발생되면 해당 예외를 throw 시킨다.
	 * 
	 * @param <T>	값의 타입.
	 * @param <X>	{@code elseSupplier}가 던질 수 있는 예외 타입.
	 * @param value        {@code null} 여부를 판단할 값.
	 * @param elseSupplier 값이 존재하지 않을 경우 반환 값을 생성하는 함수.
	 * @return 존재하는 값. 없는 경우 주어진 {@code elseSupplier}로부터 생성된 값.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code elseSupplier}가 {@code null}인 경우.
	 * @throws X {@code elseSupplier} 함수 실행 중 예외가 발생된 경우.
	 * @see #getOrElse
	 */
	public static <T, X extends Throwable> T getOrElseThrow(T value, CheckedSupplierX<? extends T, X> elseSupplier)
			throws X {
		if ( value != null ) {
			return value;
		}
		else {
			Preconditions.checkNotNullArgument(elseSupplier, "elseSupplier is null");
			return elseSupplier.get();
		}
	}

	/**
	 * {@code opt}에 값이 존재하면 {@code opt}를, 그렇지 않으면 {@code fallback}을 반환한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param opt	검사할 {@link Optional} 객체.
	 * @param fallback	{@code opt}가 비어 있는 경우 반환할 {@link Optional} 객체.
	 * @return {@code opt} 또는 {@code fallback}.
	 */
	public static <T> Optional<T> orElse(Optional<T> opt, Optional<T> fallback) {
		return opt.isPresent() ? opt : fallback;
	}

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
	public static <T> Optional<T> orElse(Optional<T> opt, Supplier<Optional<T>> fallback) {
		return opt.isPresent() ? opt : fallback.get();
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}이 아닌 경우 주어진 {@code effect} 함수를 호출한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param value  {@code null} 여부를 판단할 값.
	 * @param effect 값이 존재하는 경우 호출할 함수.
	 */
	public static <T> void ifPresent(T value, @NotNull Consumer<? super T> effect) {
		if ( value != null ) {
			effect.accept(value);
		}
	}

	/**
	 * 주어진 {@code value} 값이 {@code null}인 경우 주어진 {@code nullAction} 함수를 호출한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param value      {@code null} 여부를 판단할 값.
	 * @param nullAction 값이 존재하지 않을 경우 호출할 함수.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}이고 {@code nullAction}이 {@code null}인 경우.
	 */
	public static <T> void ifAbsent(T value, @NotNull Runnable nullAction) {
		FOption.ofNullable(value).ifAbsent(nullAction);
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
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우는 해당 객체를 활용하여 {@code mapper} 함수를
	 * 호출하여 그 결과 값을 그대로 반환하고, 그렇지 않은 경우 {@code null}을 반환한다.
	 * 
	 * @param <T>	입력 값의 타입.
	 * @param <S>	매핑 결과 값의 타입.
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param mapper   값이 존재하는 경우 호출할 매핑 함수.
	 * @return 값이 존재하는 경우 {@code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code null}.
	 */
	public static <T, S> S map(T nullable, Function<T, S> mapper) {
		return FOption.mapOrElse(nullable, mapper, (S) null);
	}

	/**
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우 해당 객체를 활용하여 {@code mapper} 함수를
	 * 호출하여, 그 결과 값을 그대로 반환하고, 그렇지 않은 경우 {@code elsePart} 값을 반환한다.
	 * <p>
	 * {@code mapper}가 {@code null}을 반환하면 {@code elsePart}가 아닌 {@code null}이 반환된다.
	 * 
	 * @param <T>	입력 값의 타입.
	 * @param <S>	매핑 결과 값의 타입.
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param mapper   값이 존재하는 경우 호출할 매핑 함수.
	 * @param elsePart 값이 존재하지 않을 경우 반환할 값.
	 * @return 값이 존재하는 경우 {@code mapper} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code elsePart}.
	 */
	public static <T, S> S mapOrElse(T nullable, Function<T, S> mapper, S elsePart) {
		if ( nullable != null ) {
			return mapper.apply(nullable);
		}
		else {
			return elsePart;
		}
	}

	/**
	 * {@code nullable} 객체 값이 {@code null}이 아닌 경우 해당 객체를 활용하여 {@code func} 함수를
	 * 호출하여, 그 결과 값을 그대로 반환하고, 그렇지 않은 경우 {@code elsePart} 함수를 호출하여 생성한
	 * 값을 반환한다.
	 * <p>
	 * {@code elsePart}는 {@code nullable}이 {@code null}인 경우에만 호출된다.
	 * 
	 * @param <T>	입력 값의 타입.
	 * @param <S>	결과 값의 타입.
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param func     값이 존재하는 경우 호출할 매핑 함수.
	 * @param elsePart 값이 존재하지 않을 경우 반환할 값 생성 함수.
	 * @return 값이 존재하는 경우 {@code func} 함수를 호출한 결과 값, 그렇지 않은 경우는 {@code elsePart}가 생성한 값.
	 */
	public static <T, S> S mapOrSupply(T nullable, Function<T, S> func, Supplier<? extends S> elsePart) {
		if ( nullable != null ) {
			return func.apply(nullable);
		}
		else {
			return elsePart.get();
		}
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
	 * @param <T>	값의 타입.
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param consumer 객체가 {@code null}이 아닌 경우 실행할 작업.
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
	 * @param <T>	값의 타입.
	 * @param <X>	{@code consumer}가 던질 수 있는 예외 타입.
	 * @param nullable {@code null} 여부를 판단할 객체.
	 * @param consumer 객체가 {@code null}이 아닌 경우 실행할 작업.
	 * @throws X {@code consumer} 실행 중 예외가 발생된 경우.
	 */
	public static <T, X extends Throwable> void acceptThrow(T nullable, CheckedConsumerX<? super T, X> consumer)
			throws X {
		if ( nullable != null ) {
			consumer.accept(nullable);
		}
	}

	/**
	 * 주어진 {@code flag}가 {@code true}인 경우에만 주어진 객체를 감싸는 {@link Optional} 객체를 생성한다.
	 * <p>
	 * 주어진 {@code flag}가 {@code false}인 경우에는 {@link Optional#empty()}를 반환한다.
	 * <p>
	 * <b>주의:</b> {@link Optional#of(Object)}를 사용하므로 {@code flag}가 {@code true}이고
	 * {@code value}가 {@code null}이면 {@link NullPointerException}이 발생한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param flag  주어진 객체를 감쌀지 여부를 나타내는 플래그.
	 * @param value 감쌀 객체
	 * @return {@link Optional} 객체
	 */
	public static <T> Optional<T> whenTrue(boolean flag, T value) {
		return flag ? Optional.of(value) : Optional.empty();
	}

	/**
	 * 주어진 {@code flag}가 {@code true}인 경우에만 {@link Supplier}로부터 생성된 객체를 감싸는
	 * {@link Optional} 객체를 생성한다.
	 * <p>
	 * 주어진 {@code flag}가 {@code false}인 경우에는 {@link Optional#empty()}를 반환하며 {@code supplier}는 호출되지 않는다.
	 * <p>
	 * <b>주의:</b> {@link Optional#of(Object)}를 사용하므로 {@code supplier}가 {@code null}을 반환하면
	 * {@link NullPointerException}이 발생한다.
	 * 
	 * @param <T>	값의 타입.
	 * @param flag     주어진 객체를 감쌀지 여부를 나타내는 플래그.
	 * @param supplier 감쌀 객체를 생성하는 {@link Supplier}
	 * @return {@link Optional} 객체
	 */
	public static <T> Optional<T> whenTrue(boolean flag, Supplier<? extends T> supplier) {
		return flag ? Optional.of(supplier.get()) : Optional.empty();
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
		Preconditions.checkNotNullArgument(mapper, "mapper BiFunction");

		return (opt.isPresent()) ? mapper.apply(src, opt.get()) : src;
	}
}
