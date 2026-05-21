package utils.func;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@code Checked*} 함수형 객체를
 * 표준 {@link java.util.function} 인터페이스처럼 즉시 실행해주는 정적 유틸리티 클래스이다.
 * <p>
 * 각 메소드는 실행 중 발생한 예외를 처리하는 방식에 따라 다음 접미사로 구분된다.
 * <ul>
 *   <li>{@code *OrIgnore}: 발생한 예외를 무시하고 넘어간다. (반환 값이 없는 연산에만 제공된다.)</li>
 *   <li>{@code *OrDefault}: 발생한 예외를 무시하고 지정된 기본 값을 반환한다.
 *          (반환 값이 있는 연산에만 제공된다.)</li>
 *   <li>{@code *OrThrowSneakily}: 발생한 예외를 검사 예외 선언 없이 그대로(sneaky throw) 다시 던진다.</li>
 *   <li>{@code *OrRTE}: 발생한 예외가 {@link RuntimeException}이면 그대로,
 *          그렇지 않으면 {@link RuntimeException}으로 감싸서 던진다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Unchecked {
	/**
	 * 주어진 {@link CheckedRunnable}을 실행하고, 예외가 발생하면 무시한다.
	 *
	 * @param checked	실행할 {@code CheckedRunnable} 객체.
	 */
	public static void runOrIgnore(CheckedRunnable checked) {
		UncheckedRunnable.ignore(checked).run();
	}
	/**
	 * 주어진 {@link CheckedRunnable}을 실행하고, 예외가 발생하면 검사 예외 선언 없이 그대로 던진다.
	 *
	 * @param checked	실행할 {@code CheckedRunnable} 객체.
	 */
	public static void runOrThrowSneakily(CheckedRunnable checked) {
		checked.tryRun().get();
	}
	/**
	 * 주어진 {@link CheckedRunnable}을 실행하고, 예외가 발생하면 {@link RuntimeException}으로 던진다.
	 * <p>
	 * 발생한 예외가 이미 {@code RuntimeException}이면 그대로 던지고,
	 * 그렇지 않으면 {@code RuntimeException}으로 감싸서 던진다.
	 *
	 * @param checked	실행할 {@code CheckedRunnable} 객체.
	 */
	public static void runOrRTE(CheckedRunnable checked) {
		checked.tryRun().mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	/**
	 * 주어진 데이터를 {@link CheckedConsumer}로 소비하고, 예외가 발생하면 무시한다.
	 *
	 * @param <T>		입력 데이터 타입.
	 * @param data		소비할 입력 데이터.
	 * @param checked	실행할 {@code CheckedConsumer} 객체.
	 */
	public static <T> void acceptOrIgnore(T data, CheckedConsumer<? super T> checked) {
		UncheckedConsumer.ignore(checked).accept(data);
	}
	/**
	 * 주어진 데이터를 {@link CheckedConsumer}로 소비하고, 예외가 발생하면 검사 예외 선언 없이 그대로 던진다.
	 *
	 * @param <T>		입력 데이터 타입.
	 * @param data		소비할 입력 데이터.
	 * @param checked	실행할 {@code CheckedConsumer} 객체.
	 */
	public static <T> void acceptOrThrowSneakily(T data, CheckedConsumer<? super T> checked) {
		checked.tryAccept(data).get();
	}
	/**
	 * 주어진 데이터를 {@link CheckedConsumer}로 소비하고, 예외가 발생하면 {@link RuntimeException}으로 던진다.
	 * <p>
	 * 발생한 예외가 이미 {@code RuntimeException}이면 그대로 던지고,
	 * 그렇지 않으면 {@code RuntimeException}으로 감싸서 던진다.
	 *
	 * @param <T>		입력 데이터 타입.
	 * @param data		소비할 입력 데이터.
	 * @param checked	실행할 {@code CheckedConsumer} 객체.
	 */
	public static <T> void acceptOrRTE(T data, CheckedConsumer<? super T> checked) {
		checked.tryAccept(data).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	/**
	 * 주어진 {@link CheckedSupplier}를 실행하여 값을 반환하고,
	 * 예외가 발생하면 지정된 기본 값을 반환한다.
	 *
	 * @param <T>		반환 값 타입.
	 * @param checked	실행할 {@code CheckedSupplier} 객체.
	 * @param defValue	예외가 발생할 경우 반환할 기본 값.
	 * @return	{@code checked}가 생성한 값. 예외가 발생한 경우 {@code defValue}.
	 */
	public static <T> T getOrDefault(CheckedSupplier<? extends T> checked, T defValue) {
		try {
			return checked.get();
		}
		catch ( Exception e ) {
			return defValue;
		}
	}
	/**
	 * 주어진 {@link CheckedSupplier}를 실행하여 값을 얻고, 예외가 발생하면 검사 예외 선언 없이 그대로 던진다.
	 *
	 * @param <T>		반환 값 타입.
	 * @param checked	실행할 {@code CheckedSupplier} 객체.
	 * @return	{@code checked}가 생성한 값.
	 */
	public static <T> T getOrThrowSneakily(CheckedSupplier<? extends T> checked) {
		return checked.tryGet().get();
	}
	/**
	 * 주어진 {@link CheckedSupplier}를 실행하여 값을 얻고, 예외가 발생하면 {@link RuntimeException}으로 던진다.
	 * <p>
	 * 발생한 예외가 이미 {@code RuntimeException}이면 그대로 던지고,
	 * 그렇지 않으면 {@code RuntimeException}으로 감싸서 던진다.
	 *
	 * @param <T>		반환 값 타입.
	 * @param checked	실행할 {@code CheckedSupplier} 객체.
	 * @return	{@code checked}가 생성한 값.
	 */
	public static <T> T getOrRTE(CheckedSupplier<? extends T> checked) {
		return checked.tryGet().mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	/**
	 * 주어진 입력에 {@link CheckedFunction}을 적용하여 결과를 반환하고,
	 * 예외가 발생하면 지정된 기본 값을 반환한다.
	 *
	 * @param <T>		입력 타입.
	 * @param <R>		결과 타입.
	 * @param input		함수에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedFunction} 객체.
	 * @param defValue	예외가 발생할 경우 반환할 기본 값.
	 * @return			함수 적용 결과. 예외가 발생한 경우 {@code defValue}.
	 */
	public static <T,R> R applyOrDefault(T input, CheckedFunction<? super T, ? extends R> checked,
										 R defValue) {
		try {
			return checked.apply(input);
		}
		catch ( Exception e ) {
			return defValue;
		}
	}
	/**
	 * 주어진 입력에 {@link CheckedFunction}을 적용하여 결과를 얻고,
	 * 예외가 발생하면 검사 예외 선언 없이 그대로 던진다.
	 *
	 * @param <T>		입력 타입.
	 * @param <R>		결과 타입.
	 * @param input		함수에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedFunction} 객체.
	 * @return	함수 적용 결과.
	 */
	public static <T,R> R applyOrThrowSneakily(T input, CheckedFunction<? super T, ? extends R> checked) {
		return checked.tryApply(input).get();
	}
	/**
	 * 주어진 입력에 {@link CheckedFunction}을 적용하여 결과를 얻고,
	 * 예외가 발생하면 {@link RuntimeException}으로 던진다.
	 * <p>
	 * 발생한 예외가 이미 {@code RuntimeException}이면 그대로 던지고,
	 * 그렇지 않으면 {@code RuntimeException}으로 감싸서 던진다.
	 *
	 * @param <T>		입력 타입.
	 * @param <R>		결과 타입.
	 * @param input		함수에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedFunction} 객체.
	 * @return	함수 적용 결과.
	 */
	public static <T,R> R applyOrRTE(T input, CheckedFunction<? super T, ? extends R> checked) {
		return checked.tryApply(input).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	/**
	 * 주어진 입력에 {@link CheckedPredicate}를 적용하여 평가 결과를 반환하고,
	 * 예외가 발생하면 지정된 기본 값을 반환한다.
	 *
	 * @param <T>		입력 타입.
	 * @param input		술어에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedPredicate} 객체.
	 * @param defValue	예외가 발생할 경우 반환할 기본 값.
	 * @return	술어 평가 결과. 예외가 발생한 경우 {@code defValue}.
	 */
	public static<T> boolean testOrDefault(T input, CheckedPredicate<? super T> checked, boolean defValue) {
		try {
			return checked.test(input);
		}
		catch ( Exception e ) {
			return defValue;
		}
	}
	/**
	 * 주어진 입력에 {@link CheckedPredicate}를 적용하여 평가하고,
	 * 예외가 발생하면 검사 예외 선언 없이 그대로 던진다.
	 *
	 * @param <T>		입력 타입.
	 * @param input		술어에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedPredicate} 객체.
	 * @return	술어 평가 결과.
	 */
	public static <T> boolean testOrThrowSneakily(T input, CheckedPredicate<? super T> checked) {
		return checked.tryTest(input).get();
	}
	/**
	 * 주어진 입력에 {@link CheckedPredicate}를 적용하여 평가하고,
	 * 예외가 발생하면 {@link RuntimeException}으로 던진다.
	 * <p>
	 * 발생한 예외가 이미 {@code RuntimeException}이면 그대로 던지고,
	 * 그렇지 않으면 {@code RuntimeException}으로 감싸서 던진다.
	 *
	 * @param <T>		입력 타입.
	 * @param input		술어에 적용할 입력 값.
	 * @param checked	실행할 {@code CheckedPredicate} 객체.
	 * @return	술어 평가 결과.
	 */
	public static <T> boolean testOrRTE(T input, CheckedPredicate<? super T> checked) {
		return checked.tryTest(input).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}
}
