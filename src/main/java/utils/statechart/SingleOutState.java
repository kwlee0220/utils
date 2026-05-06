package utils.statechart;

import java.util.Optional;
import java.util.function.Supplier;

import utils.Utilities;


/**
 * 단 하나의 trigger 신호에만 반응하여 단일 전이를 수행하는 상태.
 * <p>
 * 생성 시 지정된 trigger 신호와 정확히 일치하는 신호({@code ==} 비교)가 들어오면
 * {@link #selectTransition(Signal)}이 등록된 전이를 반환하고, 그 외의 신호는
 * {@link Optional#empty()}을 반환하여 무시한다. 가장 단순한 형태의 상태로,
 * "특정 신호 도착 → 다음 상태로 이동" 시나리오에 적합하다.
 * <p>
 * 전이는 {@link Supplier}로 등록할 수 있어, 매 신호마다 동적으로 생성되는 전이도 표현 가능하다.
 * 정적 전이를 원하면 {@link #SingleOutState(String, StateContext, Signal, Transition)} 생성자를 사용한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class SingleOutState<C extends StateContext<C>> extends AbstractState<C> {
	private final Signal m_trigger;
	private final Supplier<Transition<C>> m_outTransitionSupplier;

	/**
	 * trigger 신호에 대응하는 전이를 동적으로 공급하는 상태를 생성한다.
	 * <p>
	 * {@code outTransitionSupplier}는 매번 trigger 신호가 도착할 때마다 호출되어
	 * 그 시점에 사용할 전이를 반환한다.
	 *
	 * @param name						상태의 경로 (non-null)
	 * @param context					상태 컨텍스트
	 * @param trigger					이 상태에서 반응할 단일 신호 (non-null)
	 * @param outTransitionSupplier		trigger 신호 시 사용할 전이를 반환하는 supplier (non-null)
	 * @throws IllegalArgumentException {@code trigger} 또는 {@code outTransitionSupplier}가 {@code null}인 경우
	 */
	public SingleOutState(String name, C context, Signal trigger,
						Supplier<Transition<C>> outTransitionSupplier) {
		super(name, context);

		Utilities.checkNotNullArgument(trigger, "trigger is null");
		Utilities.checkNotNullArgument(outTransitionSupplier, "outTransitionSupplier is null");

		m_trigger = trigger;
		m_outTransitionSupplier = outTransitionSupplier;
	}

	/**
	 * trigger 신호에 대응하는 정적 전이를 갖는 상태를 생성한다.
	 * <p>
	 * 본 생성자는 {@code outTransition}을 항상 반환하는 supplier로 감싸
	 * {@link #SingleOutState(String, StateContext, Signal, Supplier)}를 호출한다.
	 *
	 * @param name				상태의 경로 (non-null)
	 * @param context			상태 컨텍스트
	 * @param trigger			이 상태에서 반응할 단일 신호 (non-null)
	 * @param outTransition		trigger 신호 시 수행할 전이 (non-null)
	 */
	public SingleOutState(String name, C context, Signal trigger, Transition<C> outTransition) {
		this(name, context, trigger, () -> outTransition);
	}

	/**
	 * 주어진 신호가 등록된 trigger와 일치하면 등록된 전이를 반환한다.
	 * <p>
	 * 신호 비교는 {@link Object#equals(Object)} 메서드를 사용하므로, 전이를 유발하려면 trigger로
	 * 등록한 것과 동일한 인스턴스의 신호가 들어와야 한다.
	 *
	 * @param signal	들어온 신호
	 * @return	신호가 trigger와 일치하면 등록된 전이, 그렇지 않으면 {@link Optional#empty()}
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		Utilities.checkNotNullArgument(signal, "signal is null");
		
		if ( m_trigger.equals(signal) ) {
			return Optional.of(m_outTransitionSupplier.get());
		}
		else {
			return Optional.empty();
		}
	}
}
