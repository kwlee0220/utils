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
public abstract class PeriodicLoopExecution<T> extends AbstractLoopExecution<T> {
	private Duration m_interval;
	private Instant m_started;
	
	protected abstract FOption<T> performPeriodicAction(long loopIndex) throws Exception;
	
	protected PeriodicLoopExecution(Duration interval) {
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
		Preconditions.checkState(m_interval != null);
		
		m_started = Instant.now();
	}

	@Override
	protected FOption<T> iterate(long loopIndex) throws Exception {
		// Loop 인덱스를 이용하여 이번 loop외 due 시간을 계산한다.
		// Due 시간은 interval 기간에 iterate 횟수를 곱해서 전체 지연 시간을 계산해서
		// loop의 시작 시각에 더해서 결정한다.
		Instant due = m_started.plus(m_interval.multipliedBy(loopIndex+1));
		try {
			return performPeriodicAction(loopIndex);
		}
		finally {
			long remainMillis = Duration.between(Instant.now(), due).toMillis();
			if ( remainMillis > 20 ) {
				Thread.sleep(remainMillis);
			}
		}
	}

	@Override
	protected void finalizeLoop() throws Exception { }
}
