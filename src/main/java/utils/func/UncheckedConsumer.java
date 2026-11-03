package utils.func;

import java.util.function.Consumer;

import com.google.common.base.Preconditions;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@link CheckedConsumer}를
 * 표준 {@link java.util.function.Consumer} 인터페이스로 감싸는 어댑터 클래스이다.
 * <p>
 * {@link #accept(Object)} 수행 중 발생한 모든 예외({@link Throwable})는 입력 값과 함께
 * {@link FailureCase}로 묶여 생성 시 지정된 {@link FailureHandler}에게 전달된다.
 *
 * @param <T>	입력 데이터 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedConsumer<T> implements Consumer<T> {
	private final CheckedConsumer<? super T> m_checked;
	private final FailureHandler<? super T> m_handler;
	
	UncheckedConsumer(CheckedConsumer<? super T> checked, FailureHandler<? super T> handler) {
		Preconditions.checkArgument(checked != null, "CheckedConsumer is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public void accept(T data) {
		try {
			m_checked.accept(data);
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(data, e));
		}
	}

	/**
	 * 주어진 {@code CheckedConsumer}와 실패 처리기를 이용하여 {@code UncheckedConsumer} 객체를 생성한다.
	 *
	 * @param <T>	입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedConsumer} 객체.
	 * @param handler	예외 발생 시 호출될 실패 처리기.
	 * @return		생성된 {@code UncheckedConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked} 또는 {@code handler}가 {@code null}인 경우.
	 */
	public static <T> UncheckedConsumer<T> lift(CheckedConsumer<? super T> checked,
												FailureHandler<? super T> handler) {
		return new UncheckedConsumer<>(checked, handler);
	}

	/**
	 * 실행 중 발생한 예외를 무시하는 {@code UncheckedConsumer} 객체를 생성한다.
	 *
	 * @param <T>	입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedConsumer} 객체.
	 * @return		생성된 {@code UncheckedConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <T> UncheckedConsumer<T> ignore(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	/**
	 * 실행 중 발생한 예외를 검사 예외 선언 없이 그대로 다시 던지는(sneaky throw)
	 * {@code UncheckedConsumer} 객체를 생성한다.
	 *
	 * @param <T>	입력 데이터 타입.
	 * @param checked	감쌀 {@code CheckedConsumer} 객체.
	 * @return		생성된 {@code UncheckedConsumer} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <T> UncheckedConsumer<T> sneakyThrow(CheckedConsumer<? super T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}