package utils.statechart;


/**
 * 바로 생성해서 쓸 수 있는 일반 상태.
 * <p>
 * 신호별 전이를 {@link #on(Signal, Transition)} 등으로 등록하기만 하는 상태는 하위 클래스를 만들 필요 없이
 * 이 클래스로 만든다. {@code enter()}/{@code exit()}는 아무 일도 하지 않는다. 진입·진출 시 작업이 필요하면
 * {@link AbstractState}를 상속하거나 이 클래스를 상속해 재정의한다.
 * <pre>{@code
 * chart.addState(new DefaultState<>("/Idle", ctx)
 *                     .on(JobSignal.START, Transitions.to("/Busy"))
 *                     .on(Shutdown.class, Transitions.to("/Done")));
 * }</pre>
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultState<C extends StateContext<C>> extends AbstractState<C> {
	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 상태를 생성한다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	public DefaultState(String path, C context) {
		super(path, context);
	}
}
