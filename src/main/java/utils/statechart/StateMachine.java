package utils.statechart;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import utils.LoggerSettable;
import utils.Throwables;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.Guard;
import utils.func.FOption;
import utils.func.Unchecked;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateMachine<C extends StateContext> extends AbstractAsyncExecution<C>
													implements CancellableWork, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(StateMachine.class);
	
	private final C m_context;
	
	protected final Guard m_guard = Guard.create();
	private utils.statechart.State<C> m_initialState;
	private Set<utils.statechart.State<C>> m_finalStates = Sets.newHashSet();
	private utils.statechart.State<C> m_currentState;
	private Logger m_logger = s_logger;
	
	public StateMachine(C context) {
		Preconditions.checkNotNull(context, "StateContext is null");
		
		m_context = context;
	}
	
	public C getContext() {
		return m_context;
	}
	
	public utils.statechart.State<C> getInitialState() {
		return m_initialState;
	}
	
	public void setInitialState(utils.statechart.State<C> initialState) {
		Preconditions.checkNotNull(initialState, "initialState is null");

		m_initialState = initialState;
	}
	
	public void addFinalState(utils.statechart.State<C> state) {
		Preconditions.checkNotNull(state, "state is null");

		m_finalStates.add(state);
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
	
	public FOption<Transition<C>> handleSignal(Signal signal) {
		Preconditions.checkNotNull(signal, "signal is null");

		m_guard.lock();
		try {
			if ( !isRunning() ) {
				getLogger().warn("ignoring signal: {} in non-running state", signal);
				return FOption.empty();
			}
			getLogger().info("received signal: {} in state {}", signal, m_currentState);

			// Transition이 선택되면 해당 전이로 상태 전이 수행
	        return m_currentState.selectTransition(signal)
		        				.ifPresent(trans -> traverse(trans, FOption.of(signal)));
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
		return FOption.getOrElse(m_logger, s_logger);
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
	
	protected void traverse(Transition<C> transition, FOption<Signal> osignal) {
		Preconditions.checkState(m_currentState.equals(transition.getSourceState()),
								"invalid transition: %s", transition);
		
		if ( transition.getSourceState() == transition.getTargetState()
			&& transition.getAction().isAbsent() ) {
			getLogger().debug("no state change (noop transition)");
			return;
		}
		getLogger().info("transition: {} -> {}", transition.getSourceState(), transition.getTargetState());
		
		try {
			exitInGuard();
			
			// 선택된 전이로 상태 전이 연산 수행
			transition.execute(m_context, osignal);
			
			// 목표 상태로 진입 연산 수행
			transition.getTargetState().enter();
			m_currentState = transition.getTargetState();
			getLogger().info("entered {}", m_currentState);
			
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
			getLogger().info("left {}", m_currentState);
			m_currentState = null;
		}
	}
}
