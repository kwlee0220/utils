package utils.func;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@link CheckedSupplier}를
 * 표준 {@link java.util.function.Supplier} 인터페이스로 감싸는 어댑터 클래스이다.
 * <p>
 * {@link #get()} 수행 중 발생한 모든 예외({@link Throwable})는 데이터 {@code null}과 함께
 * {@link FailureCase}로 묶여 생성 시 지정된 {@link FailureHandler}에게 전달된다.
 * <p>
 * <b>주의:</b> 반환 값이 있는 연산이므로, 실패 처리기가 예외를 던지지 않고 정상적으로 반환하면
 * {@code get}는 {@link AssertionError}("Should not be here")를 던진다.
 * 따라서 {@link #ignore(CheckedSupplier)}로 생성한 객체도 예외가 발생하면 실제로는 {@code AssertionError}를 던진다.
 *
 * @param <T>	생성 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedSupplier<T> implements Supplier<T> {
	private final CheckedSupplier<? extends T> m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedSupplier(CheckedSupplier<? extends T> checked, FailureHandler<Void> handler) {
		Preconditions.checkArgument(checked != null, "CheckedSupplier is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}

	@Override
	public T get() {
		try {
			return m_checked.get();
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
			throw new AssertionError("Should not be here");
		}
	}

	/**
	 * 주어진 {@code CheckedSupplier}와 실패 처리기를 이용하여 {@code UncheckedSupplier} 객체를 생성한다.
	 *
	 * @param <T>	생성 값의 타입.
	 * @param checked	감쌀 {@code CheckedSupplier} 객체.
	 * @param handler	예외 발생 시 호출될 실패 처리기.
	 * @return		생성된 {@code UncheckedSupplier} 객체.
	 * @throws IllegalArgumentException	{@code checked} 또는 {@code handler}가 {@code null}인 경우.
	 */
	public static <T> UncheckedSupplier<T> lift(CheckedSupplier<? extends T> checked,
												FailureHandler<Void> handler) {
		return new UncheckedSupplier<>(checked, handler);
	}

	/**
	 * 실행 중 발생한 예외를 무시하는 {@code UncheckedSupplier} 객체를 생성한다.
	 * <p>
	 * <b>주의:</b> 실패 처리기가 예외를 무시하더라도 반환할 값이 없으므로,
	 * 예외가 발생하면 {@code get}는 {@link AssertionError}를 던진다.
	 *
	 * @param <T>	생성 값의 타입.
	 * @param checked	감쌀 {@code CheckedSupplier} 객체.
	 * @return		생성된 {@code UncheckedSupplier} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <T> UncheckedSupplier<T> ignore(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	/**
	 * 실행 중 발생한 예외를 검사 예외 선언 없이 그대로 다시 던지는(sneaky throw)
	 * {@code UncheckedSupplier} 객체를 생성한다.
	 *
	 * @param <T>	생성 값의 타입.
	 * @param checked	감쌀 {@code CheckedSupplier} 객체.
	 * @return		생성된 {@code UncheckedSupplier} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static <T> UncheckedSupplier<T> sneakyThrow(CheckedSupplier<? extends T> checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}