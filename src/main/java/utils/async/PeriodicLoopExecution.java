package utils.async;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.FOption;


/**
 * 주어진 iteration 작업을 지정된 주기로 반복적으로 수행하는 {@link StartableExecution}을 구현한다.
 * <p>
 * 본 execution을 사용하기 위해서는 다음의 메소드를 override하여 구현할 수 있다.
 * <ul>
 *  <li>{@link #initializeLoop()}: loop 작업이 처음으로 시작될 때 호출된다.
 *  Loop 작업 시작 시에 수행할 초기화 작업을 구현한다. 본 메소드를 override한 경우에는 반드시
 *  {@code super.initializeLoop()} 메소드를 호출해야 한다.
 *  <li>{@link #finalizeLoop()}: loop 작업이 종료된 후 필요한 종료 작업을 수행한다.
 *  Loop 작업 종료 시에 수행할 종료 작업을 구현한다. 본 메소드를 override한 경우에는 반드시
 *  {@code super.finalizeLoop()} 메소드를 호출해야 한다.
 * 	<li>{@link #performPeriodicAction(long)}: 주기적으로 반복 수행할 iteration 작업을 구현한다.
 *  이 메소드는 {@link #getLoopInterval()} 주기로 반복적으로 호출되고, 반드시 구현해야 한다.
 * </ul>
 *
 * <h3>선택 옵션</h3>
 * 다음 옵션들은 loop 시작 전에만 설정할 수 있으며, 동작에 큰 영향을 준다.
 * <ul>
 *  <li>{@code cumulativeInterval} (생성자 인자):
 *  	{@code true}이면 Loop 연산이 시작된 시간을 기준으로 interval 시간을 누적하여
 *  	iteration의 종료 시각을 계산한다. 만일 한 iteration의 수행 시간이 interval 보다 긴 경우에는
 *  	다음 iteration이 대기 없이 곧바로 시작된다(catch-up).
 *  	{@code false}이면 이번 iteration 시작 시각에서 interval 시간을 더해서 iteration의
 *  	종료 시각을 계산한다.
 *  <li>{@link #setTimeout(Duration)}: 전체 loop 실행 제한 시간을 설정한다. 제한 시간이 지나
 *  새로운 iteration을 시작하려는 시점에 {@link TimeoutException}을 발생시킨다.
 *  <li>{@link #setDue(Instant)}: loop 마감 시각을 직접 지정한다. 설정된 경우
 *  {@code setTimeout}은 무시된다.
 * </ul>
 * 위 옵션들로 인해 loop이 비정상 종료되는 경우, {@link #iterate(long)}에서
 * {@link TimeoutException} 또는 {@link CancellationException}이 던져진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PeriodicLoopExecution<T> extends AbstractLoopExecution<T> {
	private final Duration m_interval;
	private @Nullable Instant m_started = null;
	private @Nullable Duration m_timeout = null;
	private @Nullable Instant m_due = null;
	private final boolean m_cumulativeInterval;
	
	/**
	 * Iteration 작업을 수행한다.
	 * <p>
	 * 이 메소드는 {@link #getLoopInterval()} 주기로 반복적으로 호출된다.
	 * 메소드의 수행 결과에 따라 다음 iteration이 수행될지를 결정한다.
	 * <dl>
	 * 	<dt>값이 있는 {@link FOption}:</dt>
	 * 	<dd>원하는 결과가 생산되어 loop을 중단함.</dd>
	 * 	<dt>{@link FOption#empty()}:</dt>
	 * 	<dd>추가 iteration이 필요함.</dd>
	 * </dl>
	 * 작업이 중단되어야 할 때는 {@link InterruptedException} 또는 {@link CancellationException}을
	 * 직접 throw 한다.
	 * <p>
	 * <b>취소(cancel) 협력 계약:</b> 상위 클래스의 계약에 따라, 한 번의 호출이 길어지면 그 동안 들어온
	 * {@code cancelWork()}는 본 메소드가 반환할 때까지 대기하게 된다. 빠른 취소가 필요한 경우 본 메소드
	 * 내부에서 주기적으로 {@link #isCancelRequested()}를 검사하여 {@code true}이면
	 * {@link CancellationException}을 던져 종료하거나, 작업 단위를 충분히 잘게 분할해야 한다.
	 *
	 * @param loopIndex	loop 인덱스. 0부터 시작하여 매 iteration마다 1씩 증가한다.
	 * @return	iteration 작업의 수행 결과.
	 * @throws InterruptedException		작업이 인터럽트된 경우.
	 * @throws CancellationException	작업이 취소된 경우.
	 * @throws TimeoutException			작업이 시간 제한을 넘긴 경우.
	 * @throws ExecutionException		비동기 작업의 결과가 실패한 경우.
	 */
	protected abstract FOption<T> performPeriodicAction(long loopIndex)
		throws InterruptedException, CancellationException, TimeoutException, ExecutionException;
	
	/**
	 * 주기적인 loop 작업을 수행하는 객체를 생성한다.
	 * 
	 * @param interval 주기
	 * @param cumulativeInterval	주기가 누적되는지 여부.
	 *                             	만일 {@code true}인 경우는 loop의 시작 시각으로부터 매 iteration의
	 *                             	시작 시간을 계산한다. 반대로 {@code false}인 경우는 매 iteration이
	 *                             	시작하는 시각을 기준으로 다음 번 iteration의 시작 시각을 계산한다.
	 *                             	{@code true}인 경우, 한 iteration이 주기보다 오래 걸리면 다음 iteration은
	 *                             	대기 없이 곧바로 시작된다(catch-up).
	 */
	protected PeriodicLoopExecution(Duration interval, boolean cumulativeInterval) {
		Preconditions.checkArgument(interval != null && DurationUtils.isPositive(interval),
								"invalid interval: %s", interval);
		
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
	 * 실행 제한 시간을 반환한다.
	 *
	 * @return 설정된 실행 제한 시간의 Duration 객체, 설정되지 않은 경우 null
	 */
	public Duration getTimeout() {
		return m_timeout;
	}

	/**
	 * 실행 제한 시간을 설정한다.
	 * <p>
	 * Loop 실행 제한 시간은 새로운 loop iteration이 시작될 수 없는 시간을 의미한다.
	 * 이 시간은 {@link #setDue(Instant)}를 통해 마감 시각이 설정되지 않은 경우에만 적용된다.
	 * Loop 작업이 시작된 이후에는 실행 제한 시간을 변경할 수 없다.
	 *
	 * @param timeout 설정할 실행 제한 시간의 Duration 객체,
	 * 					제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음.
	 * 					non-null인 경우 양수여야 한다.
	 */
	public void setTimeout(@Nullable Duration timeout) {
		Preconditions.checkState(isNotStarted(), "cannot set timeout after execution is started: %s", this);
		Preconditions.checkArgument(timeout == null || DurationUtils.isPositive(timeout),
								"invalid timeout: %s", timeout);

		m_timeout = timeout;
	}

	/**
	 * Loop 연산 종료 시각을 반환한다.
	 *
	 * @return 설정된 Loop 연산 종료 시각의 Instant 객체.
	 * 			연산이 시작되기 전에는 {@link #setDue(Instant)}를 통해 설정되지 않은 경우 null.
	 * 			연산이 시작된 이후에는 {@link #setDue(Instant)} 호출 여부와 무관하게 연산 종료 시각.
	 */
	public Instant getDue() {
		return m_due;
	}

	/**
	 * Loop 연산 종료 시각을 설정한다.
	 * <p>
	 * 마감 시각은 매 iteration의 시작 직전과 iteration 작업 완료 직후 두 시점에 검사되며,
	 * 그 시점에 이미 과거인 경우 즉시 {@link TimeoutException}이 발생하고 loop이 종료된다.
	 * Loop 작업이 시작된 이후에는 마감 시각을 변경할 수 없다.
	 *
	 * @param due 설정할 루프 마감 시각의 Instant 객체,
	 * 				제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음
	 */
	public void setDue(@Nullable Instant due) {
		Preconditions.checkState(isNotStarted(), "cannot set due after execution is started: %s", this);

		m_due = due;
	}

	/**
	 * Loop 작업이 처음으로 시작될 때 호출된다.
	 * <p>
	 * Loop 작업 시작 시에 수행할 초기화 작업을 본 메소드를 override하여 구현한다.
	 * Override한 메소드에서는 반드시 이 메소드를 호출해야 한다.
	 */
	@Override
	protected void initializeLoop() throws Exception {
		Instant started = Instant.now();
		m_started = started;

		if ( m_due == null ) {
			m_due = FOption.map(m_timeout, to -> started.plus(to));
		}

		if ( m_due != null && m_due.isBefore(started) ) {
			throw new TimeoutException(m_timeout != null ? "timeout=" + m_timeout : "due=" + m_due);
		}
	}

	/**
	 * Loop 작업이 종료된 후 필요한 종료 작업을 수행한다.
	 * <p>
	 * Loop 작업 종료 시에 수행할 종료 작업을 본 메소드를 override하여 구현한다.
	 * Override한 메소드에서는 반드시 이 메소드를 호출해야 한다.
	 */
	@Override
	protected void finalizeLoop() { }

	@Override
	protected final FOption<T> iterate(long loopIndex)
		throws CancellationException, InterruptedException, TimeoutException, ExecutionException {
		// 시간제한이 설정된 경우에는 제한 시간을 넘었는지 검사한다.
		if ( m_due != null && m_due.isBefore(Instant.now()) ) {
			throw new TimeoutException(m_timeout != null ? "timeout=" + m_timeout : "due=" + m_due);
		}

		Instant iterStarted = Instant.now();

		// 주기 작업을 수행한다.
		FOption<T> result = performPeriodicAction(loopIndex);
		Preconditions.checkNotNull(result, "performPeriodicAction() must not return null: %s", this);

		if ( result.isPresent() ) {
			// 원하는 결과가 생산된 경우는 loop 종료
			return result;
		}

		if ( isCancelRequested() ) {
			throw new CancellationException("loop execution is cancelled");
		}

		// 시간제한이 설정된 경우에는 제한 시간을 넘었는지 검사한다.
		if ( m_due != null && m_due.isBefore(Instant.now()) ) {
			throw new TimeoutException(m_timeout != null ? "timeout=" + m_timeout : "due=" + m_due);
		}

		//
		// 이번 iteration의 due 시간을 계산하고, 그 due 시간까지 대기한다.
		//

		Instant iterationDue;
		if ( !m_cumulativeInterval ) {
			// 'm_cumulativeInterval'이 false인 경우는 이번 iteration 시작 시각을 기준으로
			// due 시간을 결정한다.
			iterationDue = iterStarted.plus(m_interval);
		}
		else {
			// Loop 인덱스를 이용하여 이번 loop의 due 시간을 계산한다.
			// Due 시간은 interval 기간에 iterate 횟수를 곱해서 전체 지연 시간을 계산해서
			// loop의 시작 시각에 더해서 결정한다.
			Instant started = Preconditions.checkNotNull(m_started, "loop not initialized");
			iterationDue = started.plus(m_interval.multipliedBy(loopIndex+1));
		}
		// 전체 마감 시각(m_due)을 넘기지 않도록 iterationDue를 m_due로 캡한다.
		if ( m_due != null && iterationDue.isAfter(m_due) ) {
			iterationDue = m_due;
		}
		
		// 다음번 iteration 시작 시각까지 대기한다.
		Date due = Date.from(iterationDue);
		if ( m_aopGuard.awaitCondition(this::isCancelRequested, due).andReturn() ) {
			// 취소 요청이 들어온 경우는 loop 종료
			throw new CancellationException("cancelled while waiting for next iteration: " + this);
		}
		
		// Iteration 실행 시간을 다 채운 경우, 다음 번 iteration을 수행하도록 한다.
		return FOption.empty();
	}
}
