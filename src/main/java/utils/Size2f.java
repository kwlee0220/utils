package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2f implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final float m_width;
	private final float m_height;
	
	public Size2f(float width, float height) {
		m_width = width;
		m_height = height;
	}
	
	public float getWidth() {
		return m_width;
	}
	
	public float getHeight() {
		return m_height;
	}
	
	public Size2f divideBy(Size2f size) {
		return new Size2f(m_width / size.m_width, m_height / size.m_height);
	}
	
	public Size2f divideBy(Size2i size) {
		return new Size2f(m_width / size.getWidth(), m_height / size.getHeight());
	}
	
	public Size2i floor() {
		return new Size2i((int)Math.floor(m_width), (int)Math.floor(m_height));
	}
	
	public static Size2f fromString(String str) {
		String[] parts = str.split("x");
		
		return new Size2f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%fx%f", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2f other = (Size2f)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
