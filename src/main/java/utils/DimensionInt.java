package utils;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DimensionInt {
	private final int m_width;
	private final int m_height;
	
	public DimensionInt(int width, int height) {
		m_width = width;
		m_height = height;
	}
	
	public int getWidth() {
		return m_width;
	}
	
	public int getHeight() {
		return m_height;
	}
	
	public int getLength() {
		return m_width * m_height;
	}
	
	public static DimensionInt parseDimension(String str) {
		String[] parts = str.split("x");
		
		return new DimensionInt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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
		
		DimensionInt other = (DimensionInt)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
