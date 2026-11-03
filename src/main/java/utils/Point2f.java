package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 실수({@code float}) 좌표로 이루어진 불변(immutable) 2차원 점.
 * {@link #compareTo(Point2f)}는 x 좌표를 먼저, 같으면 y 좌표를 {@link Float#compare(float, float)}로 비교한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2f implements Comparable<Point2f>, Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * x 좌표.
	 */
	private final float m_x;
	/**
	 * y 좌표.
	 */
	private final float m_y;
	
	/**
	 * 주어진 좌표로 점 객체를 생성한다.
	 *
	 * @param x	x 좌표.
	 * @param y	y 좌표.
	 */
	public Point2f(float x, float y) {
		m_x = x;
		m_y = y;
	}
	
	/**
	 * x 좌표을(를) 반환한다.
	 *
	 * @return	x 좌표.
	 */
	public float getX() {
		return m_x;
	}
	
	/**
	 * y 좌표을(를) 반환한다.
	 *
	 * @return	y 좌표.
	 */
	public float getY() {
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
	public Size2f minus(Point2f pt) {
		return new Size2f(m_x - pt.m_x, m_y - pt.m_y);
	}
	
	/**
	 * {@code "XxY"} 형식의 문자열(예: {@code "1.5x2.0"})을 파싱하여 점 객체를 만든다.
	 * <p>
	 * 소문자 {@code 'x'}로 나눈 앞의 두 부분을 각각 {@link Float#parseFloat(String)}로 변환하며,
	 * 세 번째 이후 부분은 무시한다.
	 * <b>주의:</b> {@link #toString()}이 만드는 {@code "(x,y)"} 형식은 파싱하지 못한다.
	 *
	 * @param str	파싱할 문자열.
	 * @return	파싱된 점 객체.
	 * @throws NumberFormatException	x 또는 y 부분이 숫자가 아닌 경우.
	 * @throws ArrayIndexOutOfBoundsException	문자열에 {@code 'x'}가 없는 경우.
	 * @throws NullPointerException	{@code str}이 {@code null}인 경우.
	 */
	public static Point2f fromString(String str) {
		String[] parts = str.split("x");
		
		return new Point2f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("(%f,%f)", m_x, m_y);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Point2f other = (Point2f)obj;
		return Objects.equals(m_x, other.m_x) && Objects.equals(m_y, other.m_y);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_x, m_y);
	}

	@Override
	public int compareTo(Point2f o) {
		Preconditions.checkNotNullArgument(o, "other point is null");
		
		int cmp = Float.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Float.compare(m_y, o.m_y);
	}
}
