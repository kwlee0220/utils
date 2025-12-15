package utils.statechart;

import java.util.Optional;


/**
 * {@code StateChart}의 상태를 나타내는 인터페이스.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface State<C extends StateContext> {
	/**
	 * 상태의 위치 경로를 반환한다.
	 *
	 * @return	상태의 위치 경로
	 */
	public String getPath();
	
	/**
	 * 상태가 속한 상태문맥을 반환한다.
	 *
	 * @return 상태문맥
	 */
	public C getContext();
	
	/**
	 * 본 상태로 진입한다.
	 */
	public void enter();
	
	/**
	 * 본 상태에서 진출한다.
	 */
	public void exit();
	
	/**
	 * 주어진 신호를 처리할 전이를 선택한다.
	 * <p>
	 * 신호를 처리할 전이가 없으면 {@link Optional#empty()}을 반환한다.
	 *
	 * @param signal	처리할 신호
	 * @return	신호를 처리할 전이
	 */
	public Optional<Transition<C>> selectTransition(Signal signal);
}
