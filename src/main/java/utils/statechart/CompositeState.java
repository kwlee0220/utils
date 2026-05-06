package utils.statechart;

import org.jetbrains.annotations.Nullable;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CompositeState<C extends StateContext<C>> extends State<C> {
	public State<C> getInitialState();
	
	/**
	 * 현재 진입 중인 하위 상태를 반환한다.
	 * <p>
	 * 아직 composite 상태에 진입하지 않았거나 이미 진출한 경우에는
	 * {@code null}을 반환할 수 있다.
	 *
	 * @return 현재 하위 상태. 없으면 {@code null}.
	 */
	public @Nullable State<C> getCurrentState();
}
