package utils.func;

import static utils.Utilities.checkNotNullArgument;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import lombok.experimental.UtilityClass;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@UtilityClass
public class Optionals {

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
	 * 주어진 {@code value} 값이 {@code null}인 경우 주어진 {@code nullAction} 함수를 호출한다.
	 * 
	 * @param value      {@code null} 여부를 판단할 값.
	 * @param nullAction 값이 존재하지 않을 경우 호출할 함수.
	 */
	public static <T> void ifAbsent(T value, @Nonnull Runnable nullAction) {
		FOption.ofNullable(value).ifAbsent(nullAction);
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
	void acceptThrow(T nullable, CheckedConsumerX<? super T,X> consumer) throws X {
		if ( nullable != null ) {
			consumer.accept(nullable);
		}
	}
}
