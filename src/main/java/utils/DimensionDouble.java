package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DimensionDouble {
	private final double m_width;
	private final double m_height;
	
	public DimensionDouble(double width, double height) {
		m_width = width;
		m_height = height;
	}
	
	public double getWidth() {
		return m_width;
	}
	
	public double getHeight() {
		return m_height;
	}
	
	public static DimensionDouble parseDimension(String str) {
		String[] parts = str.split("x");
		
		return new DimensionDouble(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%fx%f", m_width, m_height);
	}
	
	public String toString(int ndecimal) {
		return String.format("%."+ndecimal+"fx%."+ndecimal+"f", m_width, m_height);
	}
}
