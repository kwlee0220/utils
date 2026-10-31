package utils.statechart;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 신호 하나를 처리한 결과.
 * <p>
 * {@link StateChart#processSignal(Signal)}과 {@link StateChart#postSignal(Signal)}이 반환한다.
 * {@link StateChart#handleSignal(Signal)}의 반환값({@link Optional#empty()})으로는 구분할 수 없던
 * "차트가 실행 중이 아님", "처리할 전이 없음", "나중에 처리하도록 큐에 넣음"을 구분한다.
 *
 * @param <C>	상태 컨텍스트 타입
 * @param status		처리 결과 종류.
 * @param transition	선택된 전이. {@link Status#TRANSITIONED} 또는 {@link Status#FAILED}일 때만 {@code null}이 아니다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public record SignalResult<C extends StateContext<C>>(Status status, @Nullable Transition<C> transition) {
	/** 신호 처리 결과 종류. */
	public enum Status {
		/**
		 * 진입해 있는 상태가 전이를 선택했고, 차트가 그 전이를 예외 없이 수행했다.
		 * 내부 전이({@link Transitions#internal(TransitionAction)}, {@link Transitions#stay()})를 선택한
		 * 경우도 포함한다. 최종 상태에 도달해 차트가 종료된 경우({@link FailureState}로 실패한 경우 포함)와,
		 * 콜백 안에서 차트가 종료되어 전이가 중간에 멈춘 경우도 이 값이다.
		 */
		TRANSITIONED,
		/**
		 * 진입해 있는 상태가 전이를 선택했지만, 전이를 수행하는 중({@code exit()}, 전이 액션, {@code enter()})
		 * 예외가 발생해 차트가 실패했다. 실패 원인은 차트의 결과({@code poll()}, {@code waitForFinished()})로 얻는다.
		 * (2026-10-31 이전에는 이 경우도 {@link #TRANSITIONED}였다.)
		 */
		FAILED,
		/** 차트는 실행 중이었지만 진입해 있는 어떤 상태도 전이를 선택하지 않았다. */
		UNHANDLED,
		/**
		 * 처리할 때 차트가 실행 중이 아니어서(시작 전, 취소 중, 종료 후) 신호를 버렸다. 상태가 전이를 고르는 중
		 * ({@code selectTransition()} 안에서) 차트 종료를 요청해 선택한 전이를 수행하지 않은 경우도 이 값이다.
		 */
		IGNORED,
		/**
		 * 같은 스레드가 다른 신호를 처리하는 중(상태 콜백이나 전이 작업 안)에 보낸 신호라서, 진행 중인 처리가
		 * 끝난 뒤 처리하도록 큐에 넣었다. 차트 lock을 보유한 스레드가 보냈는데 다른 스레드가 신호를 처리하는
		 * 중인 경우도 기다리면 교착되므로 이 값이다. {@link StateChart#processSignal(Signal)}만 이 값을 반환한다.
		 */
		QUEUED,
	}

	public SignalResult {
		Preconditions.checkNotNullArgument(status, "status");
		boolean selected = (status == Status.TRANSITIONED || status == Status.FAILED);
		Preconditions.checkArgument(selected == (transition != null),
									"transition must be given only for TRANSITIONED or FAILED: status=%s", status);
	}

	static <C extends StateContext<C>> SignalResult<C> transitioned(Transition<C> transition) {
		return new SignalResult<>(Status.TRANSITIONED, transition);
	}

	static <C extends StateContext<C>> SignalResult<C> failed(Transition<C> transition) {
		return new SignalResult<>(Status.FAILED, transition);
	}

	static <C extends StateContext<C>> SignalResult<C> of(Status status) {
		return new SignalResult<>(status, null);
	}

	/**
	 * 선택된 전이를 반환한다.
	 *
	 * @return	{@link Status#TRANSITIONED}이나 {@link Status#FAILED}이면 선택된 전이, 그 외에는 {@link Optional#empty()}.
	 */
	public Optional<Transition<C>> getTransition() {
		return Optional.ofNullable(transition);
	}

	/**
	 * 전이가 예외 없이 수행되었는지 여부를 반환한다.
	 *
	 * @return	{@link Status#TRANSITIONED}이면 {@code true}.
	 */
	public boolean isTransitioned() {
		return status == Status.TRANSITIONED;
	}
}
