package utils.func;


/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.BiConsumer} 형태의 함수 인터페이스.
 *
 * @param <S>	첫 번째 입력 인자의 타입.
 * @param <T>	두 번째 입력 인자의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiConsumer<S,T> {
	/**
	 * 주어진 두 인자로 작업을 수행한다.
	 *
	 * @param input1	첫 번째 입력 인자.
	 * @param input2	두 번째 입력 인자.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public void accept(S input1, T input2) throws Exception;
	
	/**
	 * {@link #accept(Object, Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @param input1	첫 번째 입력 인자.
	 * @param input2	두 번째 입력 인자.
	 * @return	성공하면 값이 {@code null}인 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<Void> tryAccept(S input1, T input2) {
		try {
			accept(input1, input2);
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
