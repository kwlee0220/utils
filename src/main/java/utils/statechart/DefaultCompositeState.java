package utils.statechart;

import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * {@link CompositeState}의 기본 구현.
 * <p>
 * 진입({@link #enter()}) 시 설정된 최초 하위 상태로 함께 진입하고, 진출({@link #exit()}) 시
 * 하위 상태에서도 함께 진출한다. 받은 신호는 현재 하위 상태에게 그대로 위임한다.
 * <p>
 * <b>진입/진출은 짝을 이루어야 한다.</b> 이미 진입한 상태에서 다시 {@link #enter()}를 부르거나
 * 진입하지 않은 상태에서 {@link #exit()}를 부르면 {@link IllegalStateException}이 발생한다.
 * 현재 하위 상태의 보관 여부가 곧 진입 여부이기 때문에, 진출 후에는
 * {@link #getCurrentState()}가 {@code null}이 된다.
 * <p>
 * <b>사용 전 최초 하위 상태를 반드시 설정해야 한다.</b> 설정하지 않고 진입하면
 * {@link NullPointerException}이 발생한다. 다만 설정 메소드가 package-private이므로
 * 현재로서는 {@code utils.statechart} 패키지 밖에서 설정할 방법이 없다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultCompositeState<C extends StateContext<C>> extends AbstractState<C> implements CompositeState<C> {
	/** 진입 시 함께 진입할 최초 하위 상태. */
	private State<C> m_initialState;
	/** 현재 진입 중인 하위 상태. 진입 전과 진출 후에는 {@code null}이며, 진입 여부의 판정 기준이 된다. */
	private State<C> m_currentState;

	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 복합 상태를 생성한다.
	 * <p>
	 * 최초 하위 상태는 설정되지 않은 상태로 시작한다.
	 *
	 * @param name		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code name} 또는 {@code context}가 {@code null}인 경우.
	 */
	public DefaultCompositeState(String name, C context) {
		super(name, context);
	}

	@Override
	public State<C> getInitialState() {
		return m_initialState;
	}

	/**
	 * 본 복합 상태에 진입할 때 함께 진입할 최초 하위 상태를 설정한다.
	 * <p>
	 * 진입 전에 호출되어야 한다. 이미 진입한 뒤에 호출하면 현재 하위 상태는 바뀌지 않고
	 * 다음 진입부터 반영된다.
	 *
	 * @param initialState	최초 하위 상태. {@code null}이 아니어야 한다.
	 * @throws NullPointerException	{@code initialState}가 {@code null}인 경우.
	 */
	void setInitialState(State<C> initialState) {
		Preconditions.checkNotNull(initialState, "initialState is null");

		m_initialState = initialState;
	}

	@Override
	public State<C> getCurrentState() {
		return m_currentState;
	}

	/**
	 * 본 복합 상태와 최초 하위 상태에 차례로 진입한다.
	 *
	 * @throws IllegalStateException	이미 진입한 상태인 경우.
	 * @throws NullPointerException		최초 하위 상태가 설정되지 않은 경우.
	 */
	@Override
	public void enter() {
		Preconditions.checkState(m_currentState == null, "CompositeState is already entered");

		m_currentState = m_initialState;
		m_currentState.enter();
	}

	/**
	 * 현재 하위 상태에서 진출한 뒤 본 복합 상태에서도 진출한다.
	 * <p>
	 * 진출 후에는 {@link #getCurrentState()}가 {@code null}을 반환한다.
	 *
	 * @throws IllegalStateException	진입하지 않은 상태인 경우.
	 */
	@Override
	public void exit() {
		Preconditions.checkState(m_currentState != null, "CompositeState is not entered");

		m_currentState.exit();
		m_currentState = null;
	}

	/**
	 * 신호 처리를 현재 하위 상태에게 위임한다.
	 * <p>
	 * 반환된 전이의 목표 경로는 {@link StateChart}가 보유한 최상위 상태 맵에서 해석된다는 점에 주의한다.
	 *
	 * @param signal	처리할 신호.
	 * @return	현재 하위 상태가 선택한 전이. 처리할 전이가 없으면 {@link Optional#empty()}.
	 * @throws NullPointerException	진입하지 않은 상태인 경우.
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal)  {
        return m_currentState.selectTransition(signal);
	}
}
