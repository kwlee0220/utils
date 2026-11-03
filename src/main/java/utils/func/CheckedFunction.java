package utils.func;


/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Function} 형태의 함수 인터페이스.
 * <p>
 * 표준 {@code Function}으로 바꾸려면 {@link UncheckedFunction}({@code sneakyThrow}·{@code lift} 등)를 쓴다.
 *
 * @param <T>	입력 인자의 타입.
 * @param <R>	함수 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunction<T,R> {
	/**
	 * 주어진 인자에 함수를 적용한 결과를 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	함수 적용 결과.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public R apply(T input) throws Exception;
	
	/**
	 * {@link #apply(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 예외는 던지지 않고 {@link Try#failure(Throwable)}로 반환한다. 단 {@link Error}는 잡지 않고
	 * 그대로 전파된다.
	 *
	 * @param input	입력 인자.
	 * @return	성공하면 함수 결과를 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Exception e ) {
			return Try.failure(e);
		}
	}
	
}
