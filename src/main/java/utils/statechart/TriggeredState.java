package utils.statechart;

import java.util.Optional;
import java.util.function.Supplier;

import utils.Preconditions;


/**
 * 지정한 trigger 신호에 반응하여 전이하는 상태.
 * <p>
 * (2026-10-31 이전 이름은 {@code SingleOutState}였다. {@code on(...)}으로 다른 신호의 전이도 더할 수 있게 되어
 * "출구가 하나"라는 이름이 맞지 않게 되었다.)
 * <p>
 * 생성 시 지정된 trigger 신호와 일치하는 신호({@link Object#equals(Object)} 비교)가 들어오면
 * {@link #selectTransition(Signal)}이 등록된 전이를 반환하고, 그 외의 신호는
 * {@link #on(Signal, Transition)} 등으로 추가 등록한 전이 표에 맡긴다(등록한 것이 없으면 {@link Optional#empty()}).
 * 가장 단순한 형태의 상태로,
 * "특정 신호 도착 → 다음 상태로 이동" 시나리오에 적합하다.
 * <p>
 * 전이는 {@link Supplier}로 등록할 수 있어, 매 신호마다 동적으로 생성되는 전이도 표현 가능하다.
 * 정적 전이를 원하면 {@link #TriggeredState(String, StateContext, Signal, Transition)} 생성자를 사용한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class TriggeredState<C extends StateContext<C>> extends AbstractState<C> {
	private final Signal m_trigger;
	private final Supplier<Transition<C>> m_transitionSupplier;

	/**
	 * trigger 신호에 대응하는 전이를 동적으로 공급하는 상태를 생성한다.
	 * <p>
	 * {@code transitionSupplier}는 매번 trigger 신호가 도착할 때마다 호출되어
	 * 그 시점에 사용할 전이를 반환한다.
	 *
	 * @param path						상태의 경로 (non-null)
	 * @param context					상태 컨텍스트
	 * @param trigger					이 상태에서 반응할 단일 신호 (non-null)
	 * @param transitionSupplier		trigger 신호 시 사용할 전이를 반환하는 supplier (non-null)
	 * @throws IllegalArgumentException {@code trigger} 또는 {@code transitionSupplier}가 {@code null}인 경우
	 */
	public TriggeredState(String path, C context, Signal trigger,
						Supplier<Transition<C>> transitionSupplier) {
		super(path, context);

		Preconditions.checkNotNullArgument(trigger, "trigger is null");
		Preconditions.checkNotNullArgument(transitionSupplier, "transitionSupplier is null");

		m_trigger = trigger;
		m_transitionSupplier = transitionSupplier;
	}

	/**
	 * trigger 신호에 대응하는 정적 전이를 갖는 상태를 생성한다.
	 * <p>
	 * 본 생성자는 {@code transition}을 항상 반환하는 supplier로 감싸
	 * {@link #TriggeredState(String, StateContext, Signal, Supplier)}를 호출한다.
	 *
	 * @param path				상태의 경로 (non-null)
	 * @param context			상태 컨텍스트
	 * @param trigger			이 상태에서 반응할 단일 신호 (non-null)
	 * @param transition		trigger 신호 시 수행할 전이 (non-null)
	 * @throws IllegalArgumentException {@code trigger} 또는 {@code transition}이 {@code null}인 경우
	 */
	public TriggeredState(String path, C context, Signal trigger, Transition<C> transition) {
		this(path, context, trigger, supplierOf(transition));
	}

	private static <C extends StateContext<C>> Supplier<Transition<C>> supplierOf(Transition<C> transition) {
		// 고정 전이는 신호가 올 때가 아니라 생성할 때 검사한다.
		Preconditions.checkNotNullArgument(transition, "transition is null");
		return () -> transition;
	}

	/**
	 * 주어진 신호가 등록된 trigger와 일치하면 등록된 전이를 반환한다.
	 * <p>
	 * 신호 비교는 trigger의 {@link Object#equals(Object)}를 사용한다. 따라서 enum이나 record처럼
	 * {@code equals()}가 값을 비교하는 신호는 값이 같으면 일치한다. {@code equals()}를 재정의하지 않은
	 * 일반 클래스의 신호는 trigger로 등록한 것과 동일한 인스턴스여야 일치한다.
	 *
	 * @param signal	들어온 신호
	 * @return	신호가 trigger와 일치하면 등록된 전이, 그렇지 않으면 전이 표({@link #on(Signal, Transition)} 등)에서
	 * 			고른 전이. 둘 다 없으면 {@link Optional#empty()}.
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		Preconditions.checkNotNullArgument(signal, "signal is null");
		
		if ( m_trigger.equals(signal) ) {
			Transition<C> transition = m_transitionSupplier.get();
			Preconditions.checkState(transition != null, "transition supplier returned null: state=%s", getPath());
			return Optional.of(transition);
		}
		else {
			// trigger가 아닌 신호는 on(...)으로 추가 등록한 전이 표에 맡긴다.
			return super.selectTransition(signal);
		}
	}
}
