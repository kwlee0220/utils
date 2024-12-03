package utils.async;

import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.time.DurationUtils;

import com.google.common.base.Preconditions;

import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class TsBasedReplayExecution<T> extends AbstractLoopExecution<T> {
	private static final Duration OVERHEAD = Duration.ofMillis(10);
	
	private Duration m_interval;
	private Instant m_started;
	private Instant m_firstTs = null;
	
	protected abstract FOption<T> performAction(long loopIndex) throws Exception;
	
	protected TsBasedReplayExecution(Duration interval) {
		m_interval = interval;
	}
	
	public Duration getLoopInterval() {
		return m_interval;
	}
	
	public void setLoopInterval(Duration interval) {
		Preconditions.checkArgument(interval != null && DurationUtils.isPositive(interval));
		
		m_interval = interval;
	}

	@Override
	protected void initializeLoop() throws Exception {
		m_started = Instant.now();
	}

	@Override
	protected void finalizeLoop() throws Exception { }
	
	protected void waitForNextTimestamp(Instant ts) throws InterruptedException {
		if ( m_firstTs == null ) {
			m_firstTs = ts;
		}
		
		Duration elapsed = Duration.between(m_started, Instant.now());
		Duration remains = Duration.between(m_firstTs, ts).minus(elapsed);
		if ( remains.compareTo(OVERHEAD) > 0 ) {
			Thread.sleep(remains.toMillis());
			if ( !isRunning() ) {
				return;
			}
		}
	}
}
