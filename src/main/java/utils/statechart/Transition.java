package utils.statechart;

import java.util.Optional;


/**
 * 상태 차트에서의 상태 전이를 표현한다.
 * <p>
 * 전이는 목표 상태({@link #getTargetStatePath()})와 전이 시 수행할 액션
 * ({@link #execute(StateContext, Signal)})으로 구성된다. 일반적으로 사용자는 본 인터페이스를
 * 직접 구현하지 않고 {@link Transitions#to(String)}, {@link Transitions#to(String, TransitionAction)},
 * {@link Transitions#internal}, {@link Transitions#stay} 정적 팩토리를 통해 인스턴스를 생성한다.
 * <p>
 * <b>내부 전이(internal transition) 컨벤션</b>: {@link #getTargetStatePath()}가 {@link Optional#empty()}을
 * 반환하면 상태 변경 없이 현 상태에 머무는 내부 전이로 취급된다({@link #isInternal()}). 이 경우 {@link StateChart}는
 * 진입해 있는 상태의 {@code exit()}/{@code enter()} 없이 {@link #execute(StateContext, Signal)}만 호출한다.
 * {@link Transitions#internal}은 주어진 액션을, {@link Transitions#stay}는 빈 액션을 실행한다.
 * <p>
 * 반면 현재 상태의 경로를 목표로 명시하면(UML의 자기 전이, self-transition) 액션은 실행되지만 현재 상태에서
 * 진출({@code exit()})했다가 다시 진입({@code enter()})한다. (2026-10-31 이전에는 목표 경로가 없으면 액션도
 * 실행하지 않아, "머물면서 액션만 실행"하는 전이를 표현할 수 없었다.)
 *
 * @param <C>	{@link StateContext}의 구체 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Transition<C extends StateContext<C>> {
	/**
	 * 본 전이가 도달할 목표 상태의 경로를 반환한다.
	 *
	 * @return	목표 상태의 경로. {@link Optional#empty()}이면 내부 전이로 취급되어 상태 변경 없이
	 *			{@link #execute(StateContext, Signal)}만 호출된다.
	 */
	public Optional<String> getTargetStatePath();

	/**
	 * 본 전이가 상태를 바꾸지 않는 내부 전이인지 여부를 반환한다.
	 * <p>
	 * 기본 구현은 {@link #getTargetStatePath()}가 비어있는지 검사한다. 현재 상태의 경로를 목표로 준 전이
	 * (UML의 자기 전이)는 진출했다가 다시 진입하므로 내부 전이가 아니다.
	 *
	 * @return	내부 전이이면 {@code true}. (2026-10-31 이전 이름은 {@code isSelfTransition()}이었다.)
	 */
	public default boolean isInternal() {
		return getTargetStatePath().isEmpty();
	}

	/**
	 * 전이 액션을 실행한다.
	 * <p>
	 * 본 메소드는 {@link StateChart}가 보유한 lock을 획득한 상태에서 호출된다.
	 * 목표 상태가 있으면 진출과 진입 사이에, 내부 전이이면 상태 변경 없이 호출된다.
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
