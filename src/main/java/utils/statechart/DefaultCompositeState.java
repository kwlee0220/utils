package utils.statechart;

import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultCompositeState<C extends StateContext> extends AbstractState<C> implements CompositeState<C> {
	private State<C> m_initialState;
	private State<C> m_currentState;
	
	public DefaultCompositeState(String name, C context) {
		super(name, context);
	}

	@Override
	public State<C> getInitialState() {
		return m_initialState;
	}
	
	void setInitialState(State<C> initialState) {
		Preconditions.checkNotNull(initialState, "initialState is null");

		m_initialState = initialState;
	}

	@Override
	public State<C> getCurrentState() {
		return m_currentState;
	}

	@Override
	public void enter() {
		Preconditions.checkState(m_currentState == null, "CompositeState is already entered");
		
		m_currentState = m_initialState;
		m_currentState.enter();
	}

	@Override
	public void exit() {
		Preconditions.checkState(m_currentState != null, "CompositeState is not entered");
		
		m_currentState.exit();
		m_currentState = null;
	}

	@Override
	public Optional<Transition<C>> selectTransition(Signal signal)  {
        return m_currentState.selectTransition(signal);
	}
}
