package utils.async;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.DurationUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

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
	private @Nullable Duration m_timeout = null;
	private @Nullable Instant m_due = null;
	private final boolean m_cumulativeInterval;
	
	/**
	 * Iteration 작업을 수행한다.
	 * <p>
	 * 이 메소드는 {@link #getLoopInterval()} 주기로 반복적으로 호출된다.
	 * 메소드의 수행 결과에 따라 다음 iteration이 수행될지를 결정한다.
	 * <dl>
	 * 	<dt>{@link FOption(T)}:</dt>
	 * 	<dd>원하는 결과가 생산되어 loop을 중단함.</dd>
	 * 	<dt>{@link FOption#empty()}:</dt>
	 * 	<dd>추가 iteration이 필요함.</dd>
	 * 	<dt>null:</dt>
	 * 	<dd>Loop 작업이 중단됨..</dd>
	 * </dl>
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
	 *                             	만일 {@code true}인 경우는 loop의 시작 시각으로부터 매 iteration의
	 *                             	시작 시간을 계산한다. 반대로 {@code false}인 경우는 매 iteratio이
	 *                             	시작하는 시각을 기준으로 다음 번 iteration의 시작 시각을 계산한다.
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
	 * 실행 제한 시간을 반환합니다.
	 *
	 * @return 설정된 실행 제한 시간의 Duration 객체, 설정되지 않은 경우 null
	 */
	public Duration getTimeout() {
		return m_timeout;
	}
	
	/**
	 * 실행 제한 시간을 설정합니다.
	 *
	 * @param timeout 설정할 실행 제한 시간의 Duration 객체,
	 * 					제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음
	 */
	public void setTimeout(@Nullable Duration timeout) {
		m_timeout = timeout;
	}
	
	/**
     * Polling 제한 시각을 반환합니다.
     * 
     * @return 설정된 Polling 제한 시각의 Instant 객체, 설정되지 않은 경우 null
     */
	public Instant getDue() {
		return m_due;
	}
	
	/**
	 * Polling 제한 시각을 설정합니다.
	 * 
	 * @param due 설정할 Polling 제한 시각의 Instant 객체,
	 * 				제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음
	 */
	public void setDue(@Nullable Instant due) {
		m_due = due;
	}

	/**
	 * Loop 작업이 처음으로 시작될 때 호출된다.
	 * <p>
	 * Loop 작업 시작 시에 수행할 초기화 작업을 본 메소드를 override하여 구현한다.
	 * Override한 메소드에서는 반드시 이 메소드를 호출해야 한다.
	 * 이 메소드는 {@link AsyncState#STARTING} 상태인 경우서만 호출되어야 한다.
	 */
	@Override
	protected void initializeLoop() throws Exception {
		m_started = Instant.now();
		
		if ( m_due == null ) {
			m_due = FOption.map(m_timeout, to -> m_started.plus(to));
		}
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
		// 주기 작업을 수행한다.
		FOption<T> result = performPeriodicAction(loopIndex);
		if ( result != null && result.isPresent() ) {
			return result;
		}
		
		Instant now = Instant.now();
		
		// 시간제한이 설정된 경우에는 제한 시간을 넘었는지 검사한다.
		if ( m_due != null ) {
			if ( m_due.compareTo(now) < 0 ) {
				if ( m_timeout != null ) {
					throw new TimeoutException("timeout=" + m_timeout);
				}
				else {
					throw new TimeoutException("due=" + m_due);
				}
			}
		}
		
		// 이번 iteration의 due 시간을 계산한다.
		Instant iterationDue;
		if ( !m_cumulativeInterval ) {
			// 'm_cumulativeInterval'이 false인 경우는 매 iteration마다 시작 시각을 계산해서
			// due 시간을 결정한다.
			iterationDue = now.plus(m_interval);
		}
		else {
			// Loop 인덱스를 이용하여 이번 loop외 due 시간을 계산한다.
			// Due 시간은 interval 기간에 iterate 횟수를 곱해서 전체 지연 시간을 계산해서
			// loop의 시작 시각에 더해서 결정한다.
			iterationDue = m_started.plus(m_interval.multipliedBy(loopIndex+1));
		}
		// 만일 이번 iteration due보다 m_due가 더 이른 경우는 m_due를 iterationDue로 사용한다.
		if ( m_due != null && iterationDue.compareTo(m_due) > 0 ) {
			iterationDue = m_due;
		}
		
		// 다음번 iteration 시작 시각까지 대기한다.
		Date due = Date.from(iterationDue);
		if ( m_aopGuard.awaitCondition(() -> isCancelRequested(), due).andReturn() ) {
			// 취소 요청이 들어온 경우는 loop 종료
			return null;
		}
		
		// Iteration 실행 시간을 다 채운 경우, 다음 번 iteration을 수행하도록 한다.
		return FOption.empty();
	}
}
