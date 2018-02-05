package utils;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2i implements Comparable<Point2i> {
	private final int m_x;
	private final int m_y;
	
	public Point2i(int x, int y) {
		m_x = x;
		m_y = y;
	}
	
	public int getX() {
		return m_x;
	}
	
	public int getY() {
		return m_y;
	}
	
	public Size2i minus(Point2i pt) {
		return new Size2i(m_x - pt.m_x, m_y - pt.m_y);
	}
	
	public static Point2i fromString(String str) {
		String[] parts = str.split("x");
		
		return new Point2i(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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
		Preconditions.checkNotNull(o);
		
		int cmp = Integer.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Integer.compare(m_y, o.m_y);
	}
}
