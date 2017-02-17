package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DimensionLong {
	private final long m_width;
	private final long m_height;
	
	public DimensionLong(long width, long height) {
		m_width = width;
		m_height = height;
	}
	
	public long getWidth() {
		return m_width;
	}
	
	public long getHeight() {
		return m_height;
	}
	
	public long getLength() {
		return m_width * m_height;
	}
	
	public static DimensionLong parseDimension(String str) {
		String[] parts = str.split("x");
		
		return new DimensionLong(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%dx%d", m_width, m_height);
	}
}
