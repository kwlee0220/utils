package utils.statechart;

/**
 * 상태 차트에 전달되는 사건(event)을 표현하는 표식(marker) 인터페이스.
 * <p>
 * 본 인터페이스는 아무 멤버도 정의하지 않는다. 응용은 필요한 데이터를 담은 구체 타입을 정의하고,
 * 이를 {@link StateChart#handleSignal(Signal)}에 전달한다. 그러면 차트의 현재 상태가
 * {@link State#selectTransition(Signal)}로 이 신호를 처리할 전이가 있는지 판단한다.
 * <p>
 * <b>신호의 식별 방식은 이를 소비하는 상태 구현에 달려 있다.</b> 예를 들어
 * {@link SingleOutState}는 등록된 trigger와 {@link Object#equals(Object)}로 비교하므로,
 * 값이 같으면 같은 신호로 취급되게 하려면 {@code equals}/{@code hashCode}를 재정의하거나
 * {@code record}로 정의해야 한다. 재정의하지 않으면 trigger로 등록한 것과
 * <b>동일한 인스턴스</b>만 전이를 유발한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Signal {
}
