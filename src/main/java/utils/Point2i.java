package utils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Point2i implements Comparable<Point2i> {
	private static final Pattern PATTERN = Pattern.compile("\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
	
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
		Objects.requireNonNull(o);
		
		int cmp = Integer.compare(m_x, o.m_x);
		if ( cmp != 0 ) {
			return cmp;
		}
		
		return Integer.compare(m_y, o.m_y);
	}
}
