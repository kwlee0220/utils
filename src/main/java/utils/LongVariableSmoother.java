package utils;

import javax.annotation.concurrent.GuardedBy;


/**
 * {@code long} 값의 이동 평균을 구해 값의 변동을 완만하게 만든다.
 * <p>
 * 첫 관측값은 그대로 결과가 되고, 관측값 수가 윈도우 크기에 이를 때까지는 누적 평균, 그 뒤로는
 * 이전 결과의 {@code (1 - 1/windowSize)}와 새 값의 {@code 1/windowSize}를 합하는 방식으로 갱신하며,
 * 가중치는 이 계산에 함께 적용된다. 생성자의 초기 값은 첫 관측 전 {@link #getSmoothed()}에만 보인다.
 * 모든 갱신·조회 메소드는 {@code synchronized}이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LongVariableSmoother {
	private final int m_windowSize;
	private final double m_weight;
	@GuardedBy("this") private int m_size;
	@GuardedBy("this") private long m_smoothed;
	
	/**
	 * {@literal LongVariableSmoother}를 생성한다.
	 * 
	 * @param windowSize	값 smoothing을 위해 유지하는 과거 값의 개수 (현재 값 포함).
	 * @param weight	가장 최근 값에 부여할 가중치. 최근 값에 가중치 값을 곱한 값을 사용하기 때문에
	 * 					가중치 값이 {@literal 1f}인 경우는 가중치가 없게 된다.
	 * @param initValue	초기 값. 
	 */
	public LongVariableSmoother(int windowSize, double weight, long initValue) {
		m_windowSize = windowSize;
		m_weight = weight;
		m_smoothed = initValue;
	}
	
	/**
	 * {@literal LongVariableSmoother}를 생성한다.
	 * <p>
	 * 가중치 값은 1로 설정되고 초기 값은 0으로 설정된다.
	 * 
	 * @param windowSize	값 smoothing을 위해 유지하는 과거 값의 개수 (현재 값 포함).
	 */
	public LongVariableSmoother(int windowSize) {
		this(windowSize, 1f, 0);
	}
	
	/**
	 * 관찰된 새 값을 추가시킨다.
	 * 
	 * @param value	관찰된 새 값
	 * @return	새 값이 고려된 smoothing 값.
	 */
	public synchronized long observe(long value) {	
		if ( m_size == 0 ) {
			m_size = 1;
			
			return m_smoothed = value;
		}
		
		double prevV;
		double newV;
		
		if ( m_size < m_windowSize ) {
			double c = m_size + 1.0;
			
			prevV = m_smoothed * (m_size/c);
			newV = value / c;
			
			++m_size;
		}
		else {
			double c = (double)m_size;
			
			prevV = m_smoothed - m_smoothed/c;
			newV = value / c;
		}
		
		return m_smoothed = Math.round(prevV/m_weight + m_weight*newV);
	}
	
	/**
	 * 현재까지의 smoothing된 값을 반환한다.
	 * 
	 * @return	smoothing된 값
	 */
	public synchronized long getSmoothed() {
		return m_smoothed;
	}
	
	/**
	 * 윈도우 버퍼에 축적된 이전 값을 모두 제거한다.
	 */
	public synchronized void reset() {
		m_size = 0;
		m_smoothed = 0;
	}
	
	public String toString() {
		return "smoother[long:" + m_smoothed + "]";
	}
}
