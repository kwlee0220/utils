package utils.statechart;

import java.util.Optional;

import com.google.common.base.Preconditions;

import lombok.experimental.UtilityClass;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@UtilityClass
public class Transitions {
	public static <C extends StateContext> Transition<C> create(String targetStatePath,
																TransitionAction action) {
		return new DefaultTransition<C>(targetStatePath, action);
	}

	public static <C extends StateContext> Transition<C> noop(String targetStatePath) {
		return new NoOpTransition<C>(targetStatePath);
	}

	public static <C extends StateContext> Transition<C> stay() {
		return new StayTransition<>();
	}
	
	public static class DefaultTransition<C extends StateContext> implements Transition<C> {
		private final Optional<String> m_targetStatePath;
		private final TransitionAction<C> m_action;
	
		private DefaultTransition(String targetStatePath, TransitionAction<C> action) {
			Preconditions.checkNotNull(targetStatePath, "targetStatePath is null");
			Preconditions.checkNotNull(action, "TransitionAction is null");
			
			m_targetStatePath = Optional.of(targetStatePath);
			m_action = action;
		}

		@Override
		public Optional<String> getTargetStatePath() {
			return Optional.empty();
		}
	
		@Override
		public void execute(C context, Signal signal) {
			m_action.accept(context, signal);
		}
		
		@Override
		public String toString() {
			return m_action.toString() + " -> " + m_targetStatePath.get();
		}
	}

	public static class NoOpTransition<C extends StateContext> implements Transition<C> {
		private final Optional<String> m_targetStatePath;
	
		private NoOpTransition(String targetStatePath) {
			m_targetStatePath = Optional.of(targetStatePath);
		}
	
		@Override
		public Optional<String> getTargetStatePath() {
			return m_targetStatePath;
		}
	
		@Override
		public void execute(C context, Signal signal) { }
		
		@Override
		public String toString() {
			return "noop -> " + m_targetStatePath.get();
		}
	}

	public static class StayTransition<C extends StateContext> implements Transition<C> {
		@Override
		public Optional<String> getTargetStatePath() {
			return Optional.empty();
		}
	
		@Override
		public void execute(C context, Signal signal) { }
		
		@Override
		public String toString() {
			return "stay";
		}
	}
}
