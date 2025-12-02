package utils.statechart;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CompositeState<C extends StateContext> extends State<C> {
	public State<C> getInitialState();
	
	public State<C> getCurrentState();
}
