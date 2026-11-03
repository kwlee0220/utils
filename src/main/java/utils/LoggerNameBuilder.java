package utils;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.func.Funcs;
import utils.stream.FStream;

/**
 * 마침표({@code .})로 구분된 로거 이름을 부분 이름 목록으로 다루어, 앞부분을 떼거나 뒷부분을 붙이는 식으로
 * 새 로거 이름을 만들고 해당 SLF4J {@link Logger}를 얻는 빌더.
 * <p>
 * 객체는 변경되지 않으며, {@link #append(String)}·{@link #skipPrefix(int)}·{@link #dropSuffix(int)}는
 * 새 빌더를 반환한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LoggerNameBuilder {
	private final List<String> m_parts;
	
	private LoggerNameBuilder(List<String> parts) {
		m_parts = parts;
	}
	
	/**
	 * 주어진 로거의 이름으로 빌더를 만든다.
	 *
	 * @param logger	기준 로거.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(Logger logger) {
		return from(logger.getName());
	}
	
	/**
	 * 로거 이름을 마침표 단위로 나누어 빌더를 만든다. 나누기는 {@link CSV#parseCsvAsArray(String, char)}로 한다.
	 *
	 * @param name	로거 이름.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(String name) {
		return from(CSV.parseCsvAsArray(name, '.'));
	}
	
	/**
	 * 부분 이름 배열로 빌더를 만든다.
	 *
	 * @param nameParts	부분 이름 배열. 배열은 복사된다.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(String[] nameParts) {
		return from(Arrays.asList(nameParts));
	}
	
	/**
	 * 부분 이름 목록으로 빌더를 만든다.
	 *
	 * @param nameParts	부분 이름 목록. 내용은 새 리스트로 복사된다.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(Iterable<String> nameParts) {
		return new LoggerNameBuilder(Lists.newArrayList(nameParts));
	}
	
	/**
	 * 클래스의 정규 이름({@link Class#getName()})으로 빌더를 만든다.
	 *
	 * @param cls	기준 클래스.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(Class<?> cls) {
		return from(cls.getName());
	}
	
	/**
	 * 패키지 이름으로 빌더를 만든다.
	 *
	 * @param pkg	기준 패키지.
	 * @return	생성된 빌더.
	 */
	public static LoggerNameBuilder from(Package pkg) {
		return from(pkg.getName());
	}
	
	/**
	 * 주어진 로거 이름 뒤에 {@code "." + suffix}를 붙인 이름의 로거를 반환한다.
	 *
	 * @param logger	기준 로거.
	 * @param suffix	덧붙일 이름.
	 * @return	새 이름의 로거.
	 */
	public static Logger plus(Logger logger, String suffix) {
		return from(logger.getName() + "." + suffix).getLogger();
	}
	
	/**
	 * 부분 이름들을 마침표로 이어 붙인 이름의 로거를 반환한다.
	 *
	 * @return	로거.
	 */
	public Logger getLogger() {
		return LoggerFactory.getLogger(FStream.from(m_parts).join('.'));
	}
	
	/**
	 * 끝에 부분 이름 하나를 덧붙인 새 빌더를 반환한다. 이 빌더는 바뀌지 않는다.
	 *
	 * @param namePart	덧붙일 부분 이름.
	 * @return	새 빌더.
	 */
	public LoggerNameBuilder append(String namePart) {
		return new LoggerNameBuilder(Funcs.addCopy(m_parts, namePart));
	}
	
	/**
	 * 앞쪽 부분 이름 {@code count}개를 뗀 새 빌더를 반환한다. 이 빌더는 바뀌지 않는다.
	 *
	 * @param count	뗄 부분 이름 개수.
	 * @return	새 빌더.
	 * @throws IndexOutOfBoundsException	{@code count}가 음수이거나 부분 이름 개수보다 큰 경우.
	 */
	public LoggerNameBuilder skipPrefix(int count) {
		return new LoggerNameBuilder(m_parts.subList(count, m_parts.size()));
	}
	
	/**
	 * 뒤쪽 부분 이름 {@code count}개를 뗀 새 빌더를 반환한다. 이 빌더는 바뀌지 않는다.
	 * {@code count}가 부분 이름 개수 이상이면 빈 이름이 된다.
	 *
	 * @param count	뗄 부분 이름 개수.
	 * @return	새 빌더.
	 * @throws IndexOutOfBoundsException	{@code count}가 음수인 경우.
	 */
	public LoggerNameBuilder dropSuffix(int count) {
		int end = Math.max(m_parts.size() - count, 0);
		return new LoggerNameBuilder(m_parts.subList(0, end));
	}
}
