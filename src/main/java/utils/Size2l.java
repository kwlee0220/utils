package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2l implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final long m_width;
	private final long m_height;
	
	public Size2l(long width, long height) {
		m_width = width;
		m_height = height;
	}
	
	public long getWidth() {
		return m_width;
	}
	
	public long getHeight() {
		return m_height;
	}
	
	public Point2l toPoint2l() {
		return new Point2l(m_width, m_height);
	}
	
	public static Size2l fromString(String str) {
		String[] parts = str.split("x");
		
		return new Size2l(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%dx%d", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2l other = (Size2l)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
