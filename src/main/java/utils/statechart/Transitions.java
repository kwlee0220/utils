package utils.statechart;

import java.util.Optional;

import utils.Utilities;

/**
 * {@link Transition} 인스턴스를 생성하는 정적 팩토리 모음.
 * <p>
 * 사용자는 {@link Transition} 인터페이스를 직접 구현하기보다는 본 유틸리티 클래스의
 * 정적 팩토리 메소드 ({@link #create(String, TransitionAction)}, {@link #noop(String)},
 * {@link #stay()}) 를 통해 전이를 생성하는 것을 권장한다.
 * <p>
 * 세 가지 표준 전이 형태:
 * <ul>
 *   <li>{@link #create(String, TransitionAction) create} — 목표 상태로 이동하면서 액션 실행</li>
 *   <li>{@link #noop(String) noop} — 목표 상태로 이동만 하고 액션은 실행하지 않음</li>
 *   <li>{@link #stay() stay} — 현 상태에 머무는 self-transition (액션도 실행하지 않음)</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Transitions {
	private static final Transition<?> STAY_SINGLETON = new StayTransition<>();

	private Transitions() {
		throw new AssertionError("This class should not be instantiated");
	}

	/**
	 * 목표 상태와 액션을 모두 갖는 전이를 생성한다.
	 *
	 * @param <C>				상태 컨텍스트 타입
	 * @param targetStatePath	전이 후 도달할 목표 상태의 경로 (non-null)
	 * @param action			전이 실행 시 수행할 액션 (non-null)
	 * @return					생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code targetStatePath} 또는 {@code action}이 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> create(String targetStatePath,
																TransitionAction<C> action) {
		return new DefaultTransition<C>(targetStatePath, action);
	}

	/**
	 * 목표 상태로의 이동만 수행하고 액션은 실행하지 않는 전이를 생성한다.
	 *
	 * @param <C>				상태 컨텍스트 타입
	 * @param targetStatePath	전이 후 도달할 목표 상태의 경로 (non-null)
	 * @return					생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code targetStatePath}가 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> noop(String targetStatePath) {
		return new NoOpTransition<C>(targetStatePath);
	}

	/**
	 * 현 상태에 머무는 self-transition을 생성한다.
	 * <p>
	 * 반환된 전이의 {@link Transition#getTargetStatePath()}는 {@link Optional#empty()}을 반환하며
	 * {@link Transition#execute(StateContext, Signal)}도 호출되지 않는다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 * @return		self-transition을 표현하는 {@link Transition}
	 */
	@SuppressWarnings("unchecked")
	public static <C extends StateContext<C>> Transition<C> stay() {
		return (Transition<C>) STAY_SINGLETON;
	}

	/**
	 * 목표 상태와 액션을 모두 갖는 {@link Transition}의 기본 구현.
	 * <p>
	 * 일반적으로 사용자는 본 클래스를 직접 인스턴스화하지 않고
	 * {@link Transitions#create(String, TransitionAction)} 팩토리를 사용한다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	public static final class DefaultTransition<C extends StateContext<C>> implements Transition<C> {
		private final Optional<String> m_targetStatePath;
		private final TransitionAction<C> m_action;

		private DefaultTransition(String targetStatePath, TransitionAction<C> action) {
			Utilities.checkNotNullArgument(targetStatePath, "targetStatePath is null");
			Utilities.checkNotNullArgument(action, "TransitionAction is null");

			m_targetStatePath = Optional.of(targetStatePath);
			m_action = action;
		}

		@Override
		public Optional<String> getTargetStatePath() {
			return m_targetStatePath;
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

	/**
	 * 목표 상태로의 이동만 수행하는 {@link Transition} 구현.
	 * <p>
	 * {@link #execute(StateContext, Signal)}는 아무 동작도 하지 않으며 단순히 상태 이동만
	 * 발생한다. 일반적으로 사용자는 본 클래스를 직접 인스턴스화하지 않고
	 * {@link Transitions#noop(String)} 팩토리를 사용한다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	public static final class NoOpTransition<C extends StateContext<C>> implements Transition<C> {
		private final Optional<String> m_targetStatePath;

		private NoOpTransition(String targetStatePath) {
			Utilities.checkNotNullArgument(targetStatePath, "targetStatePath is null");

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

	/**
	 * 현 상태에 머무는 self-transition을 표현하는 {@link Transition} 구현.
	 * <p>
	 * {@link #getTargetStatePath()}는 {@link Optional#empty()}을 반환하며,
	 * {@link Transition} 의 컨벤션상 {@link #execute(StateContext, Signal)}는 호출되지 않는다.
	 * 일반적으로 사용자는 본 클래스를 직접 인스턴스화하지 않고
	 * {@link Transitions#stay()} 팩토리를 사용한다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	public static final class StayTransition<C extends StateContext<C>> implements Transition<C> {
		private StayTransition() { }

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
