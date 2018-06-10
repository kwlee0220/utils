package utils;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2d {
	private final double m_width;
	private final double m_height;
	
	public Size2d(double width, double height) {
		m_width = width;
		m_height = height;
	}
	
	public double getWidth() {
		return m_width;
	}
	
	public double getHeight() {
		return m_height;
	}
	
	public Size2d divideBy(Size2d size) {
		return new Size2d(m_width / size.m_width, m_height / size.m_height);
	}
	
	public Size2d divideBy(Size2i size) {
		return new Size2d(m_width / size.getWidth(), m_height / size.getHeight());
	}
	
	public Size2i floorToInt() {
		return new Size2i((int)Math.floor(m_width), (int)Math.floor(m_height));
	}
	
	public Size2i ceilToInt() {
		return new Size2i((int)Math.ceil(m_width), (int)Math.ceil(m_height));
	}
	
	public static Size2d fromString(String str) {
		String[] parts = str.split("x");
		
		double x = Double.parseDouble(parts[0].trim());
		double y = Double.parseDouble(parts[1].trim());
		return new Size2d(x, y);
	}
	
	@Override
	public String toString() {
		return String.format("%fx%f", m_width, m_height);
	}
	
	public String toString(int ndecimal) {
		return String.format("%."+ndecimal+"fx%."+ndecimal+"f", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2d other = (Size2d)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
