package utils.func;

import com.google.common.base.Preconditions;


/**
 * 검사 예외(checked exception)를 던질 수 있는 {@link CheckedRunnable}를
 * 표준 {@link java.lang.Runnable} 인터페이스로 감싸는 어댑터 클래스이다.
 * <p>
 * {@link #run()} 수행 중 발생한 모든 예외({@link Throwable})는 데이터 {@code null}과 함께
 * {@link FailureCase}로 묶여 생성 시 지정된 {@link FailureHandler}에게 전달된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedRunnable implements Runnable {
	private final CheckedRunnable m_checked;
	private final FailureHandler<Void> m_handler;
	
	UncheckedRunnable(CheckedRunnable checked, FailureHandler<Void> handler) {
		Preconditions.checkArgument(checked != null, "CheckedRunnable is null");
		Preconditions.checkArgument(handler != null, "FailureHandler is null");
		
		m_checked = checked;
		m_handler = handler;
	}
	
	/**
	 * 예외 발생 시 호출되는 실패 처리기를 반환한다.
	 *
	 * @return	실패 처리기.
	 */
	public FailureHandler<Void> getFailureHandler() {
		return m_handler;
	}

	@Override
	public void run() {
		try {
			m_checked.run();
		}
		catch ( Throwable e ) {
			m_handler.handle(new FailureCase<>(null, e));
		}
	}

	/**
	 * 주어진 {@code CheckedRunnable}와 실패 처리기를 이용하여 {@code UncheckedRunnable} 객체를 생성한다.
	 *
	 * @param checked	감쌀 {@code CheckedRunnable} 객체.
	 * @param handler	예외 발생 시 호출될 실패 처리기.
	 * @return		생성된 {@code UncheckedRunnable} 객체.
	 * @throws IllegalArgumentException	{@code checked} 또는 {@code handler}가 {@code null}인 경우.
	 */
	public static UncheckedRunnable lift(CheckedRunnable checked, FailureHandler<Void> handler) {
		return new UncheckedRunnable(checked, handler);
	}

	/**
	 * 실행 중 발생한 예외를 무시하는 {@code UncheckedRunnable} 객체를 생성한다.
	 *
	 * @param checked	감쌀 {@code CheckedRunnable} 객체.
	 * @return		생성된 {@code UncheckedRunnable} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static UncheckedRunnable ignore(CheckedRunnable checked) {
		return lift(checked, FailureHandlers.ignore());
	}

	/**
	 * 실행 중 발생한 예외를 검사 예외 선언 없이 그대로 다시 던지는(sneaky throw)
	 * {@code UncheckedRunnable} 객체를 생성한다.
	 *
	 * @param checked	감쌀 {@code CheckedRunnable} 객체.
	 * @return		생성된 {@code UncheckedRunnable} 객체.
	 * @throws IllegalArgumentException	{@code checked}가 {@code null}인 경우.
	 */
	public static UncheckedRunnable sneakyThrow(CheckedRunnable checked) {
		return lift(checked, FailureHandlers.throwSneakly());
	}
}