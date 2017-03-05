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
	
	public FramePerSecondMeasure(double alpha) {
		m_alpha = alpha;
	}
	
	public double getFps() {
		return m_avgFps;
	}
	
	public long getElapsedMillis() {
		return Math.round(m_avgElapsed);
	}
	
	public long startFrame() {
		return m_started = System.nanoTime();
	}
	
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
	
	public void stopFrame() {
		stopFrame(0);
	}
	
	@Override
	public String toString() {
		return String.format("fps=%.1f", m_avgFps);
	}
}
