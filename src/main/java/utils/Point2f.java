package utils;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2f implements Comparable<Point2f> {
	private final float m_x;
	private final float m_y;
	
	public Point2f(float x, float y) {
		m_x = x;
		m_y = y;
	}
	
	public float getX() {
		return m_x;
	}
	
	public float getY() {
		return m_y;
	}
	
	public Size2f minus(Point2f pt) {
		return new Size2f(m_x - pt.m_x, m_y - pt.m_y);
	}
	
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
		Objects.requireNonNull(o);
		
		int cmp = Float.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Float.compare(m_y, o.m_y);
	}
}
