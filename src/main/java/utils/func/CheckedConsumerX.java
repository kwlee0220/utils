package utils.func;


/**
 * 던질 수 있는 예외 타입을 타입 인자 {@code X}로 지정한 {@link java.util.function.Consumer} 형태의 함수 인터페이스.
 *
 * @param <T>	입력 인자의 타입.
 * @param <X>	함수가 던질 수 있는 예외 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumerX<T,X extends Throwable> {
	/**
	 * 주어진 인자로 작업을 수행한다.
	 *
	 * @param data	입력 인자.
	 * @throws X	함수 수행 중 오류가 발생한 경우.
	 */
	public void accept(T data) throws X;
	
	/**
	 * {@link #accept(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 예외는 던지지 않고 {@link Try#failure(Throwable)}로 반환한다. 단 {@link Error}는 잡지 않고
	 * 그대로 전파된다.
	 *
	 * @param data	입력 인자.
	 * @return	성공하면 값이 {@code null}인 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<Void> tryAccept(T data) {
		try {
			accept(data);
			return Try.success(null);
		}
		catch ( Throwable e ) {
			if ( e instanceof Error err ) {
				throw err;
			}
			return Try.failure(e);
		}
	}
}