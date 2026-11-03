package utils;

import java.util.concurrent.TimeUnit;



/**
*
* 본 클래스는 ThreadSafe하도록 구현되었다.
*
* @author Kang-Woo Lee
*/
public class FramePerSecondMeasure {
	private static final int INITIAL_FRAMES = 10;
	private static final double INITIAL_ALPHA = 0.5;
	
	private final double m_alpha;
	private volatile double m_avgElapsed = 0;	// unsynchronized on purpose
	private volatile double m_avgFps = 0;		// unsynchronized on purpose
	
	private long m_started =-1;
	private int m_frameCount = 0;
	
	/**
	 * 측정기를 생성한다.
	 * <p>
	 * 처음 10개 프레임은 가중치 0.5로, 그 뒤로는 {@code alpha}로 평균 프레임 시간을 지수 이동 평균한다.
	 *
	 * @param alpha	11번째 프레임부터 새 프레임 시간에 줄 가중치. 범위는 검사하지 않는다.
	 */
	public FramePerSecondMeasure(double alpha) {
		m_alpha = alpha;
	}
	
	/**
	 * 평균 초당 프레임 수를 반환한다. {@code 1000 / 평균 프레임 시간(ms)}으로 계산한다.
	 *
	 * @return	평균 FPS. 아직 측정된 프레임이 없으면 {@code 0}이며, 평균 프레임 시간이 0ms이면 무한대.
	 */
	public double getFps() {
		return m_avgFps;
	}
	
	/**
	 * 평균 프레임 처리 시간을 반환한다.
	 *
	 * @return	평균 프레임 시간(밀리초, 반올림). 아직 측정된 프레임이 없으면 {@code 0}.
	 */
	public long getElapsedMillis() {
		return Math.round(m_avgElapsed);
	}
	
	/**
	 * 프레임 시작 시각을 기록한다.
	 *
	 * @return	기록한 시작 시각({@link System#nanoTime()} 값).
	 */
	public long startFrame() {
		return m_started = System.nanoTime();
	}
	
	/**
	 * 마지막 {@link #startFrame()} 이후의 경과 시간에 {@code extraMillis}를 더해 프레임 시간으로 반영한다.
	 * <p>
	 * 시작 시각은 초기화되지 않으므로, {@link #startFrame()} 없이 다시 호출하면 같은 시작 시각부터의
	 * 시간이 또 반영된다. <b>주의:</b> 클래스 설명과 달리 시작 시각과 프레임 수는 동기화 없이 갱신되므로,
	 * 여러 쓰레드가 동시에 프레임을 측정하면 결과가 섞일 수 있다.
	 *
	 * @param extraMillis	경과 시간에 더할 밀리초.
	 * @throws RuntimeException	{@link #startFrame()}이 한 번도 호출되지 않은 경우.
	 */
	public void stopFrame(long extraMillis) {
		if ( m_started < 0 ) {
			throw new RuntimeException("frame not started");
		}
		
		long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - m_started) + extraMillis;
		if ( ++m_frameCount == 1 ) {
			m_avgElapsed = elapsed;
		}
		else if ( m_frameCount <= INITIAL_FRAMES ) {
			m_avgElapsed = (1-INITIAL_ALPHA)*m_avgElapsed + INITIAL_ALPHA*elapsed;
		}
		else {
			m_avgElapsed = (1-m_alpha)*m_avgElapsed + m_alpha*elapsed;
		}
		m_avgFps = 1000.0 / m_avgElapsed;
	}
	
	/**
	 * {@code stopFrame(0)}과 같다.
	 *
	 * @throws RuntimeException	{@link #startFrame()}이 한 번도 호출되지 않은 경우.
	 */
	public void stopFrame() {
		stopFrame(0);
	}
	
	@Override
	public String toString() {
		return String.format("fps=%.1f", m_avgFps);
	}
}
