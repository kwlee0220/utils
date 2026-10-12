package utils.statechart;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;


/**
 * {@link StateChart}의 상태를 나타내는 인터페이스.
 * <p>
 * 상태는 자신의 위치 경로로 차트에 등록되고, 진입/진출 시점의 부수 작업({@link #enter()} /
 * {@link #exit()})과 신호에 대한 전이 선택({@link #selectTransition(Signal)})을 담당한다.
 * 직접 구현하기보다는 {@link AbstractState}를 상속하거나 {@link SingleOutState},
 * {@link SinkState}, {@link ExceptionState}, {@link DefaultCompositeState} 같은 기성 구현을
 * 사용하는 것이 일반적이다.
 *
 * @param <C>	상태 컨텍스트 타입. (CRTP self-bound)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface State<C extends StateContext<C>> {
	/**
	 * 상태의 위치 경로를 반환한다.
	 *
	 * @return	상태의 위치 경로
	 */
	public @NotNull String getPath();

	/**
	 * 상태가 속한 상태문맥을 반환한다.
	 *
	 * @return 상태문맥
	 */
	public @NotNull C getContext();

	/**
	 * 본 상태로 진입한다.
	 * <p>
	 * 기본 구현은 아무 일도 하지 않는다. 상태 진입 시 부수 작업이 필요한 구현체만 override한다.
	 */
	public default void enter() { }

	/**
	 * 본 상태에서 진출한다.
	 * <p>
	 * 기본 구현은 아무 일도 하지 않는다. 상태 진출 시 부수 작업이 필요한 구현체만 override한다.
	 */
	public default void exit() { }

	/**
	 * 주어진 신호를 처리할 전이를 선택한다.
	 * <p>
	 * 신호를 처리할 전이가 없으면 {@link Optional#empty()}을 반환한다.
	 * 본 메소드는 부수 효과 없이 전이 후보를 선택만 하며, 실제 전이 실행은 호출자가
	 * {@link Transition#execute(StateContext, Signal)}로 수행해야 한다.
	 *
	 * @param signal	처리할 신호
	 * @return	신호를 처리할 전이
	 */
	public Optional<Transition<C>> selectTransition(Signal signal);
}
