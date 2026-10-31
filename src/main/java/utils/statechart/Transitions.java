package utils.statechart;

import java.util.Optional;
import java.util.function.Function;

import utils.Preconditions;

/**
 * {@link Transition} 인스턴스를 생성하는 정적 팩토리 모음.
 * <p>
 * 사용자는 {@link Transition} 인터페이스를 직접 구현하기보다는 본 유틸리티 클래스의
 * 정적 팩토리 메소드 ({@link #to(String)}, {@link #to(String, TransitionAction)},
 * {@link #internal(TransitionAction)}, {@link #stay()}, {@link #fail(String, Function)}) 를 통해 전이를
 * 생성하는 것을 권장한다.
 * <p>
 * 네 가지 표준 전이 형태:
 * <ul>
 *   <li>{@link #to(String, TransitionAction) to(path, action)} — 목표 상태로 이동하면서 액션 실행</li>
 *   <li>{@link #to(String) to(path)} — 목표 상태로 이동만 하고 액션은 실행하지 않음</li>
 *   <li>{@link #internal(TransitionAction) internal} — 현 상태에 머물면서 액션만 실행
 *       ({@code exit()}/{@code enter()} 없음)</li>
 *   <li>{@link #stay() stay} — 현 상태에 머물고 아무것도 실행하지 않음</li>
 * </ul>
 * 현재 상태의 경로를 목표로 지정한 {@code to}는 현재 상태에서 진출했다가 다시 진입한다.
 * 상태를 유지한 채 작업만 하려면 {@code internal}을 사용한다.
 * <p>
 * {@link #fail(String, Function) fail}은 {@link FailureState}로 이동하면서 그 상태에 실패 원인을 설정하는
 * {@code to(path, action)}의 특수형이다.
 * <p>
 * (2026-10-31 이전 이름은 {@code create(path, action)}과 {@code noop(path)}였다. 특히 {@code noop}은
 * "아무것도 하지 않는다"로 읽히지만 실제로는 상태를 이동해서 이름을 바꿨다.)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Transitions {
	private static final Transition<?> STAY_SINGLETON = new StayTransition<>();

	private Transitions() {
		throw new AssertionError("This class should not be instantiated");
	}

	/**
	 * 목표 상태로 이동하면서 액션을 실행하는 전이를 생성한다.
	 * <p>
	 * 차트는 현재 상태(들)에서 진출한 뒤 액션을 실행하고 목표 상태로 진입한다.
	 *
	 * @param <C>				상태 컨텍스트 타입
	 * @param targetStatePath	전이 후 도달할 목표 상태의 경로 (non-null)
	 * @param action			전이 실행 시 수행할 액션 (non-null)
	 * @return					생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code targetStatePath} 또는 {@code action}이 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> to(String targetStatePath, TransitionAction<C> action) {
		return new DefaultTransition<C>(targetStatePath, action);
	}

	/**
	 * 목표 상태로 이동만 하고 액션은 실행하지 않는 전이를 생성한다.
	 *
	 * @param <C>				상태 컨텍스트 타입
	 * @param targetStatePath	전이 후 도달할 목표 상태의 경로 (non-null)
	 * @return					생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code targetStatePath}가 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> to(String targetStatePath) {
		return new NoOpTransition<C>(targetStatePath);
	}

	/**
	 * 현 상태에 머물면서 액션만 실행하는 내부 전이를 생성한다.
	 * <p>
	 * 반환된 전이의 {@link Transition#getTargetStatePath()}는 {@link Optional#empty()}을 반환한다.
	 * 차트는 진입해 있는 상태의 {@code exit()}/{@code enter()} 없이 액션만 실행한다. 액션이 예외를 던지면
	 * 다른 전이와 같이 차트가 실패 상태로 종료된다. 신호를 받을 때마다 컨텍스트를 갱신하거나 메시지를
	 * 보내는 등, 상태는 그대로 두고 부수 작업만 하는 경우에 사용한다.
	 *
	 * @param <C>		상태 컨텍스트 타입
	 * @param action	실행할 액션 (non-null)
	 * @return			생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code action}이 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> internal(TransitionAction<C> action) {
		return new InternalTransition<C>(action);
	}

	/**
	 * {@link FailureState}로 이동하면서 그 상태에 실패 원인을 설정하는 전이를 생성한다.
	 * <p>
	 * 전이 액션이 {@code causeFunc}에 신호를 넘겨 원인을 얻고, 목표 상태의
	 * {@link FailureState#setFailureCause(Throwable)}를 호출한다. 목표가 최종 상태이면 차트는 그 원인으로
	 * 실패한다. 전이 액션에서 차트로부터 상태를 찾아 형 변환한 뒤 원인을 설정하던 코드를 대신한다.
	 * <pre>{@code
	 * if ( signal instanceof JobFailed ) {
	 *     return Optional.of(Transitions.fail("/Failed", sig -> ((JobFailed)sig).cause()));
	 * }
	 * }</pre>
	 * 다음 경우에는 원인을 설정하지 못하고 {@link IllegalStateException}을 던진다. 전이 액션의 예외이므로
	 * 차트는 그 예외를 원인으로 실패한다.
	 * <ul>
	 *   <li>컨텍스트가 차트에 연결되어 있지 않은 경우</li>
	 *   <li>목표 경로의 상태가 {@link FailureState}가 아닌 경우</li>
	 *   <li>{@code causeFunc}이 {@code null}을 반환한 경우</li>
	 * </ul>
	 *
	 * @param <C>				상태 컨텍스트 타입
	 * @param targetStatePath	목표 {@link FailureState}의 경로 (non-null)
	 * @param causeFunc			전이를 유발한 신호로부터 실패 원인을 만드는 함수 (non-null)
	 * @return					생성된 {@link Transition}
	 * @throws IllegalArgumentException {@code targetStatePath} 또는 {@code causeFunc}이 {@code null}인 경우
	 */
	public static <C extends StateContext<C>> Transition<C> fail(String targetStatePath,
													Function<? super Signal, ? extends Throwable> causeFunc) {
		return new FailTransition<C>(targetStatePath, causeFunc);
	}

	/**
	 * 현 상태에 머물고 아무것도 실행하지 않는 전이를 생성한다.
	 * <p>
	 * 반환된 전이의 {@link Transition#getTargetStatePath()}는 {@link Optional#empty()}을 반환하며
	 * {@link Transition#execute(StateContext, Signal)}는 아무 일도 하지 않는다. 즉 액션이 비어 있는
	 * {@link #internal(TransitionAction)}과 같다. 신호를 처리했다는 표시(다른 처리로 넘어가지 않게 함)로 쓴다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 * @return		액션이 비어 있는 내부 전이를 표현하는 {@link Transition}
	 */
	@SuppressWarnings("unchecked")
	public static <C extends StateContext<C>> Transition<C> stay() {
		return (Transition<C>) STAY_SINGLETON;
	}

	/**
	 * 목표 상태와 액션을 모두 갖는 {@link Transition}의 기본 구현.
	 * <p>
	 * {@link Transitions#to(String, TransitionAction)}가 만든다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	private static final class DefaultTransition<C extends StateContext<C>> implements Transition<C> {
		private final Optional<String> m_targetStatePath;
		private final TransitionAction<C> m_action;

		private DefaultTransition(String targetStatePath, TransitionAction<C> action) {
			Preconditions.checkNotNullArgument(targetStatePath, "targetStatePath is null");
			Preconditions.checkNotNullArgument(action, "TransitionAction is null");

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
	 * 발생한다. {@link Transitions#to(String)}가 만든다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	private static final class NoOpTransition<C extends StateContext<C>> implements Transition<C> {
		private final Optional<String> m_targetStatePath;

		private NoOpTransition(String targetStatePath) {
			Preconditions.checkNotNullArgument(targetStatePath, "targetStatePath is null");

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
			return "-> " + m_targetStatePath.get();
		}
	}

	/**
	 * {@link FailureState}로 이동하면서 그 상태에 실패 원인을 설정하는 {@link Transition} 구현.
	 * <p>
	 * {@link Transitions#fail(String, Function)}가 만든다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	private static final class FailTransition<C extends StateContext<C>> implements Transition<C> {
		private final Optional<String> m_targetStatePath;
		private final Function<? super Signal, ? extends Throwable> m_causeFunc;

		private FailTransition(String targetStatePath, Function<? super Signal, ? extends Throwable> causeFunc) {
			Preconditions.checkNotNullArgument(targetStatePath, "targetStatePath is null");
			Preconditions.checkNotNullArgument(causeFunc, "causeFunc is null");

			m_targetStatePath = Optional.of(targetStatePath);
			m_causeFunc = causeFunc;
		}

		@Override
		public Optional<String> getTargetStatePath() {
			return m_targetStatePath;
		}

		@Override
		public void execute(C context, Signal signal) {
			String path = m_targetStatePath.get();
			StateChart<C> chart = context.getStateChart();
			Preconditions.checkState(chart != null, "StateContext is not attached to a StateChart");
			if ( !(chart.findState(path).orElse(null) instanceof FailureState<C> errorState) ) {
				throw new IllegalStateException("target state is not a FailureState: " + path);
			}

			Throwable cause = m_causeFunc.apply(signal);
			Preconditions.checkState(cause != null, "failure cause function returned null: signal=%s", signal);
			errorState.setFailureCause(cause);
		}

		@Override
		public String toString() {
			return "fail -> " + m_targetStatePath.get();
		}
	}

	/**
	 * 현 상태에 머물면서 액션만 실행하는 {@link Transition} 구현.
	 * <p>
	 * {@link #getTargetStatePath()}는 {@link Optional#empty()}을 반환한다.
	 * {@link Transitions#internal(TransitionAction)}가 만든다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	private static final class InternalTransition<C extends StateContext<C>> implements Transition<C> {
		private final TransitionAction<C> m_action;

		private InternalTransition(TransitionAction<C> action) {
			Preconditions.checkNotNullArgument(action, "TransitionAction is null");

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
			return "internal(" + m_action + ")";
		}
	}

	/**
	 * 현 상태에 머물고 아무것도 실행하지 않는 {@link Transition} 구현.
	 * <p>
	 * {@link #getTargetStatePath()}는 {@link Optional#empty()}을 반환하며,
	 * {@link #execute(StateContext, Signal)}는 아무 일도 하지 않는다. {@link Transitions#stay()}가 반환하는 싱글턴이다.
	 *
	 * @param <C>	상태 컨텍스트 타입
	 */
	private static final class StayTransition<C extends StateContext<C>> implements Transition<C> {
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
