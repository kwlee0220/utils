package utils.func;


/**
 * {@link Exception}을 던질 수 있는 {@link Runnable} 형태의 함수 인터페이스.
 * <p>
 * 표준 {@code Runnable}로 바꾸려면 {@link UncheckedRunnable}({@code sneakyThrow}·{@code lift} 등)를 쓴다.
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
	 * 호출 중 발생한 예외는 던지지 않고 {@link Try#failure(Throwable)}로 반환한다. 단 {@link Error}는 잡지 않고
	 * 그대로 전파된다.
	 *
	 * @return	성공하면 값이 {@code null}인 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<Void> tryRun() {
		try {
			run();
			return Try.success(null);
		}
		catch ( Exception e ) {
			return Try.failure(e);
		}
	}
	
}
