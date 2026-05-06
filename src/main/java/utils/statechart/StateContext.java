package utils.statechart;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 상태 차트의 도메인 컨텍스트.
 * <p>
 * 자기 자신이 속한 {@link StateChart}에 대한 역참조를 보유한다.
 * {@link #setStateChart(StateChart)}는 {@link StateChart} 생성자가 한 번 호출하는
 * 프레임워크 내부용 콜백이며, 외부 코드는 호출하지 않는다.
 *
 * @param <C> 자기 자신을 가리키는 self-bound 타입 파라미터 (CRTP 패턴).
 *            	구현체는 반드시 자기 타입을 인자로 명시한다
 * 				(예: {@code class Foo implements StateContext<Foo>}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface StateContext<C extends StateContext<C>> {
	/**
	 * 본 컨텍스트가 속한 {@link StateChart}를 반환한다.
	 *
	 * @return	소속된 {@link StateChart}. 아직 등록되지 않은 경우 {@code null}.
	 */
	public @Nullable StateChart<C> getStateChart();

	/**
	 * 본 컨텍스트가 속할 {@link StateChart}를 등록한다.
	 * <p>
	 * 본 메소드는 {@link StateChart} 생성자에서 컨텍스트당 정확히 한 번만 호출되는
	 * 프레임워크 내부용 콜백이다. 외부 코드에서 직접 호출하지 않는다.
	 * <p>
	 * <b>구현 계약</b>: 본 메소드가 이미 한 번 성공적으로 호출된 컨텍스트에 대해
	 * 다시 호출되는 경우, 구현체는 반드시 {@link IllegalStateException}을 던져
	 * 재설정을 거부해야 한다. 이는 {@link StateChart}와의 양방향 참조 일관성을
	 * 유지하기 위함이다.
	 *
	 * @param machine	소속될 {@link StateChart}
	 * @throws IllegalStateException	이미 다른 {@link StateChart}가 등록된 경우
	 */
	public void setStateChart(@NotNull StateChart<C> machine);
}
