package utils;


/**
 * {@link InterruptedException}을 감싸 unchecked로 전달하는 예외.
 * <p>
 * {@link #getCause()}는 감싼 {@link InterruptedException}을 반환한다. 이 예외를 만드는 것만으로
 * 쓰레드의 interrupt 상태가 복원되지는 않는다.
 *
 * @author Kang-Woo Lee
 */
public class RuntimeInterruptedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 원인 예외로 예외를 생성한다.
	 *
	 * @param cause	감쌀 {@link InterruptedException}.
	 */
	public RuntimeInterruptedException(InterruptedException cause) {
		super(cause);
	}

	/**
	 * 상세 메시지와 원인 예외로 예외를 생성한다.
	 *
	 * @param msg	상세 메시지.
	 * @param cause	감쌀 {@link InterruptedException}.
	 */
	public RuntimeInterruptedException(String msg, InterruptedException cause) {
		super(msg, cause);
	}
	
	public InterruptedException getCause() {
		return (InterruptedException) super.getCause();
	}
}
