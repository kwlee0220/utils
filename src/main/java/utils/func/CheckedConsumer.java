package utils.func;

import java.util.function.Consumer;

/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Consumer} 형태의 함수 인터페이스.
 *
 * @param <T>	입력 인자의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumer<T> {
	/**
	 * 주어진 인자로 작업을 수행한다.
	 *
	 * @param data	입력 인자.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public void accept(T data) throws Exception;
	
	/**
	 * {@link #accept(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
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
			return Try.failure(e);
		}
	}
	
	/**
	 * {@link java.util.function.Consumer}를 {@link CheckedConsumer}로 변환한다.
	 * 반환된 객체의 {@link #accept(Object)}는 {@code consumer.accept(data)}를 그대로 호출하며,
	 * {@code consumer}가 {@code null}인지는 검사하지 않는다.
	 *
	 * @param <T>	입력 인자의 타입.
	 * @param consumer	변환할 {@link java.util.function.Consumer}.
	 * @return	{@code consumer}에 위임하는 {@link CheckedConsumer}.
	 */
	public static <T> CheckedConsumer<T> fromConsumer(final Consumer<T> consumer) {
		return new CheckedConsumer<T>() {
			@Override
			public void accept(T data) {
				consumer.accept(data);
			}
		};
	}
}