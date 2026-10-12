package utils.statechart;

/**
 * 오류로 인해 도달하는 종료 상태.
 * <p>
 * {@link SinkState}에 실패 원인 하나를 얹은 것으로, 진출도 신호 처리도 불가능한 점은 동일하다.
 * 차이는 차트의 종료 방식이다. 최종 상태로 등록된 {@code ExceptionState}에 도달하면
 * {@link StateChart}는 {@code complete()} 대신 {@code fail(getFailureCause())}를 호출하여
 * 차트를 실패로 종료시킨다.
 * <p>
 * <b>실패 원인은 이 상태로 들어오는 전이의 액션이 채워야 한다.</b> 차트는 전이 액션을 실행한 뒤
 * 목표 상태의 {@code enter()}를 호출하고, 그 다음에 {@code getFailureCause()}를 읽는다. 따라서
 * {@link Transitions#create(String, TransitionAction)}의 액션에서
 * {@link #setFailureCause(Throwable)}를 호출해 두어야 한다. 채우지 않으면 원인이 {@code null}인 채로
 * 차트가 실패하여 원인 추적이 불가능해진다.
 * <pre>{@code
 * Transitions.create("/Error", (ctx, signal) -> {
 *     ErrorState<Ctx> error = (ErrorState<Ctx>)ctx.getStateChart().getState("/Error");
 *     error.setFailureCause(((ErrorSignal)signal).getCause());
 * });
 * }</pre>
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ExceptionState<C extends StateContext<C>> extends SinkState<C> {
	/** 차트를 실패시킬 원인 예외. 이 상태로 들어오는 전이의 액션이 채운다. */
	private Throwable m_failureCause;

	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 오류 종료 상태를 생성한다.
	 * <p>
	 * 실패 원인은 설정되지 않은 상태로 시작하므로, 이 상태로 들어오는 전이에서
	 * {@link #setFailureCause(Throwable)}를 호출해야 한다.
	 *
	 * @param name		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code name} 또는 {@code context}가 {@code null}인 경우.
	 */
	public ExceptionState(String name, C context) {
		super(name, context);
	}

	/**
	 * 차트를 실패시킬 원인 예외를 반환한다.
	 * <p>
	 * 이 상태에 도달했을 때 {@link StateChart}가 호출하여 {@code fail(cause)}의 인자로 사용한다.
	 *
	 * @return	설정된 원인 예외. {@link #setFailureCause(Throwable)}가 호출된 적이 없으면 {@code null}.
	 */
	public Throwable getFailureCause() {
		return m_failureCause;
	}

	/**
	 * 차트를 실패시킬 원인 예외를 설정한다.
	 * <p>
	 * 이 상태로 들어오는 전이의 액션에서 호출해야 한다. 차트가 이 값을 읽는 시점보다 늦게 설정하면
	 * 반영되지 않는다.
	 *
	 * @param cause	원인 예외.
	 */
	public void setFailureCause(Throwable cause) {
		m_failureCause = cause;
	}
}
