package utils.statechart;


import org.jetbrains.annotations.NotNull;

import utils.Preconditions;

/**
 * {@link CompositeState}의 기본 구현.
 * <p>
 * 생성할 때 최초 하위 상태의 경로를 지정한다. 하위 상태의 진입·진출과 신호 위임은 {@link StateChart}가
 * 수행하므로, 이 클래스의 {@code enter()}/{@code exit()}는 복합 상태 자신의 작업만 담는다(기본은 아무 일도 하지 않음).
 * <p>
 * 하위 상태 어디에 있든 공통으로 적용할 전이는 {@link #on(Class, Transition)} 등으로 전이 표에 등록한다.
 * 등록한 항목이 없으면 전이를 선택하지 않는다. 차트는 하위 상태가 전이를 선택하지 않은 신호만 이 상태에 넘긴다.
 * 전이를 직접 계산해야 하면 {@link #selectTransition(Signal)}을 재정의한다.
 *
 * <pre>{@code
 * chart.addState(new DefaultCompositeState<>("/Running", ctx, "/Running/Idle")
 *                     .on(Disconnected.class, Transitions.to("/Failed")));
 * chart.addState(new IdleState("/Running/Idle", ctx));
 * chart.addState(new BusyState("/Running/Busy", ctx));
 * }</pre>
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultCompositeState<C extends StateContext<C>> extends AbstractState<C> implements CompositeState<C> {
	/** 진입 시 이어서 진입할 최초 하위 상태의 경로. */
	private final String m_initialStatePath;

	/**
	 * 주어진 위치 경로와 상태 컨텍스트, 최초 하위 상태 경로로 복합 상태를 생성한다.
	 *
	 * @param path				상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context			상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @param initialStatePath	최초 하위 상태의 위치 경로. 본 상태의 직속 하위 경로여야 하며,
	 * 							이 조건은 {@link StateChart#start()}에서 검사한다.
	 * @throws IllegalArgumentException	인자 중 {@code null}이 있는 경우.
	 */
	public DefaultCompositeState(String path, C context, String initialStatePath) {
		super(path, context);

		Preconditions.checkNotNullArgument(initialStatePath, "initialStatePath");
		m_initialStatePath = initialStatePath;
	}

	@Override
	public @NotNull String getInitialStatePath() {
		return m_initialStatePath;
	}
}
