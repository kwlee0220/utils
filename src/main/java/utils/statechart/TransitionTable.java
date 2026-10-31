package utils.statechart;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

import utils.Preconditions;


/**
 * 신호별 전이를 선언적으로 등록해 두고, 들어온 신호에 맞는 전이를 골라 주는 전이 표.
 * <p>
 * {@link State#selectTransition(Signal)}마다 {@code instanceof} 분기와 {@code Optional.of(...)}를 반복하는 대신,
 * 신호와 전이의 짝을 표처럼 등록한다. {@link AbstractState}는 이 표를 하나 갖고 있어
 * {@link AbstractState#on(Signal, Transition)} 등으로 바로 등록할 수 있다. 다른 {@link State} 구현에서는
 * 필드로 두고 {@code selectTransition()}에서 {@link #select(Signal)}를 호출한다.
 * <pre>{@code
 * TransitionTable<Ctx> table = new TransitionTable<Ctx>()
 *         .on(Signals.START, Transitions.to("/Busy"))
 *         .on(JobFailed.class, sig -> Transitions.fail("/Error", s -> sig.cause()))
 *         .on(Progress.class, Transitions.internal((ctx, sig) -> ctx.update((Progress)sig)));
 * }</pre>
 * <ul>
 *   <li>{@link #on(Signal, Transition)}: 등록한 신호와 {@link Object#equals(Object)}로 같은 신호에 반응한다.
 *       {@link TriggeredState}와 같은 비교 방식이다.</li>
 *   <li>{@link #on(Class, Transition)}: 주어진 타입(하위 타입 포함)의 신호에 반응한다.</li>
 *   <li>{@link #on(Class, Predicate, Transition)}: 주어진 타입의 신호 중 조건을 만족하는 신호에 반응한다.
 *       조건을 만족하지 않으면 다음 항목을 확인한다.</li>
 *   <li>{@link #on(Class, Function)}: 주어진 타입의 신호를 받아 전이를 만든다. 함수가 {@code null}을 반환하면
 *       이 항목은 신호를 선택하지 않은 것으로 보고 다음 항목을 확인한다. 신호의 값에 따라 전이를 고르거나
 *       조건부로 처리할 때 쓴다.</li>
 * </ul>
 * 항목은 등록한 순서대로 확인하며, 처음으로 전이를 고른 항목이 이긴다. 맞는 항목이 없으면
 * {@link Optional#empty()}이다.
 * <p>
 * 항목은 보통 차트를 시작하기 전에 등록한다. 내부 목록은 {@link CopyOnWriteArrayList}라서 다른 스레드에서
 * 등록해도 안전하지만, 실행 중에 등록한 항목은 그 뒤에 들어오는 신호부터 반영된다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class TransitionTable<C extends StateContext<C>> {
	private final List<Entry<C>> m_entries = new CopyOnWriteArrayList<>();

	private record Entry<C extends StateContext<C>>(Predicate<Signal> matcher,
													Function<Signal, Transition<C>> selector,
													String description) { }

	/**
	 * 주어진 신호와 같은({@link Object#equals(Object)}) 신호에 대한 전이를 등록한다.
	 *
	 * @param signal		반응할 신호 (non-null)
	 * @param transition	선택할 전이 (non-null)
	 * @return	이 표.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	public TransitionTable<C> on(Signal signal, Transition<C> transition) {
		Preconditions.checkNotNullArgument(signal, "signal is null");
		Preconditions.checkNotNullArgument(transition, "transition is null");

		m_entries.add(new Entry<>(signal::equals, sig -> transition, signal + " -> " + transition));
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호에 대한 전이를 등록한다.
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param transition	선택할 전이 (non-null)
	 * @return	이 표.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	public <S extends Signal> TransitionTable<C> on(Class<S> signalType, Transition<C> transition) {
		Preconditions.checkNotNullArgument(signalType, "signalType is null");
		Preconditions.checkNotNullArgument(transition, "transition is null");

		m_entries.add(new Entry<>(signalType::isInstance, sig -> transition,
									signalType.getSimpleName() + " -> " + transition));
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호 중 조건({@code guard})을 만족하는 신호에 대한 전이를 등록한다.
	 * <p>
	 * 조건을 만족하지 않으면 이 항목은 신호를 선택하지 않은 것으로 보고 다음 항목을 확인한다. 조건과 전이를
	 * 나눠 적을 수 있어, 선택 함수에서 {@code null}을 반환하는 것보다 의도가 잘 드러난다.
	 * <pre>{@code
	 * table.on(Progress.class, p -> p.percent() >= 100, Transitions.to("/Done"))
	 *      .on(Progress.class, Transitions.stay());
	 * }</pre>
	 * 조건이 던진 예외는 {@link State#selectTransition(Signal)}이 던진 예외와 같게 처리된다(차트가 실패한다).
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param guard			전이를 선택할 조건 (non-null)
	 * @param transition	조건을 만족할 때 선택할 전이 (non-null)
	 * @return	이 표.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	public <S extends Signal> TransitionTable<C> on(Class<S> signalType, Predicate<? super S> guard,
													Transition<C> transition) {
		Preconditions.checkNotNullArgument(signalType, "signalType is null");
		Preconditions.checkNotNullArgument(guard, "guard is null");
		Preconditions.checkNotNullArgument(transition, "transition is null");

		m_entries.add(new Entry<>(sig -> signalType.isInstance(sig) && guard.test(signalType.cast(sig)),
									sig -> transition, signalType.getSimpleName() + " [guard] -> " + transition));
		return this;
	}

	/**
	 * 주어진 타입(하위 타입 포함)의 신호를 받아 전이를 만드는 함수를 등록한다.
	 * <p>
	 * 함수가 {@code null}을 반환하면 이 항목은 신호를 선택하지 않은 것으로 보고 다음 항목을 확인한다.
	 * 함수가 던진 예외는 {@link State#selectTransition(Signal)}이 던진 예외와 같게 처리된다(차트가 실패한다).
	 *
	 * @param <S>			신호 타입
	 * @param signalType	반응할 신호 타입 (non-null)
	 * @param selector		신호로부터 전이를 만드는 함수 (non-null). {@code null}을 반환할 수 있다.
	 * @return	이 표.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	public <S extends Signal> TransitionTable<C> on(Class<S> signalType,
													Function<? super S, ? extends Transition<C>> selector) {
		Preconditions.checkNotNullArgument(signalType, "signalType is null");
		Preconditions.checkNotNullArgument(selector, "selector is null");

		m_entries.add(new Entry<>(signalType::isInstance, sig -> selector.apply(signalType.cast(sig)),
									signalType.getSimpleName() + " -> (selector)"));
		return this;
	}

	/**
	 * 주어진 신호에 대한 전이를 고른다.
	 * <p>
	 * 등록한 순서대로 항목을 확인하여, 처음으로 전이를 고른 항목의 전이를 반환한다.
	 *
	 * @param signal	들어온 신호 (non-null)
	 * @return	선택된 전이. 맞는 항목이 없으면 {@link Optional#empty()}.
	 * @throws IllegalArgumentException	{@code signal}이 {@code null}인 경우.
	 */
	public Optional<Transition<C>> select(Signal signal) {
		Preconditions.checkNotNullArgument(signal, "signal is null");

		for ( Entry<C> entry: m_entries ) {
			if ( entry.matcher().test(signal) ) {
				Transition<C> selected = entry.selector().apply(signal);
				if ( selected != null ) {
					return Optional.of(selected);
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * 등록된 항목이 없는지 여부를 반환한다.
	 *
	 * @return	항목이 없으면 {@code true}.
	 */
	public boolean isEmpty() {
		return m_entries.isEmpty();
	}

	@Override
	public String toString() {
		return m_entries.stream().map(Entry::description).toList().toString();
	}
}
