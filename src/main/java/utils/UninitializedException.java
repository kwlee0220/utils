package utils;


/**
 * {@literal UninitializedException}는 초기화가 완료되지 않은
 * 서비스를 사용할 때 발생되는 예외를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UninitializedException extends RuntimeException {
	private static final long serialVersionUID = -7041730506281758400L;

	public UninitializedException(String name) {
		super(name);
	}
}