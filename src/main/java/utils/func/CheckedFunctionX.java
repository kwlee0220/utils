package utils.func;


/**
 * 던질 수 있는 예외 타입을 타입 인자 {@code X}로 지정한 {@link java.util.function.Function} 형태의 함수 인터페이스.
 *
 * @param <T>	입력 인자의 타입.
 * @param <R>	함수 결과 타입.
 * @param <X>	함수가 던질 수 있는 예외 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunctionX<T,R,X extends Throwable> {
	/**
	 * 주어진 인자에 함수를 적용한 결과를 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	함수 적용 결과.
	 * @throws X	함수 수행 중 오류가 발생한 경우.
	 */
	public R apply(T input) throws X;
	
	/**
	 * {@link #apply(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	성공하면 함수 결과를 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<? extends R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
