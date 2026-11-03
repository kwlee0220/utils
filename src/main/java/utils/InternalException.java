package utils;

import utils.func.FOption;

/**
 * 라이브러리 내부에서 예기치 않은 오류가 발생했음을 나타내는 unchecked 예외.
 * <p>
 * {@link #getMessage()}는 원인 예외가 있으면 메시지 뒤에 {@code ", cause=<원인>"}을 덧붙인다.
 * <b>주의:</b> {@link #InternalException(Throwable)}로 만든 경우 기본 메시지가 이미 원인의
 * {@code toString()}이므로 원인이 메시지에 두 번 나타난다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@SuppressWarnings("serial")
public class InternalException extends RuntimeException {
	/**
	 * 상세 메시지로 예외를 생성한다.
	 *
	 * @param details	오류 상세 메시지.
	 */
	public InternalException(String details) {
		super(details);
	}
	
	/**
	 * 원인 예외로 예외를 생성한다. 상세 메시지는 {@code cause.toString()}이 된다.
	 *
	 * @param cause	원인 예외.
	 */
	public InternalException(Throwable cause) {
		super(cause);
	}
	
	/**
	 * 상세 메시지와 원인 예외로 예외를 생성한다.
	 *
	 * @param details	오류 상세 메시지.
	 * @param cause	원인 예외.
	 */
	public InternalException(String details, Throwable cause) {
		super(details, cause);
	}
	
	@Override
	public String getMessage() {
		String causeStr = FOption.mapOrElse(getCause(), c -> ", cause=" + c, "");
		return String.format("%s%s", super.getMessage(), causeStr);
	}
}
