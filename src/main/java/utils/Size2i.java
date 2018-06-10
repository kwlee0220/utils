package utils;

import java.awt.Dimension;
import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2i {
	private final int m_width;
	private final int m_height;
	
	public Size2i(int width, int height) {
		m_width = width;
		m_height = height;
	}
	
	public int getWidth() {
		return m_width;
	}
	
	public int getHeight() {
		return m_height;
	}
	
	public int getArea() {
		return m_width * m_height;
	}
	
	public Point2i toPoint2i() {
		return new Point2i(m_width, m_height);
	}
	
	public Dimension toDimension() {
		return new Dimension(m_width, m_height);
	}
	
	public static Size2i fromString(String str) {
		String[] parts = str.split("x");
		
		return new Size2i(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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
		
		Size2i other = (Size2i)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
