package utils.statechart;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface StateContext {
	public StateChart<? extends StateContext> getStateMachine();
	public void setStateMachine(StateChart<? extends StateContext> machine);
}
