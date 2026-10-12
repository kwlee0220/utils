package utils.statechart;

import java.util.function.BiConsumer;

/**
 * 상태 전이가 일어날 때 수행할 동작.
 * <p>
 * {@link BiConsumer}{@code <C, Signal>}에 이름을 붙인 것으로, 전이를 유발한 신호와 상태 컨텍스트를
 * 받아 도메인 부수 작업(컨텍스트 갱신, 외부 호출 등)을 수행한다.
 * {@link Transitions#create(String, TransitionAction)}를 통해 전이에 등록한다.
 * <p>
 * <b>실행 문맥</b>: 본 동작은 {@link StateChart}의 lock을 보유한 상태에서, 현재 상태의
 * {@code exit()}와 목표 상태의 {@code enter()} <b>사이</b>에 호출된다. 따라서 오래 걸리는 작업을
 * 넣으면 그동안 차트의 신호 처리 전체가 지연된다. 동작이 예외를 던지면 차트는 실패 상태로 전이되며
 * 예외는 호출자에게 전파되지 않는다. 자세한 계약은
 * {@link Transition#execute(StateContext, Signal)}를 참조한다.
 * <p>
 * self-transition({@link Transitions#stay()})에서는 본 동작이 <b>호출되지 않는다</b>는 점에 주의한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface TransitionAction<C extends StateContext<C>> extends BiConsumer<C, Signal> {

}
