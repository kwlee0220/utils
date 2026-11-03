package utils;

import org.slf4j.Logger;

/**
 * 사용할 {@link Logger}를 외부에서 지정할 수 있는 객체의 인터페이스.
 *
 * @author Kang-Woo Lee
 */
public interface LoggerSettable {
	/**
	 * 현재 사용 중인 로거를 반환한다.
	 *
	 * @return	로거.
	 */
	public Logger getLogger();

	/**
	 * 사용할 로거를 지정한다.
	 *
	 * @param logger	사용할 로거. {@code null} 처리 방식은 구현 클래스에 따른다.
	 */
	public void setLogger(Logger logger);
}
