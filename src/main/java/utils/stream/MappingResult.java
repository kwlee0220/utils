package utils.stream;

import utils.Preconditions;
import utils.func.Try;


/**
 * 비동기 매핑({@link FStream#mapAsyncToTry(utils.func.CheckedFunction, AsyncExecutionOptions) mapAsyncToTry})의
 * 결과 원소. 입력 원소와, 그 입력에 매핑 함수를 적용한 결과({@link Try})를 묶는다.
 * <p>
 * 매핑 함수가 예외를 던졌으면 {@link #result()}는 그 원래 예외를 담은 {@link Try#failure(Throwable)}이다.
 *
 * @param <I>	입력 원소 타입.
 * @param <O>	매핑 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class MappingResult<I,O> {
	private final I m_input;
	private final Try<O> m_result;

	/**
	 * 매핑 결과를 생성한다.
	 *
	 * @param <I>		입력 원소 타입.
	 * @param <O>		매핑 결과 타입.
	 * @param input		입력 원소. {@code null} 허용.
	 * @param result	매핑 결과.
	 * @return	매핑 결과 객체.
	 * @throws IllegalArgumentException	{@code result}가 {@code null}인 경우.
	 */
	public static <I,O> MappingResult<I,O> of(I input, Try<O> result) {
		return new MappingResult<>(input, result);
	}

	private MappingResult(I input, Try<O> result) {
		Preconditions.checkNotNullArgument(result, "result is null");

		m_input = input;
		m_result = result;
	}

	/**
	 * 매핑 함수에 넘긴 입력 원소를 반환한다.
	 *
	 * @return	입력 원소.
	 */
	public I input() {
		return m_input;
	}

	/**
	 * 매핑 결과를 반환한다.
	 *
	 * @return	성공이면 매핑 값을, 실패면 매핑 함수가 던진 원래 예외를 담은 {@link Try}.
	 */
	public Try<O> result() {
		return m_result;
	}

	/**
	 * 매핑이 성공했는지 여부를 반환한다.
	 *
	 * @return	성공이면 {@code true}.
	 */
	public boolean isSuccessful() {
		return m_result.isSuccessful();
	}

	/**
	 * 매핑이 실패했는지 여부를 반환한다.
	 *
	 * @return	실패면 {@code true}.
	 */
	public boolean isFailed() {
		return m_result.isFailed();
	}

	/**
	 * 매핑 값을 반환한다. {@code result().get()}과 같다.
	 * <p>
	 * 매핑이 실패했으면 매핑 함수가 던진 원래 예외를 감싸지 않고 <b>선언 없이</b> 다시 던진다.
	 *
	 * @return	매핑 값.
	 */
	public O get() {
		return m_result.get();
	}

	@Override
	public String toString() {
		return String.format("%s -> %s", m_input, m_result);
	}
}
