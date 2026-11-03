package utils.func;


/**
 * 함수 실행 실패 정보를 담는 불변 객체이다.
 * <p>
 * 실패 당시의 입력 데이터와 실패 원인 예외를 함께 보관하며,
 * {@link FailureHandler}에게 전달된다.
 *
 * @param <T>	입력 데이터 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class FailureCase<T> {
	private final T m_data;
	private final Throwable m_cause;
	
	/**
	 * 실패 정보 객체를 생성한다.
	 * <p>
	 * 인자의 {@code null} 여부는 검사하지 않는다.
	 *
	 * @param data	실패 당시의 입력 데이터. 입력이 없는 연산의 경우 {@code null}.
	 * @param cause	실패 원인 예외.
	 */
	public FailureCase(T data, Throwable cause) {
		m_data = data;
		m_cause = cause;
	}
	
	/**
	 * 실패 당시의 입력 데이터를 반환한다.
	 *
	 * @return	입력 데이터. 입력이 없는 연산의 경우 {@code null}.
	 */
	public T getData() {
		return m_data;
	}
	
	/**
	 * 실패 원인 예외를 반환한다.
	 *
	 * @return	실패 원인 예외.
	 */
	public Throwable getFailureCause() {
		return m_cause;
	}
}