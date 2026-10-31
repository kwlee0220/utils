package utils.statechart;

import java.util.function.BiConsumer;

/**
 * 상태 전이가 일어날 때 수행할 동작.
 * <p>
 * {@link BiConsumer}{@code <C, Signal>}에 이름을 붙인 것으로, 전이를 유발한 신호와 상태 컨텍스트를
 * 받아 도메인 부수 작업(컨텍스트 갱신, 외부 호출 등)을 수행한다.
 * {@link Transitions#to(String, TransitionAction)}를 통해 전이에 등록한다.
 * <p>
 * <b>실행 문맥</b>: 본 동작은 {@link StateChart}의 lock을 보유한 상태에서 호출된다. 상태를 이동하는 전이
 * ({@link Transitions#to(String, TransitionAction)})에서는 현재 상태의 {@code exit()}와 목표 상태의
 * {@code enter()} <b>사이</b>에, 내부 전이({@link Transitions#internal(TransitionAction)})에서는 상태의
 * {@code exit()}/{@code enter()} 없이 호출된다. 오래 걸리는 작업을 넣으면 그동안 차트의 신호 처리 전체가
 * 지연된다. 동작이 예외를 던지면 차트는 실패 상태로 전이되며 예외는 호출자에게 전파되지 않는다.
 * 자세한 계약은 {@link Transition#execute(StateContext, Signal)}를 참조한다.
 * <p>
 * 액션이 없는 전이는 {@link Transitions#to(String)}(상태 이동만)과 {@link Transitions#stay()}(아무것도
 * 하지 않고 머묾)로 만든다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface TransitionAction<C extends StateContext<C>> extends BiConsumer<C, Signal> {

}
