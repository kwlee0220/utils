package utils.async;


/**
 * {@link PeriodicLoopExecution}(및 {@link AbstractPeriodicPoller})이 iteration 사이의 주기를 계산하는 방식.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum IntervalMode {
	/**
	 * 고정 주기(fixed-rate, 누적) 모드.
	 * <p>
	 * Loop 시작 시점을 기준으로 {@code interval × n}마다 iteration을 시작한다.
	 * 한 iteration이 interval보다 오래 걸리면 밀린 만큼 다음 iteration을 대기 없이 곧바로 시작한다(catch-up).
	 * 일정한 박자를 유지해야 하는 주기 작업(샘플링, 주기적 보고 등)에 적합하다.
	 */
	FIXED_RATE,

	/**
	 * 고정 지연(fixed-delay, 비누적) 모드.
	 * <p>
	 * 매 iteration이 끝난 시점부터 interval만큼 대기한 뒤 다음 iteration을 시작한다.
	 * 한 iteration이 interval보다 오래 걸려도 다음 iteration 전에 항상 interval만큼 대기하므로,
	 * 응답이 느린 원격 서버 조회나 재연결 시도처럼 연달아 수행되면 안 되는 작업에 적합하다.
	 */
	FIXED_DELAY;

	/**
	 * 기존의 {@code cumulativeInterval} 플래그에 해당하는 모드를 반환한다.
	 *
	 * @param cumulativeInterval	{@code true}이면 {@link #FIXED_RATE}, {@code false}이면 {@link #FIXED_DELAY}.
	 * @return	대응하는 {@link IntervalMode}.
	 */
	public static IntervalMode fromCumulative(boolean cumulativeInterval) {
		return cumulativeInterval ? FIXED_RATE : FIXED_DELAY;
	}
}
