package utils.statechart;

/**
 * 실패로 끝나는 종료 상태.
 * <p>
 * (2026-10-31 이전 이름은 {@code ExceptionState}였다. 예외 객체가 아니라 "실패로 끝나는 상태"라는 뜻이 드러나도록
 * 이름을 바꿨다.)
 * <p>
 * {@link TerminalState}에 실패 원인 하나를 얹은 것으로, 진출도 신호 처리도 불가능한 점은 동일하다.
 * 차이는 차트의 종료 방식이다. 최종 상태로 등록된 {@code FailureState}에 도달하면
 * {@link StateChart}는 {@code complete()} 대신 {@code fail(getFailureCause())}를 호출하여
 * 차트를 실패로 종료시킨다.
 * <p>
 * <b>실패 원인은 이 상태로 들어오는 전이가 채워야 한다.</b> 차트는 전이 액션을 실행한 뒤
 * 목표 상태의 {@code enter()}를 호출하고, 그 다음에 {@code getFailureCause()}를 읽는다.
 * {@link Transitions#fail(String, java.util.function.Function)}으로 전이를 만들면 신호로부터 원인을 얻어 이 상태에 설정해 준다.
 * <pre>{@code
 * Transitions.fail("/Error", signal -> ((ErrorSignal)signal).getCause());
 * }</pre>
 * 직접 만든 전이 액션에서 {@link #setFailureCause(Throwable)}를 호출해도 된다. 채우지 않으면 차트는
 * "원인이 설정되지 않았다"는 {@link IllegalStateException}(상태 경로 포함)으로 실패하며, 실제 원인은 남지 않는다.
 * 원인을 상태가 아닌 다른 곳(예: 컨텍스트)에 보관하는 경우는 {@link #getFailureCause()}를 재정의하여 그 값을 돌려준다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FailureState<C extends StateContext<C>> extends TerminalState<C> {
	/** 차트를 실패시킬 원인 예외. 이 상태로 들어오는 전이의 액션이 채운다. */
	private Throwable m_failureCause;

	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 오류 종료 상태를 생성한다.
	 * <p>
	 * 실패 원인은 설정되지 않은 상태로 시작하므로, 이 상태로 들어오는 전이를
	 * {@link Transitions#fail(String, java.util.function.Function)}으로 만들거나 전이 액션에서
	 * {@link #setFailureCause(Throwable)}를 호출해야 한다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	public FailureState(String path, C context) {
		super(path, context);
	}

	/**
	 * 차트를 실패시킬 원인 예외를 반환한다.
	 * <p>
	 * 이 상태에 도달했을 때 {@link StateChart}가 호출하여 {@code fail(cause)}의 인자로 사용한다.
	 * {@code null}이면 차트는 원인이 설정되지 않았다는 {@link IllegalStateException}으로 실패한다.
	 * 원인을 다른 곳에 보관하는 하위 클래스는 본 메소드를 재정의한다.
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
