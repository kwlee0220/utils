package utils.statechart;

import java.util.Optional;


/**
 * 한 번 진입하면 빠져나올 수 없는 종료 상태의 기반 클래스.
 * <p>
 * {@link #exit()}와 {@link #selectTransition(Signal)}이 모두 {@link UnsupportedOperationException}을
 * 던지도록 막아, 이 상태에서 다른 상태로 이동하는 경로가 존재하지 않음을 타입 차원에서 보장한다.
 * 진입 시 자원 정리 등 마무리 작업이 필요하면 {@link #enter()}를 재정의한다.
 * <p>
 * <b>반드시 최종 상태로 등록해야 한다.</b> {@link StateChart#addFinalState(String)}로 등록해 두면
 * 이 상태에 도달하는 순간 차트가 {@code complete()}되어 더 이상 신호가 전달되지 않는다. 등록하지 않으면
 * 차트가 계속 실행 중인 채로 남아 다음 신호가 {@link #selectTransition(Signal)}에 도달하고,
 * 그 예외가 차트를 실패 상태로 뒤집는다. 실패로 끝나야 하는 종료 상태는 본 클래스 대신
 * {@link ExceptionState}를 사용한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SinkState<C extends StateContext<C>> extends AbstractState<C> {
	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 종료 상태를 생성한다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	public SinkState(String path, C context) {
		super(path, context);
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태에서는 진출할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public void exit() {
		throw new UnsupportedOperationException("SinkState cannot be exited");
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 어떤 신호도 처리하지 않는다.
	 * <p>
	 * 정상적인 사용에서는 호출되지 않는다. 이 상태에 도달하면 차트가 종료되어 이후 신호가
	 * {@link StateChart#handleSignal(Signal)}에서 무시되기 때문이다. 본 메소드가 실제로 호출되었다는 것은
	 * 이 상태가 최종 상태로 등록되지 않았다는 뜻이다.
	 *
	 * @param signal	처리할 신호. (사용되지 않음)
	 * @return	반환하지 않는다.
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		throw new UnsupportedOperationException("SinkState does not handle any signal");
	}
}