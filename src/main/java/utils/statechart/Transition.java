package utils.statechart;

import java.util.Optional;


/**
 * 상태 차트에서의 상태 전이를 표현한다.
 * <p>
 * 전이는 목표 상태({@link #getTargetStatePath()})와 전이 시 수행할 액션
 * ({@link #execute(StateContext, Signal)})으로 구성된다. 일반적으로 사용자는 본 인터페이스를
 * 직접 구현하지 않고 {@link Transitions#create}, {@link Transitions#noop},
 * {@link Transitions#stay} 정적 팩토리를 통해 인스턴스를 생성한다.
 * <p>
 * <b>self-transition 컨벤션</b>: {@link #getTargetStatePath()}가 {@link Optional#empty()}을
 * 반환하면 상태 변경 없이 현 상태에 머무는 self-transition으로 취급된다. 이 경우
 * {@link StateChart}의 traverse 로직은 {@link #execute(StateContext, Signal)} 호출 없이
 * 즉시 반환한다.
 * <p>
 * <b>설계 한계</b>: 현재 디자인에서는 "현재 상태에 머물면서 액션을 실행" 하는 경우를
 * 직접 표현할 수 없다. self-transition({@link Optional#empty()})은 액션을 실행하지 않으며,
 * 명시적으로 현재 상태 경로를 목표로 지정하면 액션은 실행되지만 현재 상태의 {@code exit()}와
 * 재진입({@code enter()})이 다시 일어난다. "부수 작업만 수행하고 머무르기"가 필요한 경우는
 * 신호를 받기 전에 컨텍스트를 갱신하는 등 다른 메커니즘을 사용해야 한다.
 *
 * @param <C>	{@link StateContext}의 구체 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Transition<C extends StateContext<C>> {
	/**
	 * 본 전이가 도달할 목표 상태의 경로를 반환한다.
	 *
	 * @return	목표 상태의 경로. {@link Optional#empty()}이면 self-transition으로 취급되어
	 *			상태 변경이 일어나지 않으며 {@link #execute(StateContext, Signal)}도 호출되지 않는다.
	 */
	public Optional<String> getTargetStatePath();

	/**
	 * 본 전이가 self-transition인지 여부를 반환한다.
	 * <p>
	 * 기본 구현은 {@link #getTargetStatePath()}가 비어있는지 검사한다.
	 *
	 * @return	self-transition이면 {@code true}
	 */
	public default boolean isSelfTransition() {
		return getTargetStatePath().isEmpty();
	}

	/**
	 * 전이 액션을 실행한다.
	 * <p>
	 * 본 메소드는 {@link StateChart}가 보유한 lock을 획득한 상태에서 호출된다.
	 * self-transition인 경우 본 메소드는 호출되지 않는다.
	 * <p>
	 * <b>예외 처리</b>: 본 메소드가 예외(런타임/{@link Error})를 던지면 호출 중인
	 * {@link StateChart}는 fail 상태로 전이되며 ({@code notifyFailed(cause)} 호출),
	 * 예외는 호출 스택을 통해 전파되지 않고 차트 내부에서 흡수된다. 따라서 액션 실행의
	 * 성공/실패 여부는 {@link StateChart}의 상태(또는 {@code FinishListener})를 통해
	 * 관찰해야 한다.
	 *
	 * @param context	상태 컨텍스트
	 * @param signal	전이를 유발한 신호
	 */
	public void execute(C context, Signal signal);
}
