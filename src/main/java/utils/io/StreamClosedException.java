package utils.io;

import java.io.IOException;

/**
 * 이미 닫힌 스트림에 대해 연산을 시도한 경우 발생하는 예외이다.
 * @author Kang-Woo Lee (ETRI)
 */
public class StreamClosedException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * 주어진 상세 메시지로 예외 객체를 생성한다.
	 *
	 * @param details	예외 상세 메시지.
	 */
	public StreamClosedException(String details) {
		super(details);
	}
}
