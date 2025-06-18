package utils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MovingAverage {
	private double m_avg;
	private final float m_weight;
	private boolean m_isFirst = true;
	
	public MovingAverage(float weight) {
		m_weight = weight;
		m_avg = 0.0;
	}
	
	public double get() {
		return m_avg;
	}
	
	public double observe(double value) {
		if ( m_isFirst ) {
			m_avg = value;
			m_isFirst = false;
		}
		else {
			m_avg = m_avg * (1.0 - m_weight) + value * m_weight;
		}
		
		return m_avg;
	}
	
	public String toSecondString() {
		return UnitUtils.toMillisString(Math.round(m_avg * 1000));
	}
}
