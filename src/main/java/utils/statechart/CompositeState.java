package utils.statechart;

import org.jetbrains.annotations.Nullable;


/**
 * 내부에 하위 상태들을 갖는 복합(composite) 상태.
 * <p>
 * 계층적 상태 차트를 구성하기 위한 인터페이스로, 자신에게 진입하면 하위 상태 중 하나
 * ({@link #getInitialState()})로 함께 진입하고, 받은 신호는 현재 하위 상태
 * ({@link #getCurrentState()})에게 위임한다. 기본 구현은 {@link DefaultCompositeState}이다.
 * <p>
 * 하위 상태의 전이는 {@link StateChart}가 관리하는 최상위 상태 맵을 거치지 않고 복합 상태 내부에서
 * 처리된다. 따라서 {@link #getCurrentState()}가 가리키는 상태는 {@link StateChart#getCurrentState()}가
 * 반환하는 상태(= 이 복합 상태 자신)와 다르다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CompositeState<C extends StateContext<C>> extends State<C> {
	/**
	 * 본 복합 상태에 진입할 때 함께 진입할 최초 하위 상태를 반환한다.
	 *
	 * @return	최초 하위 상태. 아직 설정되지 않았으면 {@code null}.
	 */
	public State<C> getInitialState();

	/**
	 * 현재 진입 중인 하위 상태를 반환한다.
	 * <p>
	 * 아직 composite 상태에 진입하지 않았거나 이미 진출한 경우에는
	 * {@code null}을 반환할 수 있다.
	 *
	 * @return 현재 하위 상태. 없으면 {@code null}.
	 */
	public @Nullable State<C> getCurrentState();
}
