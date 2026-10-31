package utils.statechart;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * 한 번 진입하면 빠져나올 수 없는 종료 상태의 기반 클래스.
 * <p>
 * (2026-10-31 이전 이름은 {@code SinkState}였다. 등록하면 자동으로 최종 상태가 되어, 차트를 끝내는 상태라는
 * 역할이 이름에 드러나도록 바꿨다.)
 * <p>
 * {@link #exit()}와 {@link #selectTransition(Signal)}, 전이 등록({@link #on(Signal, Transition)} 등)이 모두
 * {@link UnsupportedOperationException}을 던지도록 막아, 이 상태에서 다른 상태로 이동하는 경로가 존재하지 않음을 타입 차원에서 보장한다.
 * 진입 시 자원 정리 등 마무리 작업이 필요하면 {@link #enter()}를 재정의한다.
 * <p>
 * <b>{@link StateChart#addState(State)}로 등록하면 자동으로 최종 상태가 된다.</b>
 * {@link StateChart#addFinalState(String)}를 따로 호출하지 않아도 된다. 이 상태에 도달하는 순간 차트가
 * {@code complete()}되어 더 이상 신호가 전달되지 않는다. (2026-10-31 이전에는 {@code addFinalState()}를
 * 직접 호출해야 했고, 빠뜨리면 {@link StateChart#start()}가 거부했다.)
 * 실패로 끝나야 하는 종료 상태는 본 클래스 대신 {@link FailureState}를 사용한다.
 *
 * @param <C>	상태 컨텍스트 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TerminalState<C extends StateContext<C>> extends AbstractState<C> {
	/**
	 * 주어진 위치 경로와 상태 컨텍스트로 종료 상태를 생성한다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태 컨텍스트. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	public TerminalState(String path, C context) {
		super(path, context);
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태에서는 진출할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public void exit() {
		throw new UnsupportedOperationException("TerminalState cannot be exited");
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 어떤 신호도 처리하지 않는다.
	 * <p>
	 * 정상적인 사용에서는 호출되지 않는다. 이 상태는 등록할 때 자동으로 최종 상태가 되고, 도달하면 차트가
	 * 종료되어 이후 신호가 {@link StateChart#handleSignal(Signal)}에서 무시되기 때문이다.
	 *
	 * @param signal	처리할 신호. (사용되지 않음)
	 * @return	반환하지 않는다.
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public Optional<Transition<C>> selectTransition(Signal signal) {
		throw new UnsupportedOperationException("TerminalState does not handle any signal");
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 신호를 처리하지 않으므로 전이를 등록할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public AbstractState<C> on(Signal signal, Transition<C> transition) {
		throw new UnsupportedOperationException("TerminalState cannot have transitions: " + getPath());
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 신호를 처리하지 않으므로 전이를 등록할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public <S extends Signal> AbstractState<C> on(Class<S> signalType, Transition<C> transition) {
		throw new UnsupportedOperationException("TerminalState cannot have transitions: " + getPath());
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 신호를 처리하지 않으므로 전이를 등록할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public <S extends Signal> AbstractState<C> on(Class<S> signalType, Predicate<? super S> guard,
												Transition<C> transition) {
		throw new UnsupportedOperationException("TerminalState cannot have transitions: " + getPath());
	}

	/**
	 * 항상 {@link UnsupportedOperationException}을 던진다. 종료 상태는 신호를 처리하지 않으므로 전이를 등록할 수 없다.
	 *
	 * @throws UnsupportedOperationException	항상.
	 */
	@Override
	public <S extends Signal> AbstractState<C> on(Class<S> signalType,
												Function<? super S, ? extends Transition<C>> selector) {
		throw new UnsupportedOperationException("TerminalState cannot have transitions: " + getPath());
	}
}