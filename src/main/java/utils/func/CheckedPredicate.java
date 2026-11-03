package utils.func;


/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Predicate} 형태의 함수 인터페이스.
 *
 * @param <T>	입력 인자의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedPredicate<T> {
	/**
	 * 주어진 인자가 조건을 만족하는지 판정한다.
	 *
	 * @param data	입력 인자.
	 * @return	조건을 만족하면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public boolean test(T data) throws Exception;
	
	/**
	 * {@link #test(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	성공하면 판정 결과를 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<Boolean> tryTest(T input) {
		try {
			return Try.success(test(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
