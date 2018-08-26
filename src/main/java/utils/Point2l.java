package utils;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2l implements Comparable<Point2l> {
	private final long m_x;
	private final long m_y;
	
	public Point2l(long x, long y) {
		m_x = x;
		m_y = y;
	}
	
	public long getX() {
		return m_x;
	}
	
	public long getY() {
		return m_y;
	}
	
	public Size2l minus(Point2l pt) {
		return new Size2l(m_x - pt.m_x, m_y - pt.m_y);
	}
	
	public static Point2l fromString(String str) {
		String[] parts = str.split("x");
		
		return new Point2l(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
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
		
		Point2l other = (Point2l)obj;
		return Objects.equals(m_x, other.m_x) && Objects.equals(m_y, other.m_y);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_x, m_y);
	}

	@Override
	public int compareTo(Point2l o) {
		Objects.requireNonNull(o);
		
		int cmp = Long.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Long.compare(m_y, o.m_y);
	}
}
