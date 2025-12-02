package utils.statechart;

import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SinkState<C extends StateContext> extends AbstractState<C> {
	public SinkState(String path, C context) {
		super(path, context);
	}

	@Override
	public void exit() {
		throw new UnsupportedOperationException("SinkState cannot be exited");
	}

	@Override
	public FOption<Transition<C>> selectTransition(Signal signal) {
		throw new UnsupportedOperationException("SinkState does not handle any signal");
	}
}