package utils.statechart;

import java.util.Optional;


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
	public Optional<Transition<C>> selectTransition(Signal signal) {
		throw new UnsupportedOperationException("SinkState does not handle any signal");
	}
}