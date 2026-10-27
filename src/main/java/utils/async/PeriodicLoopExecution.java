package utils.async;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;

import com.google.common.math.LongMath;

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
 * <h2>선택 옵션</h2>
 * 다음 옵션들은 loop 시작 전에만 설정할 수 있으며, 동작에 큰 영향을 준다.
 * <ul>
 *  <li>{@link IntervalMode} (생성자 인자, 기본값 {@link IntervalMode#FIXED_DELAY}):
 *  	{@link IntervalMode#FIXED_RATE}이면 Loop 연산이 시작된 시간을 기준으로 interval 시간을 누적하여
 *  	iteration의 시작 시각을 계산한다. 만일 한 iteration의 수행 시간이 interval 보다 긴 경우에는
 *  	다음 iteration이 대기 없이 곧바로 시작된다(catch-up).
 *  	{@link IntervalMode#FIXED_DELAY}이면 이번 iteration이 끝난 시각에서 interval 시간만큼 대기한 뒤
 *  	다음 iteration을 시작한다. 한 iteration이 interval보다 오래 걸려도 다음 iteration 전에 항상
 *  	interval만큼 대기한다.
 *  <li>{@link #setTimeout(Duration)} / {@link #setDue(Instant)}: 둘 다 loop의 제한 시간을 설정하며,
 *  제한 시간이 지나면 {@link TimeoutException}으로 loop가 종료된다. 두 메소드는 제한 시간의
 *  설정 기준이 다르다.
 *  <ul>
 *   <li>{@code setTimeout}은 loop가 <b>실제로 시작된 시점</b>부터의 상대 기간(예: 30초)으로 지정한다.
 *       {@link #start()} 호출 후 실행이 지연되더라도 지정한 기간 전체를 사용할 수 있다.</li>
 *   <li>{@code setDue}는 시스템 시계 기준의 <b>절대 시각</b>(예: 14:30:00)으로 지정한다. 실행 시작이
 *       지연되면 그만큼 사용 가능한 시간이 줄어들며, 시작 시점에 이미 지난 시각이면 첫 iteration 전에
 *       곧바로 만료된다. 상위 작업의 마감 시각을 하위 loop에 그대로 전달할 때 유용하다.</li>
 *  </ul>
 *  둘 다 설정된 경우에는 {@code setDue}가 우선하며 {@code setTimeout}은 무시된다.
 * </ul>
 * 위 옵션들로 인해 loop이 비정상 종료되는 경우, {@link #iterate(long)}에서
 * {@link TimeoutException} 또는 {@link CancellationException}이 던져진다.
 *
 * <h2>시간 기준</h2>
 * 주기와 마감 판정은 시스템 시계가 아닌 단조 시계({@link System#nanoTime()})로 측정한 loop 시작 이후의
 * 경과 시간을 기준으로 한다. 따라서 NTP 동기화 등으로 시스템 시계가 앞뒤로 점프해도 iteration이 몰려서
 * 실행되거나(catch-up 폭주), 대기가 길어지거나, 제한 시간이 조기에 만료되지 않는다.
 * {@link #setDue(Instant)}로 지정한 마감 시각만은 시스템 시계 기준이며, loop 시작 시점에 남은 시간으로
 * 한 번 환산된 뒤에는 시스템 시계 변경의 영향을 받지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PeriodicLoopExecution<T> extends AbstractLoopExecution<T> {
	private final Duration m_interval;
	private @Nullable Duration m_timeout = null;
	private @Nullable Instant m_due = null;
	private final IntervalMode m_intervalMode;

	// Loop 일정은 시스템 시계(wall-clock)가 아닌 단조 시계(System.nanoTime) 기준으로 계산한다.
	// 시스템 시계가 NTP 동기화 등으로 점프해도 주기·마감 판정이 영향을 받지 않도록 하기 위함이다.
	// 워커 스레드에서 initializeLoop() 시 설정되며, getRemainingTime()으로 외부 스레드에서도 조회되므로 volatile.
	private volatile long m_startNanos;						// loop 시작 시점의 System.nanoTime()
	private volatile @Nullable Long m_deadlineNanos = null;	// loop 시작 시점부터 마감까지의 경과 시간(ns). null이면 마감 없음.
	private @Nullable String m_deadlineDesc = null;	// 실제 적용된 제한 시간 설명 (TimeoutException 메시지용)
	
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
	 * <b>취소(cancel) 협력 계약:</b> 상위 클래스의 계약에 따라, 본 메소드가 수행 중일 때 {@code cancel(true)}가
	 * 호출되면 실행은 {@code CANCELLING}으로 전이되지만 본 메소드가 반환할 때까지 {@code CANCELLED}로 끝나지 않는다.
	 * (iteration 사이의 주기 대기 중에 들어온 취소 요청은 즉시 감지된다.) 빠른 취소가 필요한 경우 본 메소드
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
	 * @param mode		주기 계산 방식. 각 모드의 의미는 {@link IntervalMode} 참조.
	 */
	protected PeriodicLoopExecution(Duration interval, IntervalMode mode) {
		Preconditions.checkArgument(interval != null && DurationUtils.isPositive(interval),
								"invalid interval: %s", interval);
		Preconditions.checkNotNullArgument(mode, "interval mode is null");

		m_interval = interval;
		m_intervalMode = mode;
	}

	/**
	 * {@link IntervalMode#FIXED_DELAY} 모드로 주기적인 loop 작업을 수행하는 객체를 생성한다.
	 *
	 * @param interval 주기
	 */
	protected PeriodicLoopExecution(Duration interval) {
		this(interval, IntervalMode.FIXED_DELAY);
	}

	/**
	 * 주기 계산 방식을 반환한다.
	 *
	 * @return	주기 계산 방식.
	 */
	public IntervalMode getIntervalMode() {
		return m_intervalMode;
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
	 * {@link #setDue(Instant)}와 마찬가지로 loop의 제한 시간을 설정하지만, 시스템 시계 기준의 절대 시각이 아니라
	 * loop가 <b>실제로 시작된 시점</b>부터의 상대 기간으로 지정한다는 점이 다르다. 따라서 {@link #start()} 호출 후
	 * 실행 시작이 지연되더라도 지정한 기간 전체를 사용할 수 있다.
	 * 이 시간은 {@link #setDue(Instant)}를 통해 마감 시각이 설정되지 않은 경우에만 적용된다.
	 * Loop 작업이 시작된 이후에는 실행 제한 시간을 변경할 수 없다.
	 *
	 * @param timeout 설정할 실행 제한 시간의 Duration 객체,
	 * 					제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음.
	 * 					non-null인 경우 양수여야 한다.
	 * @throws IllegalStateException	이미 loop 작업이 시작된 이후에 호출된 경우.
	 * @throws IllegalArgumentException	{@code timeout}이 non-null이면서 양수가 아닌 경우.
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
	 * @return Loop 연산 종료 시각의 Instant 객체.
	 * 			연산이 시작되기 전에는 {@link #setDue(Instant)}로 설정한 값이며, 설정하지 않았으면 {@code null}.
	 * 			연산이 시작된 이후에는 {@link #setDue(Instant)} 또는 {@link #setTimeout(Duration)}으로부터
	 * 			계산된 실제 마감 시각이며, 둘 다 설정되지 않았으면 {@code null}.
	 * 			이 값은 참고용이며, 실제 마감 판정은 loop 시작 이후의 경과 시간으로 이루어지므로
	 * 			시작 이후 시스템 시계가 변경되면 실제 만료 시각과 어긋날 수 있다.
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
	 * <p>
	 * {@link #setTimeout(Duration)}과 마찬가지로 loop의 제한 시간을 설정하지만, loop 시작 시점부터의 상대 기간이
	 * 아니라 시스템 시계 기준의 <b>절대 시각</b>으로 지정한다는 점이 다르다. 따라서 {@link #start()} 호출 후 실행
	 * 시작이 지연되면 그만큼 사용 가능한 시간이 줄어들고, 시작 시점에 이미 지난 시각이면 첫 iteration 전에
	 * 곧바로 만료된다. 마감 시각이 설정되면 {@link #setTimeout(Duration)}은 무시된다.
	 * <p>
	 * 마감 시각은 loop 시작 시점에 "남은 시간"으로 한 번 환산되어 적용된다. 따라서 시작 이후에
	 * 시스템 시계가 변경되더라도 마감 판정은 시작 시점에 계산된 남은 시간을 기준으로 이루어진다.
	 *
	 * @param due 설정할 루프 마감 시각의 Instant 객체,
	 * 				제한 시간이 없음을 나타내기 위해 null을 지정할 수 있음
	 * @throws IllegalStateException	이미 loop 작업이 시작된 이후에 호출된 경우.
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
		m_startNanos = System.nanoTime();
		Instant started = Instant.now();

		if ( m_due == null ) {
			m_due = FOption.map(m_timeout, to -> plusSaturated(started, to));
			m_deadlineNanos = (m_timeout != null) ? toNanosSaturated(m_timeout) : null;
			m_deadlineDesc = (m_timeout != null) ? "timeout=" + m_timeout : null;
		}
		else {
			// setDue()로 지정된 마감 시각은 시스템 시계 기준이므로, 시작 시점에 남은 시간으로 한 번만 환산한다.
			// 이후의 시스템 시계 변경은 마감 판정에 영향을 주지 않는다.
			// 마감 시각이 우선하므로 setTimeout()이 함께 설정되었더라도 무시한다.
			m_deadlineNanos = toNanosSaturated(Duration.between(started, m_due));
			m_deadlineDesc = "due=" + m_due;
		}

		if ( m_deadlineNanos != null && m_deadlineNanos < 0 ) {
			throw newTimeoutException();
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
	protected final FOption<T> iterate(long loopIndex) throws CancellationException, InterruptedException,
																TimeoutException, ExecutionException {
		// 시간제한이 설정된 경우에는 제한 시간을 넘었는지 검사한다.
		if ( isDeadlinePassed() ) {
			throw newTimeoutException();
		}

		// 주기 작업을 수행한다.
		FOption<T> result = performPeriodicAction(loopIndex);
		Preconditions.checkNotNull(result, "performPeriodicAction() must not return null: %s", this);

		if ( result.isPresent() ) {
			// 원하는 결과가 생산된 경우는 loop 종료
			return result;
		}

		// 작업 수행 중 취소 요청이 들어온 경우는 loop 종료.
		// 아래 제한 시간 검사보다 먼저 수행하여, 취소 요청과 제한 시간 초과가 겹친 경우 취소가 우선하도록 한다.
		if ( isCancelRequested() ) {
			throw new CancellationException("loop execution is cancelled");
		}
		// 작업 수행 중 외부에서 이미 종료 상태로 확정된 경우는 제한 시간 검사로 새 실패 원인을 만들지 않고
		// 상위 클래스의 다음 iteration 직전 검사에서 loop를 끝내도록 넘긴다.
		if ( isDone() ) {
			return FOption.empty();
		}

		// 시간제한이 설정된 경우에는 제한 시간을 넘었는지 검사한다.
		if ( isDeadlinePassed() ) {
			throw newTimeoutException();
		}

		//
		// 다음 iteration의 시작 시점(loop 시작 시점부터의 경과 시간)을 계산하고, 그때까지 대기한다.
		//

		long elapsedNanos = getElapsedNanos();
		long intervalNanos = toNanosSaturated(m_interval);
		long nextStartNanos;
		if ( m_intervalMode == IntervalMode.FIXED_DELAY ) {
			// FIXED_DELAY인 경우는 이번 iteration 종료 시점을 기준으로 다음 시작 시점을 결정한다.
			// Iteration이 interval보다 오래 걸려도 다음 iteration 전에 항상 interval만큼 대기한다.
			nextStartNanos = LongMath.saturatedAdd(elapsedNanos, intervalNanos);
		}
		else {
			// Loop 인덱스를 이용하여 다음 시작 시점을 계산한다.
			// interval 기간에 iterate 횟수를 곱한 누적 지연 시간을 loop 시작 시점에 더해서 결정한다.
			nextStartNanos = LongMath.saturatedMultiply(intervalNanos, loopIndex+1);
		}
		// 전체 마감 시점을 넘기지 않도록 다음 시작 시점을 마감 시점으로 캡한다.
		if ( m_deadlineNanos != null ) {
			nextStartNanos = Math.min(nextStartNanos, m_deadlineNanos);
		}

		// 다음번 iteration 시작 시점까지 대기한다.
		// 외부에서 이미 종료 상태로 전이된 경우에도 곧바로 깨어나며, 이 경우의 loop 종료는
		// 상위 클래스의 다음 iteration 직전 검사에서 처리된다.
		Duration wait = Duration.ofNanos(Math.max(0, nextStartNanos - elapsedNanos));
		try {
			m_aopGuard.awaitCondition(() -> isCancelRequested() || isDone(), wait).andReturn();
			if ( isCancelRequested() ) {
				// 취소 요청이 들어온 경우는 loop 종료
				throw new CancellationException("cancelled while waiting for next iteration: " + this);
			}
		}
		catch ( TimeoutException expected ) { }
		
		// Iteration 실행 시간을 다 채운 경우, 다음 번 iteration을 수행하도록 한다.
		return FOption.empty();
	}

	/**
	 * Loop 제한 시간까지 남은 시간을 반환한다.
	 * <p>
	 * 하위 클래스가 iteration 안에서 수행하는 개별 요청(원격 호출 등)의 timeout을 정할 때 사용한다.
	 * Loop 전체의 제한 시간은 진행 중인 iteration을 강제로 중단하지 않으므로, 오래 걸릴 수 있는 호출에는
	 * 이 값을 timeout으로 전달하는 것이 좋다. 값은 loop 시작 이후의 경과 시간(단조 시계) 기준으로 계산된다.
	 *
	 * @return	남은 시간을 담은 {@link FOption}. 이미 마감이 지났으면 {@link Duration#ZERO}.
	 * 			제한 시간이 설정되지 않았거나 loop가 아직 시작되지 않은 경우({@link #initializeLoop()} 이전)에는
	 * 			{@link FOption#empty()}.
	 */
	public FOption<Duration> getRemainingTime() {
		Long deadlineNanos = m_deadlineNanos;
		if ( deadlineNanos == null ) {
			return FOption.empty();
		}
		return FOption.of(Duration.ofNanos(Math.max(0, deadlineNanos - getElapsedNanos())));
	}

	private long getElapsedNanos() {
		return System.nanoTime() - m_startNanos;
	}

	private boolean isDeadlinePassed() {
		return m_deadlineNanos != null && getElapsedNanos() > m_deadlineNanos;
	}

	private TimeoutException newTimeoutException() {
		return new TimeoutException(m_deadlineDesc);
	}

	/**
	 * {@link Instant}에 {@link Duration}을 더한다. 표현 가능한 범위를 넘는 경우에는 {@link Instant#MAX}로 포화시킨다.
	 * ({@code ChronoUnit.FOREVER.getDuration()}처럼 사실상 "제한 없음"을 뜻하는 큰 timeout을 허용하기 위함이다.)
	 */
	private static Instant plusSaturated(Instant instant, Duration duration) {
		try {
			return instant.plus(duration);
		}
		catch ( DateTimeException | ArithmeticException e ) {
			return Instant.MAX;
		}
	}

	/**
	 * {@link Duration}을 nano 초로 변환한다. {@code long} 범위를 넘는 경우에는 최댓값/최솟값으로 포화시킨다.
	 */
	private static long toNanosSaturated(Duration duration) {
		try {
			return duration.toNanos();
		}
		catch ( ArithmeticException e ) {
			return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
	}
}
