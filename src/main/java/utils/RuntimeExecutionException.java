package utils;


/**
 * 작업 실행 중 발생한 예외를 감싸 unchecked로 전달하는 예외.
 * <p>
 * 주로 {@link java.util.concurrent.ExecutionException}의 원인 같은 checked 예외를
 * public API 밖으로 내보낼 때 사용한다.
 *
 * @author Kang-Woo Lee
 */
public class RuntimeExecutionException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 원인 예외로 예외를 생성한다.
	 *
	 * @param cause	실행 중 발생한 원인 예외.
	 */
	public RuntimeExecutionException(Throwable cause) {
		super(cause);
	}
}
