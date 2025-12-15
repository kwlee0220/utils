package utils.statechart;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.LoggerSettable;
import utils.Throwables;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.Guard;
import utils.func.Unchecked;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChart<C extends StateContext> extends AbstractAsyncExecution<C>
													implements CancellableWork, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(StateChart.class);
	
	private final C m_context;
	private final Map<String,utils.statechart.State<C>> m_states = Maps.newHashMap();
	
	protected final Guard m_guard = Guard.create();
	private utils.statechart.State<C> m_initialState;
	private Set<utils.statechart.State<C>> m_finalStates = Sets.newHashSet();
	private utils.statechart.State<C> m_currentState;
	private Logger m_logger = s_logger;
	
	public StateChart(C context) {
		Preconditions.checkNotNull(context, "StateContext is null");
		
		m_context = context;
		m_context.setStateMachine(this);
	}
	
	protected Guard getMachineLock() {
		return m_guard;
	}
	
	public C getContext() {
		return m_context;
	}
	
	public utils.statechart.State<C> getState(String path) {
		Preconditions.checkNotNull(path, "path is null");

		return m_states.get(path);
	}
	
	public void addState(utils.statechart.State<C> state) {
		Preconditions.checkNotNull(state, "state is null");

		m_states.put(state.getPath(), state);
	}
	
	public utils.statechart.State<C> getInitialState() {
		return m_initialState;
	}
	
	public void setInitialState(String initialStatePath) {
		Preconditions.checkNotNull(initialStatePath, "initialState is null");
		
		utils.statechart.State<C> initialState = m_states.get(initialStatePath);
		if ( initialState == null ) {
			throw new IllegalArgumentException("initialState is not registered: " + initialState);
		}

		m_initialState = initialState;
	}
	
	public void addFinalState(String path) {
		Preconditions.checkNotNull(path, "state path is null");
		
		utils.statechart.State<C> finalState = m_states.get(path);
		if ( finalState == null ) {
			throw new IllegalArgumentException("finalState is not registered: " + finalState);
		}

		m_finalStates.add(finalState);
	}
	
	public utils.statechart.State<C> getCurrentState() {
		return m_currentState;
	}

	@Override
	public void start() {
		Preconditions.checkArgument(m_finalStates != null && m_finalStates.size() > 0, "finalState should not be empty");
		Preconditions.checkArgument(!m_finalStates.contains(m_initialState), "initialState should not be a finalState");
		
		m_guard.lock();
		try {
			if ( !notifyStarting() ) {
				throw new IllegalStateException("failed to start the StateMachine");
			}
			
			try {
	            // 초기 상태로 진입
	            m_initialState.enter();
	            m_currentState = m_initialState;
	        }
	        catch ( Exception e ) {
	            Throwable cause = Throwables.unwrapThrowable(e);
	            notifyFailed(cause);
	            throw new IllegalStateException("failed to start the StateMachine", cause);
	        }
			
			if ( !notifyStarted() ) {
	            notifyFailed(new IllegalStateException("failed to start the StateMachine"));
				throw new IllegalStateException("failed to start the StateMachine");
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 주어진 신호를 처리한다.
	 * <p>
	 * 신호 처리 과정에서 내부 상태가 전의될 수 있다.
	 * 신호 처리 과정에서 사용된 transition이 반환된다.
	 * 만일 상태 전이가 발생되지 않으면 {@link Optional#empty()}을 반환한다.
	 * <p>
	 * 만일 상태머신이 실행 중이 아니면 신호는 무시되고 {@link Optional#empty()}이 반환된다.
	 *
	 * @param signal	처리할 신호
	 * @return 신호 처리 과정에서 사용된 최종 {@link Transition} 객체.
	 */
	public Optional<Transition<C>> handleSignal(Signal signal) {
		Preconditions.checkNotNull(signal, "signal is null");

		m_guard.lock();
		try {
			if ( !isRunning() ) {
				getLogger().warn("ignoring signal: {} in non-running state", signal);
				return Optional.empty();
			}
			getLogger().info("received signal: {}, state={}", signal, m_currentState);

			// Signal에 따른 transition을 선택하고, 해당 전이로 이동
			Optional<Transition<C>> selected = m_currentState.selectTransition(signal);
			selected.ifPresent(trans -> traverse(trans, signal));
			
			return selected;
		}
		finally {
			m_guard.unlock();
		}
	}
	
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
	
//	public void cancel() {
//		m_guard.lock();
//		try {
//			
//			
//			if ( !notifyCancelled() ) {
//				throw new IllegalStateException("cannot cancel the StateMachine execution");
//			}
//		}
//		finally {
//			m_guard.unlock();
//		}
//	}

	@Override
	public boolean cancelWork() {
		m_guard.lock();
		try {
			Unchecked.runOrIgnore(() -> m_currentState.exit());
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
	
	protected void traverse(Transition<C> transition, Signal signal) {
		if ( transition.getTargetStatePath().isEmpty() ) {
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
