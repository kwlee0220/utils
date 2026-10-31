package utils.statechart;

import org.jetbrains.annotations.NotNull;


/**
 * 내부에 하위 상태들을 갖는 복합(composite) 상태.
 * <p>
 * 상태 사이의 부모·자식 관계는 위치 경로로 정해진다. 경로의 마지막 {@code '/'} 앞부분이 부모 경로다.
 * 예를 들어 {@code "/Running/Idle"}의 부모는 {@code "/Running"}이다. 하위 상태는 복합 상태와 마찬가지로
 * {@link StateChart#addState(State)}로 차트에 등록하며, 부모 경로로 등록된 상태는 반드시 복합 상태여야 한다.
 * 이 관계는 {@link StateChart#start()}에서 검사한다.
 * <p>
 * 차트는 루트부터 가장 안쪽 상태까지 진입해 있는 상태들의 체인을 관리한다({@link StateChart#getActiveStates()}).
 * <ul>
 *   <li><b>진입</b>: 복합 상태에 진입하면 차트가 이어서 {@link #getInitialStatePath()}의 하위 상태로 진입한다.
 *       그 하위 상태도 복합 상태이면 같은 방식으로 더 안쪽까지 진입한다. 복합 상태의 {@code enter()}는
 *       하위 상태보다 먼저 호출된다.</li>
 *   <li><b>진출</b>: 가장 안쪽 하위 상태부터 바깥쪽으로 차례로 {@code exit()}가 호출된다.</li>
 *   <li><b>신호 처리</b>: 차트는 먼저 가장 안쪽 상태에게 전이를 묻고, 선택하지 않으면 바깥쪽 복합 상태에게
 *       차례로 묻는다. 따라서 복합 상태의 {@link #selectTransition(Signal)}에는 하위 상태 어느 곳에 있든
 *       공통으로 적용할 전이(예: 연결 끊김 시 오류 상태로 이동)를 둔다. 기본 구현
 *       {@link DefaultCompositeState}에서는 {@link AbstractState#on(Class, Transition)} 등으로 등록한다.</li>
 * </ul>
 * 복합 상태 자신은 하위 상태를 직접 진입·진출시키지 않는다. 이 작업은 모두 차트가 수행한다.
 * 기본 구현은 {@link DefaultCompositeState}이다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CompositeState<C extends StateContext<C>> extends State<C> {
	/**
	 * 본 복합 상태에 진입할 때 이어서 진입할 최초 하위 상태의 경로를 반환한다.
	 * <p>
	 * 본 복합 상태의 직속 하위 상태 경로여야 하며, 차트에 등록되어 있어야 한다.
	 * 이 조건은 {@link StateChart#start()}에서 검사한다.
	 *
	 * @return	최초 하위 상태의 위치 경로.
	 */
	public @NotNull String getInitialStatePath();
}
