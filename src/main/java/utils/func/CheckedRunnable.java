package utils.func;

import utils.Throwables;

/**
 * {@link Exception}을 던질 수 있는 {@link Runnable} 형태의 함수 인터페이스.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnable {
	/**
	 * 작업을 수행한다.
	 *
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public void run() throws Exception;
	
	/**
	 * {@link #run()}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @return	성공하면 값이 {@code null}인 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<Void> tryRun() {
		try {
			run();
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	/**
	 * 이 작업을 {@link Runnable}로 변환한다.
	 * 반환된 {@link Runnable}은 {@link #run()}이 던진 예외를 {@link Throwables#toRuntimeException(Throwable)}로
	 * 변환하여 던진다. 즉 {@link RuntimeException}과 {@link Error}는 그대로 다시 던지고,
	 * 그 외 검사 예외는 {@link RuntimeException}으로 감싸 던진다.
	 *
	 * @return	이 작업에 위임하는 {@link Runnable}.
	 */
	public default Runnable toRunnable() {
		return () -> {
			try {
				run();
			}
			catch ( Throwable e ) {
				throw Throwables.toRuntimeException(e);
			}
		};
	}
}
