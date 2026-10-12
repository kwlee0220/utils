package utils.statechart;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.Throwables;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.func.Unchecked;
import utils.thread.Guard;


/**
 * 신호로 구동되는 계층적 상태 차트.
 * <p>
 * {@link AbstractAsyncExecution}을 상속하기 때문에 차트 자체가 하나의 비동기 실행이다. 즉
 * {@code Future}처럼 종료를 기다릴 수 있고({@code waitForFinished()}), 종료 리스너를 등록할 수 있으며
 * ({@code whenFinished(...)}), 취소할 수 있다({@code cancel(true)}). 상태 전이의 결과가 곧 실행의
 * 성공/실패/취소로 드러난다.
 * <p>
 * <b>구성 순서</b>는 다음과 같다. 상태 등록 계열 메소드는 차트가 시작되기 전에만 호출할 수 있다.
 * <ol>
 *   <li>{@code new StateChart<>(context)} — 컨텍스트에 자기 자신이 자동으로 등록된다.</li>
 *   <li>{@link #addState(utils.statechart.State)}로 모든 상태를 등록한다.</li>
 *   <li>{@link #setInitialState(String)}로 시작 상태를, {@link #addFinalState(String)}로
 *       최종 상태를 지정한다. 시작 상태는 최종 상태일 수 없다.</li>
 *   <li>{@link #start()}로 시작하면 시작 상태에 진입하고 {@code RUNNING}이 된다.</li>
 *   <li>{@link #handleSignal(Signal)}로 신호를 넣으면 현재 상태가 전이를 선택하고,
 *       선택된 전이를 따라 이동한다.</li>
 * </ol>
 * <p>
 * <b>종료 방식은 두 가지다.</b> 최종 상태에 도달하면 차트가 스스로 종료한다 — 보통은
 * {@code complete()}이고, 도달한 상태가 {@link ExceptionState}이면 그 상태가 보유한 원인으로
 * {@code fail()}된다. 이와 별개로 {@link #complete()} / {@link #fail(Throwable)} /
 * {@code cancel(true)}를 직접 호출해 밖에서 끝낼 수도 있다. 이 경우는 상태 전이를 거치지 않으므로
 * 어떤 상태의 {@code enter()}도 실행되지 않는다는 점에 주의한다.
 * <p>
 * <b>동시성</b>: 시작, 신호 처리, 종료는 모두 하나의 {@link Guard}({@link #getMachineLock()})로
 * 직렬화된다. 이 lock은 부모가 실행 상태를 보호하는 lock과는 별개다. 상태의
 * {@code enter()}/{@code exit()}와 전이 액션은 모두 이 lock을 보유한 채 실행되므로, 오래 걸리는
 * 작업을 넣으면 그동안 다른 신호가 처리되지 못한다.
 *
 * @param <C>	상태 컨텍스트 타입. (CRTP self-bound)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChart<C extends StateContext<C>> extends AbstractAsyncExecution<C>
												implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(StateChart.class);

	private final C m_context;
	private final Map<String,utils.statechart.State<C>> m_states = Maps.newHashMap();

	protected final Guard m_guard = Guard.create();
	private utils.statechart.State<C> m_initialState;
	private Set<utils.statechart.State<C>> m_finalStates = Sets.newHashSet();
	@GuardedBy("m_guard") private utils.statechart.State<C> m_currentState;
	private Logger m_logger = s_logger;

	/**
	 * 주어진 상태 컨텍스트로 상태 차트를 생성한다.
	 * <p>
	 * 생성과 동시에 컨텍스트에 자기 자신을 등록하여({@link StateContext#setStateChart(StateChart)})
	 * 양방향 참조를 맺는다. 따라서 하나의 컨텍스트를 두 개의 차트에 넘길 수 없다.
	 *
	 * @param context	상태 컨텍스트. 다른 차트에 등록된 적이 없어야 한다.
	 * @throws NullPointerException		{@code context}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code context}가 이미 다른 차트에 등록된 경우.
	 */
	public StateChart(C context) {
		Preconditions.checkNotNull(context, "StateContext is null");

		m_context = context;
		m_context.setStateChart(this);
	}

	/**
	 * 본 차트의 상태 전이를 직렬화하는 lock을 반환한다.
	 * <p>
	 * 하위 클래스가 차트 상태와 함께 갱신되어야 하는 자체 데이터를 보호할 때 사용한다.
	 * 이 lock은 재진입 가능하므로, 이미 lock을 보유한 콜백(상태의 {@code enter()}, 전이 액션 등)
	 * 안에서 다시 획득해도 무방하다.
	 *
	 * @return	상태 전이 lock.
	 */
	protected Guard getMachineLock() {
		return m_guard;
	}

	/**
	 * 본 차트의 상태 컨텍스트를 반환한다.
	 *
	 * @return	상태 컨텍스트. ({@code null} 아님)
	 */
	public C getContext() {
		return m_context;
	}

	/**
	 * 주어진 위치 경로로 등록된 상태를 반환한다.
	 *
	 * @param path	찾을 상태의 위치 경로.
	 * @return	해당 경로로 등록된 상태. <b>등록된 적이 없으면 예외 대신 {@code null}을 반환한다.</b>
	 * @throws NullPointerException	{@code path}가 {@code null}인 경우.
	 */
	public utils.statechart.State<C> getState(String path) {
		utils.Preconditions.checkNotNull(path, "path is null");

		return m_states.get(path);
	}

	/**
	 * 상태를 차트에 등록한다.
	 * <p>
	 * 상태는 자신의 위치 경로({@link utils.statechart.State#getPath()})를 키로 등록되므로, 같은 경로로 다시 등록하면
	 * 기존 상태를 <b>조용히 대체</b>한다. {@link #setInitialState(String)}와
	 * {@link #addFinalState(String)}는 등록된 상태만 지정할 수 있으므로 이 메소드를 먼저 호출해야 한다.
	 *
	 * @param state	등록할 상태.
	 * @throws NullPointerException		{@code state}가 {@code null}인 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void addState(utils.statechart.State<C> state) {
		utils.Preconditions.checkNotNull(state, "state is null");
		utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");

		m_states.put(state.getPath(), state);
	}

	/**
	 * 차트의 시작 상태를 반환한다.
	 *
	 * @return	시작 상태. {@link #setInitialState(String)} 호출 전이면 {@code null}.
	 */
	public utils.statechart.State<C> getInitialState() {
		return m_initialState;
	}

	/**
	 * 차트의 시작 상태를 지정한다.
	 * <p>
	 * 이미 {@link #addState(utils.statechart.State)}로 등록된 상태만 지정할 수 있다. 시작 상태는 최종 상태일 수 없으며,
	 * 이 검사는 여기가 아니라 {@link #start()}에서 이루어진다.
	 *
	 * @param initialStatePath	시작 상태의 위치 경로.
	 * @throws NullPointerException		{@code initialStatePath}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	해당 경로로 등록된 상태가 없는 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void setInitialState(@NotNull String initialStatePath) {
		utils.Preconditions.checkNotNull(initialStatePath, "initialState is null");
		utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");
		
		utils.statechart.State<C> initialState = m_states.get(initialStatePath);
		if ( initialState == null ) {
			throw new IllegalArgumentException("initialState is not registered: " + initialStatePath);
		}

		m_initialState = initialState;
	}
	
	/**
	 * 차트의 최종 상태를 추가한다.
	 * <p>
	 * 최종 상태에 도달하면 차트는 스스로 종료한다. 해당 상태가 {@link ExceptionState}이면 그 상태가
	 * 보유한 원인으로 {@link #fail(Throwable)}되고, 그 외에는 {@link #complete()}된다.
	 * 최종 상태는 여러 개를 등록할 수 있으며, 최소 하나는 있어야 {@link #start()}가 성공한다.
	 * <p>
	 * 이미 {@link #addState(utils.statechart.State)}로 등록된 상태만 지정할 수 있다.
	 *
	 * @param path	최종 상태의 위치 경로.
	 * @throws NullPointerException		{@code path}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	해당 경로로 등록된 상태가 없는 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void addFinalState(String path) {
		utils.Preconditions.checkNotNull(path, "state path is null");
		utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");
		
		utils.statechart.State<C> finalState = m_states.get(path);
		if ( finalState == null ) {
			throw new IllegalArgumentException("finalState is not registered: " + path);
		}

		m_finalStates.add(finalState);
	}
	
	/**
	 * 현재 진입 중인 상태를 반환한다.
	 * <p>
	 * 반환값은 조회 시점의 snapshot이므로, 다른 스레드가 신호를 처리하면 곧바로 낡은 값이 된다.
	 * 상태를 보고 판단한 뒤 행동해야 한다면 {@link #getMachineLock()}으로 직접 직렬화한다.
	 * <p>
	 * 복합 상태({@link CompositeState})에 진입한 경우, 반환되는 것은 복합 상태 자신이며
	 * 그 내부의 하위 상태는 {@link CompositeState#getCurrentState()}로 따로 조회한다.
	 *
	 * @return	현재 상태. 시작 전이거나 상태 전이 중, 또는 취소로 상태에서 진출한 뒤에는 {@code null}.
	 */
	public utils.statechart.State<C> getCurrentState() {
		return m_guard.get(() -> m_currentState);
	}

	/**
	 * 차트를 시작한다.
	 * <p>
	 * 시작 상태에 진입한 뒤 실행 상태를 {@code RUNNING}으로 전환한다. 이 시점부터
	 * {@link #handleSignal(Signal)}이 신호를 처리한다.
	 * <p>
	 * 시작 상태의 {@code enter()}가 예외를 던지면 차트는 실패 상태로 종료되고, 그 원인을 담은
	 * {@link IllegalStateException}이 호출자에게 전파된다.
	 *
	 * @throws NullPointerException		시작 상태가 지정되지 않은 경우.
	 * @throws IllegalStateException	최종 상태가 하나도 없거나, 시작 상태가 최종 상태이거나,
	 * 									이미 시작된 차트이거나, 시작 상태 진입이 실패한 경우.
	 */
	@Override
	public void start() {
		utils.Preconditions.checkNotNull(m_initialState, "initialState is not set");
		utils.Preconditions.checkState(m_finalStates != null && m_finalStates.size() > 0,
							"finalState should not be empty");
		utils.Preconditions.checkState(!m_finalStates.contains(m_initialState),
							"initialState should not be a finalState");
		
		m_guard.lock();
		try {
			if ( !notifyStarting() ) {
				throw new IllegalStateException("failed to start the StateMachine");
			}
			
			try {
	            // 초기 상태로 진입
	            m_initialState.enter();
	        }
	        catch ( Exception e ) {
	            Throwable cause = Throwables.unwrapThrowable(e);
	            notifyFailed(cause);
	            throw new IllegalStateException("failed to start the StateMachine", cause);
	        }

            m_currentState = m_initialState;
			if ( !notifyStarted() ) {
				Unchecked.runOrIgnore(m_initialState::exit);
				
	            notifyFailed(new IllegalStateException("failed to start the StateMachine"));
				throw new IllegalStateException("failed to start the StateMachine");
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	private static final long MAX_IGNORED_MSG_COUNT = 50;
	private long m_ignoredMsgCount = 0;
	
	/**
	 * 주어진 신호를 처리한다.
	 * <p>
	 * 현재 상태에게 이 신호를 처리할 전이가 있는지 묻고({@link utils.statechart.State#selectTransition(Signal)}),
	 * 있으면 그 전이를 따라 이동한다. 이동은 현재 상태의 {@code exit()} → 전이 액션 →
	 * 목표 상태의 {@code enter()} 순으로 이루어지며, 목표가 최종 상태이면 차트가 종료된다.
	 * <p>
	 * <b>차트가 실행 중이 아니면 신호는 조용히 버려지고 {@link Optional#empty()}이 반환된다.</b>
	 * (경고 로그만 남는다.) 시작 전이나 종료 후에 보낸 신호가 아무 반응 없이 사라지므로, 신호가
	 * 처리되었는지 확인이 필요하면 반환값을 검사한다. 다만 반환값이 비어 있다는 것은
	 * "차트가 실행 중이 아님"과 "현재 상태가 이 신호를 처리하지 않음"을 구분해 주지 않는다.
	 *
	 * @param signal	처리할 신호.
	 * @return	신호 처리에 사용된 전이. 전이가 일어나지 않았으면 {@link Optional#empty()}.
	 * @throws NullPointerException	{@code signal}이 {@code null}인 경우.
	 * @throws RuntimeException	{@link utils.statechart.State#selectTransition(Signal)}이 예외를 던진 경우.
	 * 							이때 차트도 함께 실패 상태로 종료된다. 반면 전이 <b>수행</b> 중에
	 * 							발생한 예외는 차트 내부에서 흡수되어 전파되지 않는다.
	 */
	public Optional<Transition<C>> handleSignal(Signal signal) {
		utils.Preconditions.checkNotNull(signal, "signal is null");

		m_guard.lock();
		try {
			if ( !isRunning() ) {
				if ( ++m_ignoredMsgCount <= MAX_IGNORED_MSG_COUNT ) {
					getLogger().warn("{}: ignoring signal: {} in non-running state", m_currentState, signal);
					if ( m_ignoredMsgCount == MAX_IGNORED_MSG_COUNT ) {
						getLogger().warn("{}: ignoring signal: {} in non-running state (further messages will be suppressed)",
										m_currentState, signal);
					}
				}
				return Optional.empty();
			}
			getLogger().debug("{}: received signal: {}", m_currentState, signal);

			// Signal에 따른 transition을 선택하고, 해당 전이로 이동
			Optional<Transition<C>> selected = m_currentState.selectTransition(signal);
			selected.ifPresent(trans -> traverse(trans, signal));
			
			return selected;
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			getLogger().error("{}: failed to handle signal: {}, cause={}", m_currentState, signal, cause);
			
			notifyFailed(cause);
			throw e;
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 차트를 정상 완료 상태로 종료시킨다.
	 * <p>
	 * 최종 상태에 도달하면 차트가 스스로 호출하지만, 상태 전이와 무관하게 밖에서 끝내고 싶을 때
	 * 직접 호출할 수도 있다. <b>직접 호출하는 경우 상태 전이를 거치지 않으므로 현재 상태의
	 * {@code exit()}도, 어떤 최종 상태의 {@code enter()}도 실행되지 않는다.</b> 상태 진입에 의존하는
	 * 정리 작업이 있다면 종료 리스너({@code whenFinished})로 옮겨야 한다.
	 * <p>
	 * 실행 결과값으로는 상태 컨텍스트가 사용된다.
	 *
	 * @throws IllegalStateException	아직 시작되지 않았거나 이미 종료된 차트인 경우.
	 */
	public void complete() {
        m_guard.lock();
        try {
            if ( !notifyCompleted(m_context) ) {
            	throw new IllegalStateException("cannot complete the StateMachine");
            }
        }
        finally {
            m_guard.unlock();
        }
	}
	
	/**
	 * 차트를 실패 상태로 종료시킨다.
	 * <p>
	 * {@link ExceptionState}에 도달하면 차트가 그 상태의 원인으로 스스로 호출하며, 전이 수행 중
	 * 예외가 발생한 경우에도 내부적으로 같은 경로를 탄다. {@link #complete()}와 마찬가지로 직접
	 * 호출할 수 있고, 그 경우 상태 전이를 거치지 않는다는 점도 동일하다.
	 *
	 * @param cause	실패 원인. {@code null}도 허용되지만, 그러면 원인 추적이 불가능해진다.
	 * @throws IllegalStateException	아직 시작되지 않았거나 이미 종료된 차트인 경우.
	 */
	public void fail(Throwable cause) {
		m_guard.lock();
		try {
			if ( !notifyFailed(cause) ) {
				throw new IllegalStateException("cannot fail the StateMachine");
			}
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 취소 요청을 받아 현재 상태에서 진출한다.
	 * <p>
	 * {@code cancel(true)}가 호출되면 부모가 lock 밖에서 호출하는 콜백이다. 현재 상태의
	 * {@code exit()}를 수행하고 현재 상태를 비운다. <b>어떤 최종 상태로도 진입하지 않으므로
	 * 최종 상태의 {@code enter()}는 실행되지 않는다.</b> 진출 과정에서 발생한 예외는 무시된다
	 * (예: {@link SinkState}는 진출 자체가 불가능하다).
	 * <p>
	 * 취소 요청 시점에 진입 중인 상태가 없으면 아무 일도 하지 않는다.
	 *
	 * @return	항상 {@code true}.
	 */
	@Override
	public boolean cancelWork() {
		m_guard.lock();
		try {
			if ( m_currentState == null ) {
				return true;
			}
			
			Unchecked.runOrIgnore(this::exitInGuard);
			return true;
		}
		finally {
			m_guard.unlock();
		}
	}

	@Override
	public Logger getLogger() {
		return (m_logger != null) ? m_logger : s_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s, state=%s]",
							getClass().getSimpleName(), getState(), getCurrentState());
	}
	
	/**
	 * 선택된 전이를 따라 상태를 이동시킨다.
	 * <p>
	 * 이동은 현재 상태의 {@code exit()} → 전이 액션({@link Transition#execute(StateContext, Signal)})
	 * → 목표 상태의 {@code enter()} 순으로 수행되며, 목표가 최종 상태이면 이어서 차트를 종료시킨다
	 * ({@link ExceptionState}이면 그 상태의 원인으로 {@link #fail(Throwable)}, 그 외에는
	 * {@link #complete()}).
	 * <p>
	 * self-transition({@link Transition#isSelfTransition()})이면 아무 일도 하지 않고 즉시 반환한다.
	 * 즉 현재 상태의 {@code exit()}/{@code enter()}는 물론 <b>전이 액션도 실행되지 않는다.</b>
	 * <p>
	 * <b>이동 도중 발생한 예외는 밖으로 전파되지 않는다.</b> 차트를 실패 상태로 종료시키고 흡수한다.
	 * 어느 단계에서 실패했느냐에 따라 현재 상태가 이전 상태에서 진출만 한 채 비어 있을 수 있다.
	 * 단, 목표 경로에 해당하는 상태가 등록되어 있지 않은 경우는 이 처리 이전에 검사되므로
	 * {@link IllegalStateException}이 그대로 전파된다.
	 * <p>
	 * 본 메소드는 {@link #handleSignal(Signal)}이 lock을 보유한 채 호출한다. 하위 클래스가 전이 과정에
	 * 개입해야 하는 경우에만 재정의한다.
	 *
	 * @param transition	수행할 전이.
	 * @param signal		전이를 유발한 신호.
	 * @throws IllegalStateException	전이의 목표 경로로 등록된 상태가 없는 경우.
	 */
	protected void traverse(Transition<C> transition, Signal signal) {
		if ( transition.isSelfTransition() ) {
			getLogger().debug("no state change (self transition)");
			return;
		}
		
		String targetStatePath = transition.getTargetStatePath().get();
		utils.statechart.State<C> targetState = m_states.get(targetStatePath);
		if ( targetState == null ) {
			throw new IllegalStateException("no state found for path: " + targetStatePath);
		}
	
		if ( getLogger().isInfoEnabled() ) {
			String targetStr = transition.getTargetStatePath()
										.map(path -> " -> " + path)
										.orElse("");
			getLogger().info("selected transition: {}{}", m_currentState.getPath(), targetStr);
		}

		utils.statechart.State<C> srcState = m_currentState;
		utils.statechart.State<C> dstState = targetState;
		try {
			// 현재 상태에서 진출한다.
			exitInGuard();
			
			// 인자로 주어진 transition으로 전이 연산 수행
			transition.execute(m_context, signal);
			
			// 목표 상태로 진입한다.
			targetState.enter();
			m_currentState = targetState;
			getLogger().info("entered state: {}", m_currentState.getPath());
			
			// 최종 상태에 도달했으면 완료 통지
			if ( m_finalStates.contains(m_currentState) ) {
				if ( m_currentState instanceof ExceptionState ) {
					Throwable cause = ((ExceptionState<C>) m_currentState).getFailureCause();
					fail(cause);
				}
				else {
					complete();
				}
			}
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			getLogger().error("failed to traverse transition: {} -> {}",
								srcState.getPath(), dstState.getPath(), cause);
			
			notifyFailed(cause);
		}
	}
	
	private void exitInGuard() {
		// 현재 상태에서 exit 연산 수행
		if ( m_currentState != null ) {
			m_currentState.exit();
			getLogger().info("left state: {}", m_currentState.getPath());
			m_currentState = null;
		}
	}
}
