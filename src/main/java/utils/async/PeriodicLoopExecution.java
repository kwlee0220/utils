package utils.async;

import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.time.DurationUtils;

import com.google.common.base.Preconditions;

import utils.func.FOption;


/**
 * 주어진 iteration 작업을 주어진 주기로 반복적으로 수행하는 {@link StartableExecution}을 구현한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PeriodicLoopExecution<T> extends AbstractLoopExecution<T> {
	private Duration m_interval;
	private Instant m_started;
	private boolean m_cumulativeInterval = false;
	
	/**
	 * Iteration 작업을 수행한다.
	 * <p>
	 * 이 메소드는 {@link #getLoopInterval()} 주기로 반복적으로 호출된다.
	 * 메소드의 수행 결과에 따라 다음 iteration이 수행될지를 결정한다.
	 * 수행 결과가 {@link FOption#empty()}가 아닌 경우는 전체 loop 가 종료된다.
	 * 반대로 수행 결과가 {@code null}이거나 {@link FOption#empty()}인 경우는
	 * 추가 iteration이 필요하다는 의미이다.
	 * 
	 * @return	iteration 작업의 수행 결과.
	 * @throws Exception	iteration 작업 중 예외가 발생한 경우.
	 */
	protected abstract FOption<T> performPeriodicAction(long loopIndex) throws Exception;
	
	/**
	 * 주기적인 loop 작업을 수행하는 객체를 생성한다.
	 * 
	 * @param interval 주기
	 * @param cumulativeInterval	주기가 누적되는지 여부.
	 *                             만일 {@code true}인 경우는 loop의 시작 시각으로부터 매 iteration의
	 *                             시작 시간을 계산한다. 반대로 {@code false}인 경우는 매 iteratio이
	 *                             시작하는 시각을 기준으로 다음 번 iteration의 시작 시각을 계산한다.
	 */
	protected PeriodicLoopExecution(Duration interval, boolean cumulativeInterval) {
		Preconditions.checkArgument(interval != null && DurationUtils.isPositive(interval));
		
		m_interval = interval;
		m_cumulativeInterval = cumulativeInterval;
	}

	/**
	 * 주기적인 loop 작업을 수행하는 객체를 생성한다.
	 * 
	 * @param interval 주기
	 */
	protected PeriodicLoopExecution(Duration interval) {
		this(interval, false);
	}
	
	/**
	 * 설정된 loop 주기를 반환한다.
	 * 
	 * @return	loop 주기.
     */
	public Duration getLoopInterval() {
		return m_interval;
	}
	
	/**
	 * loop 주기를 설정한다.
	 * 
	 * @param interval 설정할 loop 주기.
	 */
	public void setLoopInterval(Duration interval) {
		Preconditions.checkArgument(interval != null && DurationUtils.isPositive(interval));
		
		m_interval = interval;
	}

	/**
	 * Loop 작업이 처음으로 시작될 때 호출된다.
	 * <p>
	 * Loop 작업 시작 시에 수행할 초기화 작업을 본 메소드를 override하여 구현한다.
	 * Override한 메소드에서는 반드시 이 메소드를 호출해야 한다.
	 */
	@Override
	protected void initializeLoop() throws Exception {
		m_started = Instant.now();
	}

	/**
	 * Loop 작업이 종료된 후 필요한 종료 작업을 수행한다.
	 * <p>
	 * Loop 작업 종료 시에 수행할 종료 작업을 본 메소드를 override하여 구현한다.
	 * Override한 메소드에서는 반드시 이 메소드를 호출해야 한다.
	 */
	@Override
	protected void finalizeLoop() throws Exception { }

	@Override
	protected FOption<T> iterate(long loopIndex) throws Exception {
		Instant due;
		if ( !m_cumulativeInterval ) {
			// 'm_cumulativeInterval'이 false인 경우는 매 iteration마다 시작 시각을 계산해서
			// due 시간을 결정한다.
			due = Instant.now().plus(m_interval);
		}
		else {
			// Loop 인덱스를 이용하여 이번 loop외 due 시간을 계산한다.
			// Due 시간은 interval 기간에 iterate 횟수를 곱해서 전체 지연 시간을 계산해서
			// loop의 시작 시각에 더해서 결정한다.
			due = m_started.plus(m_interval.multipliedBy(loopIndex+1));
		}
		
		FOption<T> result = performPeriodicAction(loopIndex);
		if ( result == null || result.isAbsent() ) {
			// 다음번 iteration 시작 시각까지 대기한다.
			long remainMillis = Duration.between(Instant.now(), due).toMillis();
			if ( remainMillis > 20 ) {
				Thread.sleep(remainMillis);
			}
		}
		
		return result;
	}
}
