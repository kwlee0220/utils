package utils.statechart;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import utils.Preconditions;

/**
 * {@link State} 구현의 공통 기반 클래스.
 * <p>
 * 상태의 위치 경로({@code path})와 상태문맥({@code context})을 보관하며, 경로를 {@code '/'} 기준으로
 * 분리한 세그먼트 목록도 함께 제공한다. {@link #enter()} / {@link #exit()}는 상속받은 구현체가 필요에 따라
 * override한다.
 * <p>
 * 신호에 대한 전이는 두 가지 방법으로 정할 수 있다. 하나는 {@link #on(Signal, Transition)} 등으로 전이 표
 * ({@link TransitionTable})에 신호와 전이의 짝을 등록하는 것이다. 기본 {@link #selectTransition(Signal)}이 이 표에서
 * 전이를 고른다. 다른 하나는 {@link #selectTransition(Signal)}을 직접 재정의하는 것이다. 재정의하면 표는 쓰이지
 * 않으므로, 두 방법을 섞으려면 재정의한 메소드에서 {@code super.selectTransition(signal)}을 호출한다.
 * <pre>{@code
 * chart.addState(new IdleState("/Idle", ctx)
 *                     .on(Signals.START, Transitions.to("/Busy"))
 *                     .on(Shutdown.class, Transitions.to("/Done")));
 * }</pre>
 *
 * @param <C>	상태문맥의 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractState<C extends StateContext<C>> implements State<C> {
	private @NotNull final String m_path;
	private final List<String> m_pathSegments;
	private @NotNull final C m_context;
	private final TransitionTable<C> m_transitions = new TransitionTable<>();

	/**
	 * 주어진 위치 경로와 상태문맥으로 상태를 생성한다.
	 * <p>
	 * 위치 경로는 {@code '/'}로 구분된 계층 경로(예: {@code "/Running/ReceivingVideo"})로 해석되며,
	 * 선두 {@code '/'}로 인해 생기는 빈 세그먼트는 제거된다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태문맥. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	protected AbstractState(String path, C context) {
		Preconditions.checkNotNullArgument(path, "path");
		Preconditions.checkNotNullArgument(context, "context");

		m_path = path;
		m_pathSegments = StatePath.segments(path);
		m_context = context;
	}

	@Override
	public @NotNull String getPath() {
		return m_path;
	}

	/**
	 * 위치 경로를 {@code '/'} 기준으로 분리한 세그먼트 목록을 반환한다.
	 * <p>
	 * 선두 {@code '/'}로 인한 빈 세그먼트는 포함되지 않는다.
	 * 예를 들어 경로가 {@code "/Running/ReceivingVideo"}이면 {@code ["Running", "ReceivingVideo"]}를 반환한다.
	 *
	 * @return	경로 세그먼트 목록
	 */
	public @NotNull List<String> getPathSegments() {
		return m_pathSegments;
	}

	@Override
	public @NotNull C getContext() {
		return m_context;
	}

	/**
	 * 주어진 신호와 같은({@link Object#equals(Object)}) 신호에 대한 전이를 전이 표에 등록한다.
	 *
	 * @param signal		반응할 신호 (non-null)
	 * @param transition	선택할 전이 (non-null)
	 * @return	이 상태.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 * @see TransitionTable#on(Signal, Transition)
	 */
	public AbstractState<C> on(Signal signal, Transition<C> transition) {
		m_transitions.on(signal, transition);
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호에 대한 전이를 전이 표에 등록한다.
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param transition	선택할 전이 (non-null)
	 * @return	이 상태.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 * @see TransitionTable#on(Class, Transition)
	 */
	public <S extends Signal> AbstractState<C> on(Class<S> signalType, Transition<C> transition) {
		m_transitions.on(signalType, transition);
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호 중 조건을 만족하는 신호에 대한 전이를 전이 표에 등록한다.
	 * 조건을 만족하지 않으면 이 항목은 신호를 선택하지 않는다.
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param guard			전이를 선택할 조건 (non-null)
	 * @param transition	조건을 만족할 때 선택할 전이 (non-null)
	 * @return	이 상태.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 * @see TransitionTable#on(Class, Predicate, Transition)
	 */
	public <S extends Signal> AbstractState<C> on(Class<S> signalType, Predicate<? super S> guard,
												Transition<C> transition) {
		m_transitions.on(signalType, guard, transition);
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호를 받아 전이를 만드는 함수를 전이 표에 등록한다.
	 * 함수가 {@code null}을 반환하면 이 항목은 신호를 선택하지 않는다.
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param selector		신호로부터 전이를 만드는 함수 (non-null)
	 * @return	이 상태.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 * @see TransitionTable#on(Class, Function)
	 */
	public <S extends Signal> AbstractState<C> on(Class<S> signalType,
												Function<? super S, ? extends Transition<C>> selector) {
		m_transitions.on(signalType, selector);
		return this;
	}

	/**
	 * 전이 표({@link #on(Signal, Transition)} 등으로 등록한 항목)에서 신호에 맞는 전이를 고른다.
	 * <p>
	 * 등록한 항목이 없으면 항상 {@link Optional#empty()}를 반환한다. 전이를 직접 계산하려면 재정의한다.
	 *
	 * @param signal	처리할 신호
	 * @return	선택된 전이. 맞는 항목이 없으면 {@link Optional#empty()}.
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		return m_transitions.select(signal);
	}

	@Override
	public String toString() {
		return String.format("State[%s]", m_path);
	}
}
