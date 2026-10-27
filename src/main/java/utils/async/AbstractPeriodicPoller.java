package utils.async;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.func.FOption;


/**
 * {@code AbstractPeriodicPoller}는 주어진 목표 상태가 될 때까지 주기적으로 확인하는 기능을
 * 수행한다.
 * 목표 상태 도달 여부는 abstract method {@link #tryPoll()}를 사용한다.
 * {@link #tryPoll()} 메소드는 결과 값에 따라 다음과 같이 동작한다:
 * <dl>
 * 		<dt>값이 있는 {@code FOption<R>}:</dt>
 * 		<dd>목표 상태에 도달한 경우이고, polling 과정이 종료된다.</dd>
 * 		<dt>{@link FOption#empty()}:</dt>
 * 		<dd>아직 목표 상태에 도달하지 않은 경우이고, 다음 polling이 진행된다.</dd>
 * </dl>
 * 만일 메소드 호출시 {@link InterruptedException} 또는
 * {@link java.util.concurrent.CancellationException}이 발생하는 경우에는 polling 과정이 중단된다.
 * {@code null}을 반환하면 {@link NullPointerException}이 발생하여 작업이
 * {@link AsyncState#FAILED} 상태로 전이되므로 반환값으로 사용해서는 안 된다.
 * <p>
 * 하위 클래스가 구현할 수 있는 hook은 다음 순서로 호출된다.
 * <ol>
 *   <li>{@link #initializePoller()} — polling 시작 시 1회.</li>
 *   <li>{@link #tryPoll()} — 목표 상태에 도달할 때까지 주기적으로 반복.</li>
 *   <li>{@link #onTargetReached(Object)} — 목표 상태에 도달한 경우 1회. 결과를 가공/반영하며,
 *       여기서 발생한 예외는 polling 결과(실패/취소)에 반영된다.</li>
 *   <li>{@link #onTargetDiscarded(Object)} — 목표 상태 값을 얻었지만 최종 결과로 채택되지 못한 경우 1회.
 *       그 값이 소유한 자원(연결 등)을 정리한다.</li>
 *   <li>{@link #cleanupPoller(AsyncResult)} — 종료 사유와 무관하게 1회. 자원 정리용이며, 완료·실패·취소와
 *       실패 원인을 담은 종료 결과를 받는다. 여기서 발생한 예외는 로그만 남고 결과에 영향을 주지 않는다.</li>
 * </ol>
 * {@link #tryPoll()}과 {@link #onTargetReached(Object)}는 {@link Exception}을 던질 수 있다.
 * {@link InterruptedException}/{@link java.util.concurrent.CancellationException}은 취소로, 그 외 예외는
 * 그 예외를 원인으로 하는 실패로 처리된다(예: {@link java.io.IOException}을 {@link ExecutionException}으로
 * 감쌀 필요가 없다).
 * <p>
 * Polling의 시간 제한은 부모 클래스의
 * {@link PeriodicLoopExecution#setTimeout(java.time.Duration) setTimeout}
 * 또는 {@link PeriodicLoopExecution#setDue(java.time.Instant) setDue}를 통해 설정한다.
 * 제한 시간 내에 목표 상태가 되지 않으면 {@link java.util.concurrent.TimeoutException}이 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractPeriodicPoller<R> extends PeriodicLoopExecution<R> {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractPeriodicPoller.class);

	private FOption<R> m_adopted = FOption.empty();	// onTargetReached()까지 성공하여 최종 결과로 채택된 값
	private long m_pollAttempt = -1;				// 현재(마지막) polling 시도 번호. 워커 스레드에서만 갱신/조회된다.

	/**
	 * Poller 객체를 생성한다.
	 *
	 * @param pollInterval	Polling 주기.
	 * @param mode			주기 계산 방식. 각 모드의 의미는 {@link IntervalMode} 참조.
	 */
	protected AbstractPeriodicPoller(Duration pollInterval, IntervalMode mode) {
		super(pollInterval, mode);

		setLogger(s_logger);
	}

	/**
	 * Poller 객체를 생성한다.
	 * <p>
	 * {@link IntervalMode#FIXED_DELAY} 모드를 사용한다. 즉, 매 polling이 끝난 시각부터 {@code pollInterval}만큼
	 * 대기한 뒤 다음 polling을 시작한다. 한 번의 polling이 주기보다 오래 걸려도 다음 polling 전에 항상
	 * 주기만큼 대기하므로, 응답이 느린 원격 서버나 재연결 대상에 연달아 요청을 보내지 않는다.
	 * 고정 주기(catch-up) 모드가 필요하면 {@link #AbstractPeriodicPoller(Duration, IntervalMode)}에
	 * {@link IntervalMode#FIXED_RATE}를 지정한다.
	 *
	 * @param pollInterval Polling 주기.
	 */
	protected AbstractPeriodicPoller(Duration pollInterval) {
		this(pollInterval, IntervalMode.FIXED_DELAY);
	}

	/**
	 * 현재(또는 마지막으로) 수행된 polling의 시도 번호를 반환한다.
	 * <p>
	 * 첫 {@link #tryPoll()} 호출 시 {@code 0}이며 매 polling마다 1씩 증가한다. 따라서 {@link #tryPoll()}과
	 * {@link #onTargetReached(Object)} 안에서는 현재 시도 번호를, {@link #cleanupPoller(AsyncResult)} 안에서는
	 * 마지막 시도 번호를 나타낸다(지금까지 수행된 polling 횟수는 이 값 + 1). 첫 polling 전에는 {@code -1}이다.
	 * 재연결 시도 횟수 로깅이나 시도 횟수에 따른 동작 변경 등에 사용한다.
	 *
	 * @return	polling 시도 번호(0부터 시작). 아직 polling이 수행되지 않았으면 {@code -1}.
	 */
	protected long getPollAttempt() {
		return m_pollAttempt;
	}

	/**
	 * Polling 시작 시 1회 호출되는 초기화 작업.
	 * <p>
	 * 본 메소드 호출 시점에는 부모 클래스의 시작 관련 상태가 모두 설정된 상태이다
	 * (예: {@link PeriodicLoopExecution#getRemainingTime()}로 남은 제한 시간 조회 가능).
	 * 본 메소드에서 예외가 발생하면 {@link #cleanupPoller(AsyncResult) cleanupPoller}는 호출되지 않으므로,
	 * 일부 초기화만 성공한 경우의 cleanup은 본 메소드 내부에서 직접 처리해야 한다.
	 *
	 * @throws Exception 초기화 과정에서 예외가 발생한 경우.
	 */
	protected void initializePoller() throws Exception { }

	/**
	 * 목표 상태 도달 여부를 확인한다.
	 * <p>
	 * 본 메소드는 {@link PeriodicLoopExecution#getLoopInterval() interval} 주기로 반복 호출된다.
	 * 반환값에 따른 동작은 클래스 Javadoc 참조. 현재 시도 번호는 {@link #getPollAttempt()}로 얻을 수 있다.
	 * <p>
	 * 목표 상태 값이 자원(연결 등)을 소유하는 경우, 그 값이 최종 결과로 채택되지 못하면
	 * {@link #onTargetDiscarded(Object)}로 전달되므로 그곳에서 정리해야 한다.
	 *
	 * @return	목표 상태에 도달한 경우 그 값을 담은 {@link FOption},
	 * 			아직 도달하지 않은 경우 {@link FOption#empty()}. {@code null}을 반환해서는 안 된다.
	 * @throws InterruptedException		polling이 인터럽트된 경우. polling이 취소된다.
	 * @throws CancellationException	polling이 취소된 경우.
	 * @throws Exception				그 밖의 polling 오류. 그 예외를 원인으로 polling이 실패한다.
	 */
	protected abstract FOption<R> tryPoll() throws Exception;

	/**
	 * 목표 상태에 도달했을 때 1회 호출되어, {@link #tryPoll()}이 반환한 값을 가공하거나 내부 상태에 반영한다.
	 * <p>
	 * 반환한 값이 polling의 최종 결과가 된다. 기본 구현은 주어진 값을 그대로 반환한다.
	 * {@link #cleanupPoller(AsyncResult)}와 달리 본 메소드의 예외는 polling 결과에 반영된다.
	 * 즉 예외가 발생하면 {@link #tryPoll()}에서 발생한 경우와 동일하게 처리되어
	 * ({@link InterruptedException}/{@link CancellationException}은 취소, 그 외는 실패)
	 * 목표 상태에 도달하지 못한 것으로 간주되고, 전달받은 값은 {@link #onTargetDiscarded(Object)}로 넘겨진다.
	 * 따라서 결과 반영이 실패하면 작업이 실패해야 하는 처리(예: 연산 결과의 출력 변수 반영)는
	 * {@link #cleanupPoller(AsyncResult)}가 아니라 본 메소드에서 수행해야 한다.
	 * <p>
	 * <b>외부 종료와의 경합:</b> {@link #tryPoll()} 수행 중 외부에서 이미 종료 상태({@code FAILED} 등)로 확정된
	 * 경우에는 본 메소드를 호출하지 않는다. 본 메소드 수행 도중 외부 종료가 확정된 경우에는 반환값을 최종 결과로
	 * 채택하지 않는다. 두 경우 모두 채택되지 못한 값은 {@link #onTargetDiscarded(Object)}로 전달되며,
	 * {@link #cleanupPoller(AsyncResult)}에는 외부에서 확정된 결과가 전달된다.
	 * 다만 본 메소드가 이미 수행한 부수 효과(내부 상태 반영 등)는 되돌리지 않으며, 정리 작업 이후 완료 전이
	 * 사이의 좁은 구간에 확정된 외부 종료는 막을 수 없다. 따라서 본 메소드가 반영한 상태는
	 * 실행이 {@code COMPLETED}로 끝난 것을 확인한 뒤에 사용해야 한다.
	 * <p>
	 * 취소 요청 중({@code CANCELLING})에 목표 상태에 도달한 경우는 종료가 확정된 것이 아니므로 본 메소드가
	 * 호출되며, 상위 클래스의 정책에 따라 {@code COMPLETED}로 끝날 수 있다.
	 *
	 * @param result	{@link #tryPoll()}이 반환한 목표 상태 값.
	 * @return	polling의 최종 결과 값.
	 * @throws Exception	결과 처리 도중 오류가 발생한 경우. 그 예외를 원인으로 polling이 실패한다
	 * 						({@link InterruptedException}/{@link CancellationException}이면 취소된다).
	 */
	protected R onTargetReached(R result) throws Exception {
		return result;
	}

	/**
	 * 목표 상태 값을 얻었지만 최종 결과로 채택되지 못했을 때 1회 호출된다.
	 * <p>
	 * 목표 상태 값이 자원(연결 객체 등)을 소유하는 경우, 그 자원을 정리하기 위한 hook이다.
	 * 다음의 경우에 호출된다.
	 * <ul>
	 *   <li>{@link #tryPoll()}이 값을 반환했지만 외부에서 이미 종료가 확정되어 {@link #onTargetReached(Object)}를
	 *       호출하지 않은 경우 — {@link #tryPoll()}이 반환한 값.</li>
	 *   <li>{@link #onTargetReached(Object)}가 예외를 던진 경우 — {@link #tryPoll()}이 반환한 값.</li>
	 *   <li>{@link #onTargetReached(Object)} 수행 도중 외부 종료가 확정되어 반환값이 채택되지 않은 경우 —
	 *       {@link #onTargetReached(Object)}가 반환한 값.</li>
	 *   <li>최종 결과로 채택된 뒤 외부에서 종료가 확정되어 실행이 완료되지 못한 경우 — 채택된 최종 결과 값.
	 *       정리 시점에 이미 종료가 확정되었으면 {@link #cleanupPoller(AsyncResult)}보다 먼저, 정리 도중이나
	 *       그 이후에 확정되었으면 {@link #cleanupPoller(AsyncResult)} 다음(완료 전이가 실패한 시점)에 호출된다.
	 *       완료 전이의 실패 여부로 판단하므로, 채택된 값이 결과로 전달되지 못하는 경우에는 빠짐없이 호출된다.</li>
	 * </ul>
	 * 하나의 값에 대해 최대 1회 호출되며, 발생한 예외는 경고 로그만 남기고 무시된다.
	 * 기본 구현은 아무 작업도 하지 않는다.
	 *
	 * @param value	채택되지 못한 목표 상태 값.
	 * @throws Exception	정리 도중 오류가 발생한 경우(로그만 남는다).
	 */
	protected void onTargetDiscarded(R value) throws Exception { }

	/**
	 * Polling이 종료될 때 호출되는 정리 작업.
	 * <p>
	 * 초기화({@link #initializePoller()})가 성공적으로 완료된 이후라면 polling의 종료 사유와
	 * 무관하게 항상 1회 호출된다. 호출 시점에는 실행이 아직 최종 상태로 전이하기 전이므로({@link #poll()}로는
	 * 결과를 얻을 수 없다), 종료 결과는 인자로 전달된다.
	 * 본 메소드에서 발생한 예외는 경고 로그만 남기고 무시되므로, polling의 결과(완료/실패/취소)에는
	 * 영향을 주지 않는다. 결과 반영처럼 실패가 결과에 드러나야 하는 처리는 {@link #onTargetReached(Object)}에서
	 * 수행한다.
	 * <p>
	 * 기본 구현은 아무 작업도 하지 않는다.
	 *
	 * @param outcome	polling의 종료 결과. 완료({@link AsyncResult#isCompleted()}, 결과 값은 {@code null}일 수
	 * 					있다), 실패({@link AsyncResult#getFailureCause()}로 원인 조회. timeout이면
	 * 					{@link java.util.concurrent.TimeoutException}), 취소 중 하나이다.
	 * 					정리 작업 이후 완료 전이 사이의 좁은 구간에 외부에서 종료가 확정되면 실제 최종 상태와
	 * 					다를 수 있다.
	 * @throws Exception	정리 도중 오류가 발생한 경우(로그만 남는다).
	 */
	protected void cleanupPoller(AsyncResult<R> outcome) throws Exception { }

	@Override
	protected final void initializeLoop() throws Exception {
		super.initializeLoop();
		initializePoller();
	}

	@Override
	protected final void finalizeLoop() {
		try {
			AsyncResult<R> outcome = Preconditions.checkNotNull(getLoopOutcome(), "loop outcome is not available");

			// 최종 결과로 채택된 값이 있지만 정리 시점에 이미 외부 종료가 확정된 경우, 그 값의 자원을 정리한다.
			// 정리 이후 완료 전이 전에 확정된 외부 종료는 onResultDiscarded()에서 처리된다.
			if ( !outcome.isCompleted() ) {
				discardAdoptedQuietly();
			}
			try {
				cleanupPoller(outcome);
			}
			catch ( Exception e ) {
				getLogger().warn("failed to clean up poller: {}", this, e);
			}
		}
		finally {
			super.finalizeLoop();
		}
	}

	@Override
	protected final FOption<R> performPeriodicAction(long loopIndex)
		throws InterruptedException, CancellationException, ExecutionException {
		m_pollAttempt = loopIndex;
		FOption<R> ostate = Preconditions.checkNotNull(callHook(this::tryPoll),
														"tryPoll() must not return null: %s", this);
		if ( ostate.isAbsent() ) {
			return ostate;
		}

		// 목표 상태에 도달한 경우: onTargetReached()까지 성공해야 최종 결과로 확정한다.
		// 단, 외부에서 이미 종료 상태(FAILED 등)로 확정된 실행에는 결과를 반영하지 않는다. 종료가 확정된 뒤
		// 호출자가 후속 처리를 시작했을 수 있으므로, 반영 전과 후에 모두 확인하여 결과를 채택하지 않고
		// 상위 loop의 종료 처리로 넘긴다. 채택되지 못한 값은 onTargetDiscarded()로 넘겨 자원을 정리하게 한다.
		// 취소 요청 중(CANCELLING)은 종료 상태가 아니므로 기존 정책대로 결과 반영과 완료를 허용한다.
		R reached = ostate.get();
		if ( isDone() ) {
			discardQuietly(reached);
			return FOption.empty();
		}

		R result;
		try {
			result = callHook(() -> onTargetReached(reached));
		}
		catch ( InterruptedException | ExecutionException | RuntimeException e ) {
			discardQuietly(reached);
			throw e;
		}
		if ( isDone() ) {
			discardQuietly(result);
			return FOption.empty();
		}

		m_adopted = FOption.of(result);
		return m_adopted;
	}

	@FunctionalInterface
	private interface Hook<V> {
		V call() throws Exception;
	}

	/**
	 * Hook을 호출하고, 상위 loop가 처리하는 예외({@link InterruptedException}, {@link ExecutionException},
	 * unchecked 예외({@link CancellationException} 포함)) 외의 checked 예외는 {@link ExecutionException}으로 감싼다.
	 * 감싼 예외는 실패 원인을 기록할 때 다시 풀리므로, 실패 원인은 hook이 던진 원래 예외가 된다.
	 */
	private <V> V callHook(Hook<V> hook) throws InterruptedException, CancellationException, ExecutionException {
		try {
			return hook.call();
		}
		catch ( InterruptedException | ExecutionException | RuntimeException e ) {
			throw e;
		}
		catch ( Exception e ) {
			throw new ExecutionException(e);
		}
	}

	/**
	 * 완료 전이가 실패하여 최종 결과가 채택되지 못한 경우, 채택했던 값의 자원을 정리한다.
	 * 정리 작업({@link #cleanupPoller(AsyncResult)}) 도중이나 그 이후에 외부 종료가 확정된 경우가 여기에 해당한다.
	 */
	@Override
	protected final void onResultDiscarded(R result) {
		discardAdoptedQuietly();
	}

	/**
	 * 채택된 값이 아직 이 poller 소유이면 {@link #onTargetDiscarded(Object)}로 넘기고 소유를 해제한다.
	 * 정리 시점과 완료 전이 실패 시점 양쪽에서 호출될 수 있으나 같은 워커 스레드에서 순차적으로 호출되므로,
	 * 소유 해제로 인해 값은 정확히 1회만 넘겨진다.
	 */
	private void discardAdoptedQuietly() {
		if ( m_adopted.isPresent() ) {
			R adopted = m_adopted.get();
			m_adopted = FOption.empty();
			discardQuietly(adopted);
		}
	}

	private void discardQuietly(R value) {
		try {
			onTargetDiscarded(value);
		}
		catch ( Exception e ) {
			getLogger().warn("failed to discard target value: {}, value={}", this, value, e);
		}
	}
}
