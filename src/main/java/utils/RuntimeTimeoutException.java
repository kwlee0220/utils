package utils;

import java.util.concurrent.TimeoutException;

/**
 * {@link TimeoutException}을 감싸 unchecked로 전달하는 예외.
 *
 * @author Kang-Woo Lee
 */
public class RuntimeTimeoutException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 원인 예외로 예외를 생성한다.
	 *
	 * @param e	감쌀 {@link TimeoutException}.
	 */
	public RuntimeTimeoutException(TimeoutException e) {
		super(e);
	}
}
