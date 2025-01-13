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
public abstract class FixedIntervalLoopExecution<T> extends AbstractLoopExecution<T> {
	private static final Duration OVERHEAD = Duration.ofMillis(10);
	
	private Duration m_interval;
	private Instant m_started;
	
	protected abstract FOption<T> performAction(long loopIndex) throws Exception;
	
	protected FixedIntervalLoopExecution(Duration interval) {
		m_interval = interval;
	}
	protected FixedIntervalLoopExecution() {
		this(null);
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
		Preconditions.checkArgument(m_interval != null, "Null LoopInterval");
		
		m_started = Instant.now();
	}

	@Override
	protected void finalizeLoop() throws Exception { }

	@Override
	protected final FOption<T> iterate(long loopIndex) throws Exception {
		// 현재 시각을 기준은 다음 번 iteration을 수행할 시각을 계산하고 대기한다.
		waitForNextTimestamp(loopIndex);
		
		return performAction(loopIndex);
	}
	
	protected void waitForNextTimestamp(long loopIndex) throws InterruptedException {
		if ( loopIndex == 0 ) {
			// 첫번째 iteration인 경우에는 sleep없이 바로 반환한다.
			return;
		}
		
		// 다음번 iteration을 수행할 시각을 계산하고, 현재 시각을 기준으로 남은 시간을 계산한다.
		Instant nextStartTime = m_started.plus(m_interval.multipliedBy(loopIndex + 1));
		Duration remains = Duration.between(Instant.now(), nextStartTime);
		
		// 남은 시간이 sleep overhead보다 큰 경우에만 sleep을 수행한다.
		if ( remains.compareTo(OVERHEAD) > 0 ) {
			Thread.sleep(remains.toMillis());
			
			// 깨어났을 때, execution 종료 여부를 확인한다.
			if ( !isRunning() ) {
				return;
			}
		}
	}
}
