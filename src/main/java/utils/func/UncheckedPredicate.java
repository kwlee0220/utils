package utils.func;

import java.util.function.Predicate;

import utils.Preconditions;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@link CheckedPredicate}를
 * 표준 {@link java.util.function.Predicate} 인터페이스로 감싸는 어댑터 클래스이다.
 * <p>
 * {@link #test(Object)} 수행 중 발생한 예외({@link Error} 제외)는 입력 값과 함께
 * {@link FailureCase}로 묶여 생성 시 지정된 {@link FailureHandler}에게 전달된다.
 * <p>
 * 반환 값이 있는 연산이므로 실패 처리기는 예외를 던져야 한다. 처리기가 정상적으로 반환하면 돌려줄 값이 없으므로
 * {@code test}는 원래 예외를 원인으로 하는 {@link IllegalStateException}을 던진다. 같은 이유로 예외를 무시하는
 * {@code ignore} 팩토리는 없다(값이 없는 {@link UncheckedConsumer}·{@link UncheckedRunnable}에만 있다).
 *
 * @param <T>	입력 데이터 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedPredicate<T> implements Predicate<T> {
	private final CheckedPredicate<? super T> m_checked;
	private final FailureHandler<? super T> m_handler;
	
	UncheckedPredicate(CheckedPredicate<? super T> checked, FailureHandler<? super T> handler) {
		Preconditions.checkNotNullArgument(checked, "CheckedPredicate is null");
		Preconditions.checkNotNullArgument(handler, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public boolean test(T input) {
		try {
			return m_checked.test(input);
		}
		catch ( Throwable e ) {
			if ( e instanceof Error err ) {
				throw err;		// Error는 실패 처리기에 넘기지 않는다.
			}
			m_handler.handle(new FailureCase<>(input, e));
			throw new IllegalStateException("FailureHandler returned without throwing: cause=" + e, e);
		}
	}

	/**
	 * 주어진 {@code CheckedPredicate}와 실패 처리기를 이용하여 {@code UncheckedPredicate} 객체를 생성한다.
	 *
	 * @param <T>	입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedPredicate} 객체.
	 * @param handler	예외 발생 시 호출될 실패 처리기.
	 * @return		생성된 {@code UncheckedPredicate} 객체.
	 * @throws IllegalArgumentException	{@code checked} 또는 {@code handler}가 {@code null}인 경우.
	 */
	public static <T> UncheckedPredicate<T> lift(CheckedPredicate<? super T> checked,
												FailureHandler<? super T> handler) {
		return new UncheckedPredicate<>(checked, handler);
	}

	/**
	 * 실행 중 발생한 예외를 검사 예외 선언 없이 그대로 다시 던지는(sneaky throw)
	 * {@code UncheckedPredicate} 객체를 생성한다.
	 *
	 * @param <T>	입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedPredicate} 객체.
	 * @return		생성된 {@code UncheckedPredicate} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <T> UncheckedPredicate<T> sneakyThrow(CheckedPredicate<? super T> checked) {
		return lift(checked, FailureHandlers.throwSneakily());
	}
}