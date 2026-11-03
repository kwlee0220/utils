package utils.func;

import java.util.function.BiConsumer;

import com.google.common.base.Preconditions;

import utils.Tuple;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@link CheckedBiConsumer}를
 * 표준 {@link java.util.function.BiConsumer} 인터페이스로 감싸는 어댑터 클래스이다.
 * <p>
 * {@link #accept(Object, Object)} 수행 중 발생한 모든 예외({@link Throwable})는 두 입력 값을 묶은 {@link utils.Tuple}과 함께
 * {@link FailureCase}로 묶여 생성 시 지정된 {@link FailureHandler}에게 전달된다.
 *
 * @param <S>	첫번째 입력 데이터 타입.
 * @param <T>	두번째 입력 데이터 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedBiConsumer<S,T> implements BiConsumer<S,T> {
	private final CheckedBiConsumer<? super S, ? super T> m_checked;
	private final FailureHandler<Tuple<? super S, ? super T>> m_handler;
	
	UncheckedBiConsumer(CheckedBiConsumer<? super S, ? super T> checked,
						FailureHandler<Tuple<? super S, ? super T>> handler) {
		Preconditions.checkArgument(checked != null, "CheckedBiConsumer is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public void accept(S input1, T input2) {
		try {
			m_checked.accept(input1, input2);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(Tuple.of(input1, input2), e));
		}
	}

	/**
	 * 주어진 {@code CheckedBiConsumer}와 실패 처리기를 이용하여 {@code UncheckedBiConsumer} 객체를 생성한다.
	 *
	 * @param <S>	첫번째 입력 데이터 타입.
	 * @param <T>	두번째 입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedBiConsumer} 객체.
	 * @param handler	예외 발생 시 호출될 실패 처리기.
	 * @return		생성된 {@code UncheckedBiConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked} 또는 {@code handler}가 {@code null}인 경우.
	 */
	public static <S,T> UncheckedBiConsumer<S,T> lift(CheckedBiConsumer<? super S, ? super T> checked,
														FailureHandler<Tuple<? super S, ? super T>> handler) {
		return new UncheckedBiConsumer<>(checked, handler);
	}

	/**
	 * 실행 중 발생한 예외를 무시하는 {@code UncheckedBiConsumer} 객체를 생성한다.
	 *
	 * @param <S>	첫번째 입력 데이터 타입.
	 * @param <T>	두번째 입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedBiConsumer} 객체.
	 * @return		생성된 {@code UncheckedBiConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <S,T> UncheckedBiConsumer<S,T> ignore(CheckedBiConsumer<? super S, ? super T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	/**
	 * 실행 중 발생한 예외를 검사 예외 선언 없이 그대로 다시 던지는(sneaky throw)
	 * {@code UncheckedBiConsumer} 객체를 생성한다.
	 *
	 * @param <S>	첫번째 입력 데이터 타입.
	 * @param <T>	두번째 입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedBiConsumer} 객체.
	 * @return		생성된 {@code UncheckedBiConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <S,T> UncheckedBiConsumer<S,T> sneakyThrow(CheckedBiConsumer<? super S, ? super T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}