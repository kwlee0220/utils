package utils.async;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.FOption;
import utils.func.Unchecked;


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
 * Polling의 시간 제한은 부모 클래스의
 * {@link PeriodicLoopExecution#setTimeout(java.time.Duration) setTimeout}
 * 또는 {@link PeriodicLoopExecution#setDue(java.time.Instant) setDue}를 통해 설정한다.
 * 제한 시간 내에 목표 상태가 되지 않으면 {@link java.util.concurrent.TimeoutException}이 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractPeriodicPoller<R> extends PeriodicLoopExecution<R> {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractPeriodicPoller.class);

	private @Nullable R m_result = null;

	/**
	 * Poller 객체를 생성한다.
	 *
	 * @param pollInterval			Polling 주기.
	 * @param cumulativeInterval	부모 클래스의 누적 주기 모드 사용 여부.
	 * 								자세한 의미는 {@link PeriodicLoopExecution} 참조.
	 */
	protected AbstractPeriodicPoller(Duration pollInterval, boolean cumulativeInterval) {
		super(pollInterval, cumulativeInterval);

		setLogger(s_logger);
	}

	/**
	 * Poller 객체를 생성한다.
	 * <p>
	 * 누적 주기 모드를 사용한다. 즉, loop 시작 시각을 기준으로 매 iteration의 시작 시각이
	 * 계산되며, 한 iteration의 수행 시간이 주기보다 길어지면 다음 iteration은 대기 없이
	 * 곧바로 시작된다(catch-up). 자세한 의미는 {@link PeriodicLoopExecution} 참조.
	 *
	 * @param pollInterval Polling 주기.
	 */
	protected AbstractPeriodicPoller(Duration pollInterval) {
		this(pollInterval, true);
	}

	/**
	 * Polling 시작 시 1회 호출되는 초기화 작업.
	 * <p>
	 * 본 메소드 호출 시점에는 부모 클래스의 시작 관련 상태가 모두 설정된 상태이다
	 * (예: {@link PeriodicLoopExecution#getDue()}로 마감 시각 조회 가능).
	 * 본 메소드에서 예외가 발생하면 {@link #finalizePoller(Object) finalizePoller}는 호출되지 않으므로,
	 * 일부 초기화만 성공한 경우의 cleanup은 본 메소드 내부에서 직접 처리해야 한다.
	 *
	 * @throws Exception 초기화 과정에서 예외가 발생한 경우.
	 */
	protected void initializePoller() throws Exception { }

	/**
	 * 목표 상태 도달 여부를 확인한다.
	 * <p>
	 * 본 메소드는 {@link PeriodicLoopExecution#getLoopInterval() interval} 주기로 반복 호출된다.
	 * 반환값에 따른 동작은 클래스 Javadoc 참조.
	 *
	 * @return	목표 상태에 도달한 경우 그 값을 담은 {@link FOption},
	 * 			아직 도달하지 않은 경우 {@link FOption#empty()}. {@code null}을 반환해서는 안 된다.
	 * @throws InterruptedException		polling이 인터럽트된 경우.
	 * @throws CancellationException	polling이 취소된 경우.
	 * @throws Exception		polling 중 예외가 발생한 경우.
	 */
	protected abstract FOption<R> tryPoll() throws InterruptedException, CancellationException, ExecutionException;

	/**
	 * Polling이 종료될 때 호출되는 정리 작업.
	 * <p>
	 * 초기화({@link #initializePoller()})가 성공적으로 완료된 이후라면 polling의 종료 사유와
	 * 무관하게 항상 1회 호출된다.
	 *
	 * @param state {@link #tryPoll()}가 값이 있는 {@link FOption}을 반환하여 정상적으로
	 * 				목표 상태에 도달한 경우 그 값. 취소/timeout/예외 등 비정상 종료 시에는 {@code null}.
	 */
	protected void finalizePoller(@Nullable R state) { }

	@Override
	protected final void initializeLoop() throws Exception {
		super.initializeLoop();
		initializePoller();
	}

	@Override
	protected final void finalizeLoop() {
		Unchecked.runOrIgnore(() -> finalizePoller(m_result));
		super.finalizeLoop();
	}

	@Override
	protected final FOption<R> performPeriodicAction(long loopIndex)
		throws InterruptedException, CancellationException, ExecutionException {
		FOption<R> ostate = tryPoll();
		if ( ostate.isPresent() ) {
			m_result = ostate.get();
		}

		return ostate;
	}
}
