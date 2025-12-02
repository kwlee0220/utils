package utils.statechart;

import java.util.function.BiConsumer;

import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Transition<C extends StateContext> {
	private final State<C> m_sourceState;
	private final State<C> m_targetState;
	private final FOption<BiConsumer<C,FOption<Signal>>> m_action;
	
	public Transition(State<C> source, State<C> target, BiConsumer<C, FOption<Signal>> action) {
		m_sourceState = source;
		m_targetState = target;
		m_action = FOption.of(action);
	}
	
	public Transition(State<C> source, State<C> target) {
		m_sourceState = source;
		m_targetState = target;
		m_action = FOption.empty();
	}
	
	public State<C> getSourceState() {
		return m_sourceState;
	}
	
	public State<C> getTargetState() {
		return m_targetState;
	}
	
	public FOption<BiConsumer<C, FOption<Signal>>> getAction() {
		return m_action;
	}
	
	public void execute(C context, FOption<Signal> signal) {
		m_action.ifPresent(act -> act.accept(context, signal));
	}
	
	@Override
	public String toString() {
		return "Transition[%s -> %s]".formatted(m_sourceState.getPath(), m_targetState.getPath());
	}

	public static <C extends StateContext> Transition<C> noop(State<C> source, State<C> target) {
		return new Transition<>(source, target);
	}

	public static <C extends StateContext> Transition<C> stay(State<C> state) {
		return new Transition<>(state, state);
	}
}
