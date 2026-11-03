package utils;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 정수({@code int}) 좌표로 이루어진 불변(immutable) 2차원 점.
 * {@link #compareTo(Point2i)}는 x 좌표를 먼저, 같으면 y 좌표를 비교한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2i implements Comparable<Point2i>, Serializable {
	private static final long serialVersionUID = 1L;
	private static final Pattern PATTERN = Pattern.compile("\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
	
	/**
	 * x 좌표.
	 */
	private final int m_x;
	/**
	 * y 좌표.
	 */
	private final int m_y;
	
	/**
	 * 주어진 좌표로 점 객체를 생성한다.
	 *
	 * @param x	x 좌표.
	 * @param y	y 좌표.
	 */
	public Point2i(int x, int y) {
		m_x = x;
		m_y = y;
	}
	
	/**
	 * x 좌표을(를) 반환한다.
	 *
	 * @return	x 좌표.
	 */
	public int getX() {
		return m_x;
	}
	
	/**
	 * y 좌표을(를) 반환한다.
	 *
	 * @return	y 좌표.
	 */
	public int getY() {
		return m_y;
	}
	
	/**
	 * 이 점에서 주어진 점을 뺀 x, y 좌표 차이를 크기 객체로 반환한다.
	 * 결과의 폭과 높이는 음수일 수 있다.
	 *
	 * @param pt	뺄 점.
	 * @return	좌표 차이 ({@code this.x - pt.x}, {@code this.y - pt.y}).
	 * @throws NullPointerException	{@code pt}가 {@code null}인 경우.
	 */
	public Size2i minus(Point2i pt) {
		return new Size2i(m_x - pt.m_x, m_y - pt.m_y);
	}
	
	/**
	 * {@code "(x,y)"} 형식의 문자열(예: {@code "(10, 20)"})을 파싱하여 점 객체를 만든다.
	 * <p>
	 * 괄호와 쉼표 주변의 공백은 허용하며, 문자열 안에서 이 형식에 맞는 첫 부분을 찾아 사용하므로
	 * 앞뒤에 다른 문자가 있어도 된다.
	 * <b>주의:</b> 좌표는 부호 없는 숫자만 인식하므로, {@link #toString()}이 음수 좌표에 대해 만드는
	 * {@code "(-1,2)"} 같은 문자열은 파싱하지 못한다.
	 *
	 * @param str	파싱할 문자열.
	 * @return	파싱된 점 객체.
	 * @throws IllegalArgumentException	문자열에서 {@code "(x,y)"} 형식을 찾지 못한 경우.
	 * @throws NumberFormatException	좌표 값이 {@code int} 범위를 넘는 경우.
	 * @throws NullPointerException	{@code str}이 {@code null}인 경우.
	 */
	public static Point2i fromString(String str) {
		Matcher matcher = PATTERN.matcher(str);
		if ( !matcher.find() ) {
			throw new IllegalArgumentException(String.format("invalid: '%s'", str));
		}
		
		int x = Integer.parseInt(matcher.group(1));
		int y = Integer.parseInt(matcher.group(2));
		
		return new Point2i(x, y);
	}
	
	@Override
	public String toString() {
		return String.format("(%d,%d)", m_x, m_y);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Point2i other = (Point2i)obj;
		return Objects.equals(m_x, other.m_x) && Objects.equals(m_y, other.m_y);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_x, m_y);
	}

	@Override
	public int compareTo(Point2i o) {
		Preconditions.checkNotNullArgument(o, "other point is null");
		
		int cmp = Integer.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Integer.compare(m_y, o.m_y);
	}
}
