package utils.statechart;

import java.util.Optional;
import java.util.function.BiConsumer;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TransitionImpl<C extends StateContext> {
	private final String m_targetStatePath;
	private final Optional<BiConsumer<C,Optional<Signal>>> m_oaction;
	
	public TransitionImpl(String targetStatePath, BiConsumer<C, Optional<Signal>> action) {
		m_targetStatePath = targetStatePath;
		m_oaction = Optional.ofNullable(action);
	}
	
	public TransitionImpl(String targetStatePath) {
		m_targetStatePath = targetStatePath;
		m_oaction = Optional.empty();
	}
	
	public String getTargetStatePath() {
		return m_targetStatePath;
	}
	
	public Optional<BiConsumer<C, Optional<Signal>>> getAction() {
		return m_oaction;
	}
	
	public void execute(C context, Optional<Signal> signal) {
		m_oaction.ifPresent(act -> act.accept(context, signal));
	}
	
	@Override
	public String toString() {
		return "Transition[ -> %s]".formatted(m_targetStatePath);
	}

	public static <C extends StateContext> TransitionImpl<C> noop(String targetStatePath) {
		return new TransitionImpl<>(targetStatePath);
	}
	public static <C extends StateContext> TransitionImpl<C> stay(String statePath) {
		return new TransitionImpl<>(null);
	}
}
