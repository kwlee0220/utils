package utils.statechart;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;

/**
 * 머무는 시간에 제한이 있는 상태의 공통 기반 클래스.
 * <p>
 * 진입하면 {@link #getTimeout()}만큼의 타이머를 걸고, 진출하면 취소한다. 타이머가 만료되면 차트에
 * {@link TimedOut} 신호를 보내고, 차트는 이 신호를 다른 상태에게 묻지 않고 타이머를 건 이 상태에게 바로 전달해
 * {@link #onTimeout(Duration)}이 고른 전이를 수행한다. 그 밖의 신호는 전이 표({@link #on(Signal, Transition)} 등)로
 * 처리한다.
 * <p>
 * 타이머는 신호를 받는다고 저절로 다시 시작되지 않는다. "메시지가 없는 시간"처럼 진행이 있을 때마다 다시
 * 재야 하는 경우에는, 하위 클래스가 의미 있는 진행을 확인한 곳(전이 액션, {@code selectTransition} 등)에서
 * {@link #restartTimeout()}을 호출한다.
 * <p>
 * 진입·진출 시 작업은 {@link #onEnter()}/{@link #onExit()}를 재정의해서 넣는다. {@link #enter()}와
 * {@link #exit()}는 타이머를 빠뜨리지 않도록 {@code final}이다. 만료 신호는 {@link #selectTransition(Signal)}을
 * 거치지 않으므로, 이를 재정의해도 만료 처리에는 영향이 없다.
 * <p>
 * 타이머는 상태 객체마다 따로 있어, 부모와 자식 상태가 동시에 타이머를 가질 수 있다. 만료 신호는 주인 상태에게만
 * 전달되므로, 자식 상태의 포괄 전이({@code on(Signal.class, ...)} 등)가 부모의 만료 신호를 가로채지 못한다. 각
 * 진입·재시작마다 세대 번호가 바뀌므로, 취소되었거나 이전 진입에서 걸린 타이머의 신호는 머무르기만 한다
 * ({@link Transitions#stay()}). 주인 상태를 이미 벗어난 뒤 도착한 만료 신호는 처리되지 않은 신호가 되어
 * {@link StateChart#onUnhandledSignal(Signal)}에 전달된다.
 * <p>
 * 실행 스레드: 만료 작업은 {@code scheduler}에서 실행된다. 차트는 신호를 보낸 스레드가 큐를 비우며 전이까지
 * 실행하므로, {@code signalExecutor}를 주면 신호를 그 실행기에서 보내 전이가 스케줄러 스레드를 붙잡지 않게
 * 한다. {@code signalExecutor}가 작업을 거부하면 경고를 남기고 스케줄러 스레드에서 직접 보낸다(만료가 사라지지
 * 않게 하기 위해서다. 이때 전이는 스케줄러 스레드에서 실행될 수 있다). 두 실행기는 호출자가 소유하며, 이 상태는
 * 종료(shutdown)하지 않는다.
 * <p>
 * 차트를 {@link StateChart#complete()}/{@link StateChart#fail(Throwable)}로 직접 끝내면 {@code exit()}가
 * 실행되지 않는다. 이 경우에도 예약이 남지 않도록, 상태는 처음 진입할 때 차트에 종료 리스너를 한 번 등록해
 * 차트가 끝나면 타이머를 취소한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractTimedState<C extends StateContext<C>> extends AbstractState<C> {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractTimedState.class);

	private final ScheduledExecutorService m_scheduler;
	private final @Nullable Executor m_signalExecutor;

	private final Object m_timerLock = new Object();
	// 진입·재시작·취소 때마다 증가한다. 만료 신호는 자신을 건 세대가 현재 세대일 때만 유효하다.
	@GuardedBy("m_timerLock") private long m_generation = 0;
	@GuardedBy("m_timerLock") private @Nullable ScheduledFuture<?> m_timer;
	// 진입해 있는지 여부. 진입해 있지 않으면 restartTimeout()이 타이머를 걸지 않는다.
	@GuardedBy("m_timerLock") private boolean m_entered = false;
	// 차트 종료 시 타이머를 취소하는 리스너를 등록했는지 여부. 처음 진입할 때 한 번만 등록한다.
	@GuardedBy("m_timerLock") private boolean m_finishHookRegistered = false;

	/**
	 * 만료 신호를 스케줄러 스레드에서 바로 보내는 상태를 생성한다.
	 * <p>
	 * 만료로 일어나는 전이가 스케줄러 스레드에서 실행되므로, 전이가 오래 걸릴 수 있으면
	 * {@link #AbstractTimedState(String, StateContext, ScheduledExecutorService, Executor)}를 쓴다.
	 *
	 * @param path		상태의 위치 경로
	 * @param context	상태문맥
	 * @param scheduler	타이머를 예약할 스케줄러
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	protected AbstractTimedState(String path, C context, ScheduledExecutorService scheduler) {
		this(path, context, scheduler, null);
	}

	/**
	 * 만료 신호를 주어진 실행기에서 보내는 상태를 생성한다.
	 *
	 * @param path				상태의 위치 경로
	 * @param context			상태문맥
	 * @param scheduler			타이머를 예약할 스케줄러
	 * @param signalExecutor	만료 신호를 보낼 실행기. {@code null}이면 스케줄러 스레드에서 보낸다.
	 * @throws IllegalArgumentException	{@code path}, {@code context}, {@code scheduler} 중 {@code null}이
	 * 									있는 경우.
	 */
	protected AbstractTimedState(String path, C context, ScheduledExecutorService scheduler,
								@Nullable Executor signalExecutor) {
		super(path, context);

		Preconditions.checkNotNullArgument(scheduler, "scheduler");

		m_scheduler = scheduler;
		m_signalExecutor = signalExecutor;
	}

	/**
	 * 이 상태의 제한 시간을 반환한다. 타이머를 걸 때마다({@link #restartTimeout()}) 호출된다.
	 *
	 * @return	제한 시간. 양수여야 한다.
	 */
	protected abstract Duration getTimeout();

	/**
	 * 제한 시간이 지났을 때 수행할 전이를 고른다. 신호를 처리하는 스레드에서 차트 lock을 보유한 채 호출된다.
	 *
	 * @param timeout	지난 제한 시간
	 * @return	수행할 전이. {@code null}이면 안 된다.
	 */
	protected abstract Transition<C> onTimeout(Duration timeout);

	/**
	 * 상태에 진입할 때 수행할 작업. 기본 구현은 아무 일도 하지 않는다.
	 * <p>
	 * 타이머를 건 뒤에 호출되므로, 여기서 {@link #restartTimeout()}/{@link #cancelTimeout()}을 불러도 된다.
	 * 예외를 던지면 타이머를 취소한 뒤 그 예외를 전파한다(진입에 실패한 상태는 진출되지 않는다).
	 */
	protected void onEnter() { }

	/**
	 * 상태에서 진출할 때 수행할 작업. 기본 구현은 아무 일도 하지 않는다. 타이머를 취소한 뒤 호출된다.
	 */
	protected void onExit() { }

	@Override
	public final void enter() {
		boolean registerFinishHook;
		synchronized ( m_timerLock ) {
			m_entered = true;
			registerFinishHook = !m_finishHookRegistered;
			m_finishHookRegistered = true;
		}
		if ( registerFinishHook ) {
			// complete()/fail()로 직접 끝내면 exit()가 불리지 않으므로, 차트가 끝날 때 예약을 해제한다.
			StateChart<C> chart = getContext().getStateChart();
			if ( chart != null ) {
				chart.whenFinished(result -> leave());
			}
		}
		try {
			restartTimeout();
			onEnter();
		}
		catch ( Throwable e ) {
			leave();
			throw e;
		}
	}

	@Override
	public final void exit() {
		leave();
		onExit();
	}

	/**
	 * 이 상태가 보낸 만료 신호에 대한 전이를 고른다. 이 상태가 진입해 있을 때 {@link StateChart}가 호출한다.
	 *
	 * @param timedOut	이 상태가 보낸 만료 신호
	 * @return	현재 타이머의 신호이면 {@link #onTimeout(Duration)}의 전이, 취소되었거나 이전 진입의 신호이면
	 * 			{@link Transitions#stay()}.
	 */
	Transition<C> selectTimeoutTransition(TimedOut timedOut) {
		return isCurrent(timedOut.m_generation) ? onTimeout(timedOut.m_timeout) : Transitions.stay();
	}

	/**
	 * 걸려 있는 타이머를 취소하고 {@link #getTimeout()}만큼의 타이머를 새로 건다.
	 * <p>
	 * 상태에 진입해 있을 때만 타이머를 건다. 진출한 뒤(예: 비동기 작업의 완료 콜백에서) 호출하면 아무 일도 하지 않는다.
	 *
	 * @throws IllegalStateException	{@link #getTimeout()}이 {@code null}이거나 양수가 아닌 경우.
	 */
	protected final void restartTimeout() {
		// 진출한 뒤에는 getTimeout()을 부르지 않는다. 진출하면서 제한 시간 정보를 정리한 하위 클래스도 있다.
		// getTimeout()은 하위 클래스 코드이므로 타이머 lock 밖에서 부르고, 예약 직전에 한 번 더 확인한다.
		if ( !isEntered() ) {
			s_logger.debug("ignoring restartTimeout() on a state not entered: {}", getPath());
			return;
		}
		Duration timeout = getTimeout();
		Preconditions.checkState(timeout != null && !timeout.isNegative() && !timeout.isZero(),
								"timeout should be positive: state=%s, timeout=%s", getPath(), timeout);

		synchronized ( m_timerLock ) {
			if ( !m_entered ) {
				s_logger.debug("ignoring restartTimeout() on a state not entered: {}", getPath());
				return;
			}
			cancelInLock();
			long generation = m_generation;
			m_timer = m_scheduler.schedule(() -> expire(generation, timeout), toNanos(timeout),
											TimeUnit.NANOSECONDS);
		}
	}

	/**
	 * 걸려 있는 타이머를 취소한다. 이미 예약 스레드에서 실행을 시작한 만료 작업이 보낸 신호도 무시된다.
	 * 걸린 타이머가 없으면 아무 일도 하지 않는다.
	 */
	protected final void cancelTimeout() {
		synchronized ( m_timerLock ) {
			cancelInLock();
		}
	}

	private void leave() {
		synchronized ( m_timerLock ) {
			m_entered = false;
			cancelInLock();
		}
	}

	@GuardedBy("m_timerLock")
	private void cancelInLock() {
		++m_generation;
		if ( m_timer != null ) {
			m_timer.cancel(false);
			m_timer = null;
		}
	}

	private boolean isEntered() {
		synchronized ( m_timerLock ) {
			return m_entered;
		}
	}

	private boolean isCurrent(long generation) {
		synchronized ( m_timerLock ) {
			return generation == m_generation;
		}
	}

	private void expire(long generation, Duration timeout) {
		if ( !isCurrent(generation) ) {
			return;
		}
		StateChart<C> chart = getContext().getStateChart();
		if ( chart == null || chart.isDone() ) {
			return;
		}

		TimedOut signal = new TimedOut(this, generation, timeout);
		if ( m_signalExecutor != null ) {
			try {
				m_signalExecutor.execute(() -> chart.postSignal(signal));
				return;
			}
			catch ( RuntimeException e ) {
				// 거부된 만료를 버리면 만료가 유일한 탈출 조건인 차트가 끝나지 않는다. 직접 보낸다.
				s_logger.warn("signalExecutor rejected a timeout signal; posting it from the scheduler thread: "
								+ "state={}, cause={}", getPath(), ""+e);
			}
		}
		try {
			chart.postSignal(signal);
		}
		catch ( RuntimeException e ) {
			// 예약 작업의 예외는 아무도 보지 않으므로 로그로 남긴다.
			s_logger.warn("failed to post a timeout signal: state={}, cause={}", getPath(), ""+e);
		}
	}

	private static long toNanos(Duration timeout) {
		// 약 292년을 넘는 값은 toNanos()가 넘치므로 가장 긴 지연으로 맞춘다.
		return timeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : timeout.toNanos();
	}

	/**
	 * {@link AbstractTimedState}의 제한 시간이 지났음을 알리는 신호.
	 * <p>
	 * 차트는 이 신호를 타이머를 건 상태에게만 전달한다. 주인 상태를 벗어난 뒤 도착하면 처리되지 않은 신호가 된다.
	 */
	public static final class TimedOut implements Signal {
		private final AbstractTimedState<?> m_state;
		private final long m_generation;
		private final Duration m_timeout;

		TimedOut(AbstractTimedState<?> state, long generation, Duration timeout) {
			m_state = state;
			m_generation = generation;
			m_timeout = timeout;
		}

		/**
		 * 타이머를 건 상태를 반환한다.
		 *
		 * @return	타이머를 건 상태
		 */
		public AbstractTimedState<?> getState() {
			return m_state;
		}

		/**
		 * 지난 제한 시간을 반환한다.
		 *
		 * @return	제한 시간
		 */
		public Duration getTimeout() {
			return m_timeout;
		}

		@Override
		public String toString() {
			return String.format("TimedOut[state=%s, timeout=%s, generation=%d]",
									m_state.getPath(), m_timeout, m_generation);
		}
	}
}
