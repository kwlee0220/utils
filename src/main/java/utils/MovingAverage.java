package utils;


/**
 * 지수 가중 이동 평균(EMA)을 계산한다.
 * <p>
 * 첫 관측값은 그대로 평균이 되고, 이후에는 {@code avg = avg * (1 - weight) + value * weight}로
 * 갱신한다. 동기화하지 않으므로 여러 쓰레드에서 함께 쓰면 안 된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MovingAverage {
	private double m_avg;
	private final float m_weight;
	private boolean m_isFirst = true;
	
	/**
	 * 이동 평균 계산기를 생성한다.
	 *
	 * @param weight	새 관측값에 줄 가중치. 보통 0과 1 사이 값이며, 범위는 검사하지 않는다.
	 */
	public MovingAverage(float weight) {
		m_weight = weight;
		m_avg = 0.0;
	}
	
	/**
	 * 현재 이동 평균을 반환한다.
	 *
	 * @return	현재 이동 평균. 아직 관측값이 없으면 {@code 0}.
	 */
	public double get() {
		return m_avg;
	}
	
	/**
	 * 새 관측값을 반영하여 이동 평균을 갱신한다.
	 *
	 * @param value	관측값.
	 * @return	갱신된 이동 평균.
	 */
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
	
	/**
	 * 현재 평균을 초 단위 값으로 보고 {@link UnitUtils#toMillisString(long)} 형식의 문자열로 만든다.
	 * 평균에 1000을 곱해 밀리초로 반올림한 뒤 변환한다.
	 *
	 * @return	시간 형식 문자열 (예: {@code "0.500"}).
	 */
	public String toSecondString() {
		return UnitUtils.toMillisString(Math.round(m_avg * 1000));
	}
}
