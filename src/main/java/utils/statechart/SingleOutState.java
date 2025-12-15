package utils.statechart;

import java.util.Optional;
import java.util.function.Supplier;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SingleOutState<C extends StateContext> extends AbstractState<C> {
	private final Signal m_trigger;
	private final Supplier<Transition<C>> m_outTransitionSupplier;

	public SingleOutState(String name, C context, Signal trigger, Supplier<Transition<C>> outTransitionSupplier) {
		super(name, context);
		
		m_trigger = trigger;
		m_outTransitionSupplier = outTransitionSupplier;
	}

	public SingleOutState(String name, C context, Signal trigger, Transition<C> outTransition) {
		this(name, context, trigger, () -> outTransition);
	}

	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		if ( m_trigger == signal ) {
			return Optional.of(m_outTransitionSupplier.get());
		}
		else {
			return Optional.empty();
		}
	}
}