package utils.async;

import java.time.Duration;
import java.time.Instant;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.CheckedConsumer;
import utils.func.CheckedFunction;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;
import utils.func.FOption;

/**
 * 일정 주기로 함수형 supplier를 호출하여 목표 상태가 관찰될 때까지 polling을 수행하는
 * {@link AbstractPeriodicPoller}의 concrete 구현체.
 * <p>
 * 인스턴스는 정적 팩토리 메소드와 {@link Builder}를 통해서만 생성한다.
 * <ul>
 *  <li>{@link #poll(CheckedSupplier)} — supplier가 non-null 값을 반환하면 그 값을 결과로 polling이 종료된다.
 *  <li>{@link #pollUntil(CheckedSupplier)} — 조건이 {@code true}가 될 때까지 반복한다.
 *  <li>{@link #pollWhile(CheckedSupplier)} — 조건이 {@code true}인 동안 반복한다.
 * </ul>
 * Builder를 통해 polling 간격({@link Builder#fixedRate(Duration) fixedRate} 또는
 * {@link Builder#fixedDelay(Duration) fixedDelay}), 제한 시간({@link Builder#timeout(Duration) timeout}),
 * 마감 시각({@link Builder#due(Instant) due}), 초기화 작업({@link Builder#initializer initializer}),
 * 채택되지 못한 결과의 정리({@link Builder#onDiscarded onDiscarded}), 종료 시 정리 작업
 * ({@link Builder#cleanup cleanup}), 이름({@link Builder#name(String) name}) 등을 설정할 수 있다.
 * <p>
 * 사용 예:
 * <pre>{@code
 * PeriodicPoller<Connection> poller = PeriodicPoller.poll(this::tryConnect)
 *         .fixedDelay(Duration.ofSeconds(2))
 *         .timeout(Duration.ofMinutes(1))
 *         .onDiscarded(Connection::close)   // 연결은 됐지만 결과로 채택되지 못한 경우 닫는다
 *         .build();
 * poller.start();
 * }</pre>
 * supplier가 연결 객체처럼 자원을 소유한 값을 반환하는 경우에는 {@link Builder#onDiscarded onDiscarded}를
 * 지정해야 한다. 그 값이 외부 종료 등으로 최종 결과로 채택되지 못하면 아무도 닫지 않게 되기 때문이다.
 * <p>
 * Polling 함수가 시도 번호나 남은 제한 시간을 알아야 하는 경우에는 {@link PollContext}를 인자로 받는
 * {@link #poll(CheckedFunction)}을 사용한다. 예를 들어 원격 조회의 개별 요청 timeout에 남은 시간을 넘길 수 있다.
 * <pre>{@code
 * PeriodicPoller<State> poller = PeriodicPoller.poll(ctx -> fetchState(ctx.remainingTime().getOrElse(DEFAULT)))
 *         .timeout(Duration.ofMinutes(1))
 *         .build();
 * }</pre>
 *
 * @param <T> Polling 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicPoller<T> extends AbstractPeriodicPoller<T> {
	/**
	 * Polling 함수({@link PeriodicPoller#poll(CheckedFunction)})에 전달되는 polling 문맥.
	 * <p>
	 * Polling 함수가 호출되는 동안(워커 스레드)에만 의미가 있다.
	 */
	public interface PollContext {
		/**
		 * 현재 polling 시도 번호를 반환한다. 첫 시도는 {@code 0}이며 매 시도마다 1씩 증가한다.
		 *
		 * @return	polling 시도 번호.
		 */
		public long attempt();

		/**
		 * Polling 제한 시간까지 남은 시간을 반환한다.
		 * <p>
		 * 제한 시간은 진행 중인 polling 함수 호출을 중단시키지 않으므로, 원격 호출 등 오래 걸릴 수 있는 작업의
		 * 개별 timeout으로 이 값을 사용하는 것이 좋다. 자세한 의미는 {@link PeriodicLoopExecution#getRemainingTime()} 참조.
		 *
		 * @return	남은 시간. 제한 시간({@link Builder#timeout}/{@link Builder#due})이 없으면 {@link FOption#empty()}.
		 */
		public FOption<Duration> remainingTime();
	}

	private final @Nullable CheckedRunnable m_initializer;
	private final CheckedFunction<? super PollContext, ? extends T> m_poller;
	private final PollContext m_context = new PollContext() {
		@Override
		public long attempt() {
			return getPollAttempt();
		}

		@Override
		public FOption<Duration> remainingTime() {
			return getRemainingTime();
		}
	};
	private final @Nullable CheckedConsumer<? super T> m_onDiscarded;
	private final @Nullable CheckedConsumer<? super AsyncResult<T>> m_cleanup;

	private PeriodicPoller(Builder<T> builder) {
		super(builder.m_pollInterval, builder.m_intervalMode);
		
		m_initializer = builder.m_initializer;
		m_onDiscarded = builder.m_onDiscarded;
		m_cleanup = builder.m_cleanup;
		
		StringBuilder nameBuilder = new StringBuilder(builder.m_name);
		if ( builder.m_intervalMode == IntervalMode.FIXED_RATE ) {
			nameBuilder.append(String.format("(fixedRate=%s", builder.m_pollInterval));
		}
		else {
			nameBuilder.append(String.format("(fixedDelay=%s", builder.m_pollInterval));
		}

		m_poller = builder.m_poller;
		if ( builder.m_due != null ) {
			this.setDue(builder.m_due);
			nameBuilder.append(String.format(", due=%s", builder.m_due));
		}
		else if ( builder.m_timeout != null ) {
			this.setTimeout(builder.m_timeout);
			nameBuilder.append(String.format(", timeout=%s", builder.m_timeout));
		}
		
		String threadName = nameBuilder.append(")").toString();
		this.setThreadNamePrefix(threadName);
	}
	
	@Override
	protected void initializePoller() throws Exception {
		if ( m_initializer != null ) {
			m_initializer.run();
		}
	}

	@Override
	protected FOption<T> tryPoll() throws Exception {
		// supplier의 예외는 그대로 전파한다. 상위 클래스가 취소/실패로 처리하며, 실패 원인은 그 예외가 된다.
		return FOption.ofNullable(m_poller.apply(m_context));
	}

	@Override
	protected void onTargetDiscarded(T value) throws Exception {
		if ( m_onDiscarded != null ) {
			m_onDiscarded.accept(value);
		}
	}

	@Override
	protected void cleanupPoller(AsyncResult<T> outcome) throws Exception {
		if ( m_cleanup != null ) {
			m_cleanup.accept(outcome);
		}
	}

	/**
	 * 주어진 상태 함수로 polling을 수행할 {@link Builder}를 생성한다.
	 *
	 * @param <T>    상태 타입.
	 * @param poller 상태를 반환하는 함수.
	 *               반환값이 {@code null}이면 polling이 계속되고, non-null이면 종료된다.
	 *               {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static <T> Builder<T> poll(CheckedSupplier<T> poller) {
		Preconditions.checkNotNullArgument(poller, "poller is null");
		return new Builder<>(ctx -> poller.get());
	}

	/**
	 * Polling 문맥({@link PollContext})을 인자로 받는 상태 함수로 polling을 수행할 {@link Builder}를 생성한다.
	 * <p>
	 * {@link #poll(CheckedSupplier)}와 같지만, 상태 함수가 현재 시도 번호({@link PollContext#attempt()})와
	 * 남은 제한 시간({@link PollContext#remainingTime()})을 사용할 수 있다.
	 *
	 * @param <T>    상태 타입.
	 * @param poller polling 문맥을 받아 상태를 반환하는 함수.
	 *               반환값이 {@code null}이면 polling이 계속되고, non-null이면 종료된다.
	 *               {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static <T> Builder<T> poll(CheckedFunction<? super PollContext, ? extends T> poller) {
		return new Builder<>(poller);
	}
	
	/**
	 * 주어진 조건이 {@code true}가 될 때까지 polling을 반복하는 {@link Builder}를 생성한다.
	 * <p>
	 * {@code condition}이 {@code false}를 반환하는 동안에는 polling이 계속되고,
	 * {@code true}를 반환하면 polling이 종료된다.
	 * <p>
	 * 결과 값은 항상 {@link Boolean#TRUE}이며 의미가 없다. 조건이 충족되었는지는 결과 값이 아니라
	 * 실행이 {@link AsyncState#COMPLETED}로 끝났는지로 판단한다(제한 시간 초과는 {@code FAILED}).
	 * 제한 시간({@link Builder#timeout(Duration)} / {@link Builder#due(Instant)})이 설정된 경우
	 * 그 시각까지 조건이 {@code true}가 되지 못하면 {@link java.util.concurrent.TimeoutException}이
	 * 발생한다.
	 *
	 * @param condition 종료 조건을 반환하는 함수. 검사 중 예외가 발생하면 polling이 중단되고
	 *                  최종 상태는 {@link AsyncState#FAILED}가 된다.
	 *                  {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static Builder<Boolean> pollUntil(CheckedSupplier<Boolean> condition) {
		Preconditions.checkNotNullArgument(condition, "condition is null");
		return poll(() -> condition.get() ? Boolean.TRUE : null);
	}

	/**
	 * 주어진 조건이 {@code true}인 동안 polling을 반복하는 {@link Builder}를 생성한다.
	 * <p>
	 * {@code condition}이 {@code true}를 반환하는 동안에는 polling이 계속되고,
	 * {@code false}를 반환하면 polling이 종료된다.
	 * <p>
	 * 결과 값은 항상 {@link Boolean#TRUE}이며 의미가 없다. 조건이 <b>{@code false}가 되어</b> 종료되었더라도
	 * 결과는 {@code TRUE}이므로 결과 값으로 조건을 해석하지 않도록 주의한다. 조건이 해제되었는지는
	 * 실행이 {@link AsyncState#COMPLETED}로 끝났는지로 판단한다(제한 시간 초과는 {@code FAILED}).
	 * 제한 시간({@link Builder#timeout(Duration)} / {@link Builder#due(Instant)})이 설정된 경우
	 * 그 시각까지 조건이 {@code false}가 되지 못하면 {@link java.util.concurrent.TimeoutException}이
	 * 발생한다.
	 *
	 * @param condition 지속 조건을 반환하는 함수. 검사 중 예외가 발생하면 polling이 중단되고
	 *                  최종 상태는 {@link AsyncState#FAILED}가 된다.
	 *                  {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static Builder<Boolean> pollWhile(CheckedSupplier<Boolean> condition) {
		Preconditions.checkNotNullArgument(condition, "condition is null");
		return poll(() -> condition.get() ? null : Boolean.TRUE);
	}
	
	/**
	 * {@link PeriodicPoller}의 빌더.
	 * <p>
	 * {@link #due(Instant)}와 {@link #timeout(Duration)}을 모두 설정한 경우 {@code due}가 우선
	 * 적용되고 {@code timeout}은 무시된다.
	 *
	 * @param <T> Polling 대상 상태의 타입.
	 */
	public static class Builder<T> {
		private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
		private static final IntervalMode DEFAULT_INTERVAL_MODE = IntervalMode.FIXED_DELAY;

		private final CheckedFunction<? super PollContext, ? extends T> m_poller;
		private CheckedRunnable m_initializer = null;
		private CheckedConsumer<? super T> m_onDiscarded = null;
		private CheckedConsumer<? super AsyncResult<T>> m_cleanup = null;
		private String m_name = "poller";
		private Duration m_pollInterval = DEFAULT_POLL_INTERVAL;
		private Duration m_timeout = null;
		private Instant m_due = null;
		private IntervalMode m_intervalMode = DEFAULT_INTERVAL_MODE;

		private Builder(CheckedFunction<? super PollContext, ? extends T> poller) {
			Preconditions.checkNotNullArgument(poller, "poller is null");
			m_poller = poller;
		}

		/**
		 * 현재 설정으로 새 {@link PeriodicPoller}를 생성한다.
		 *
		 * @return 새로 생성된 {@link PeriodicPoller}.
		 */
		public PeriodicPoller<T> build() {
			return new PeriodicPoller<>(this);
		}
		
		/**
		 * 이 {@link PeriodicPoller}의 논리적 이름을 설정한다.
		 * <p>
		 * 설정된 이름은 스레드 이름 접두사 등에 사용된다. 미설정 시 기본값 {@code "poller"}가 사용된다.
		 *
		 * @param name 설정할 이름. {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> name(String name) {
			Preconditions.checkNotNullArgument(name, "name is null");
			
			m_name = name;
			return this;
		}
		
		/**
		 * Polling 시작 시 1회 호출될 초기화 작업을 설정한다.
		 * <p>
		 * 등록된 {@code initializer}는 {@link AbstractPeriodicPoller#initializePoller()} 시점에
		 * 실행된다. 초기화 중 발생한 예외는 polling 시작을 실패시키고 작업을
		 * {@link AsyncState#FAILED} 상태로 전이시킨다.
		 *
		 * @param initializer 초기화 작업.
		 *                    {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> initializer(CheckedRunnable initializer) {
			Preconditions.checkNotNullArgument(initializer, "initializer is null");

			m_initializer = initializer;
			return this;
		}

		/**
		 * Supplier가 반환한 값이 최종 결과로 채택되지 못했을 때 호출될 정리 작업을 설정한다.
		 * <p>
		 * Supplier가 연결 객체처럼 자원을 소유한 값을 반환하는 경우, 그 자원을 정리하기 위해 사용한다.
		 * 값을 얻었지만 그 사이 외부에서 실행이 종료(실패·취소)되어 결과로 채택되지 못한 경우 등에 호출되며,
		 * 자세한 호출 조건은 {@link AbstractPeriodicPoller#onTargetDiscarded(Object)} 참조.
		 * 정리 작업에서 발생한 예외는 경고 로그만 남기고 무시된다.
		 *
		 * @param onDiscarded	채택되지 못한 값을 받아 정리하는 작업.
		 * 						{@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> onDiscarded(CheckedConsumer<? super T> onDiscarded) {
			Preconditions.checkNotNullArgument(onDiscarded, "onDiscarded is null");

			m_onDiscarded = onDiscarded;
			return this;
		}

		/**
		 * Polling이 종료될 때 호출될 정리 작업을 설정한다.
		 * <p>
		 * {@code initializer}가 성공한 이후라면 종료 사유와 무관하게 1회 호출되며, 완료·실패·취소와 실패 원인을
		 * 담은 종료 결과({@link AsyncResult})를 받는다. 호출 시점은 실행이 최종 상태로 전이하기 전이다.
		 * 정리 작업에서 발생한 예외는 경고 로그만 남기고 무시되므로 polling 결과에 영향을 주지 않는다.
		 * 자세한 계약은 {@link AbstractPeriodicPoller#cleanupPoller(AsyncResult)} 참조.
		 *
		 * @param cleanup	종료 결과를 받아 정리하는 작업.
		 * 					{@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> cleanup(CheckedConsumer<? super AsyncResult<T>> cleanup) {
			Preconditions.checkNotNullArgument(cleanup, "cleanup is null");

			m_cleanup = cleanup;
			return this;
		}

		/**
		 * 고정 주기({@link IntervalMode#FIXED_RATE}) 모드로 polling 간격을 설정한다.
		 * <p>
		 * Polling 시작 시각을 기준으로 {@code rate × n}마다 polling을 시작한다. 한 번의 polling이 {@code rate}보다
		 * 오래 걸리면 밀린 만큼 다음 polling을 <b>대기 없이 연달아</b> 시작한다(catch-up). 일정한 박자가 필요한
		 * 경우에 사용하며, 응답이 느릴 수 있는 원격 조회나 재연결에는 {@link #fixedDelay(Duration)}가 적합하다.
		 * <p>
		 * 이 메소드와 {@link #fixedDelay(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 * 둘 다 설정하지 않으면 {@code fixedDelay(1초)}가 적용된다.
		 *
		 * @param rate Polling 주기 (양수).
		 *             {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> fixedRate(Duration rate) {
			Preconditions.checkNotNullArgument(rate, "rate is null");
			Preconditions.checkArgument(rate.compareTo(Duration.ZERO) > 0, "rate is not positive: %s", rate);

			m_pollInterval = rate;
			m_intervalMode = IntervalMode.FIXED_RATE;
			return this;
		}

		/**
		 * 고정 지연({@link IntervalMode#FIXED_DELAY}) 모드로 polling 간격을 설정한다.
		 * <p>
		 * 매 polling이 끝난 시각부터 {@code delay}만큼 대기한 뒤 다음 polling을 시작한다. 한 번의 polling이
		 * 오래 걸려도 다음 polling 전에 항상 {@code delay}만큼 대기하므로, 응답이 느린 원격 서버나 재연결 대상에
		 * 연달아 요청을 보내지 않는다.
		 * <p>
		 * 이 메소드와 {@link #fixedRate(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 * 둘 다 설정하지 않으면 기본값인 {@code fixedDelay(1초)}가 적용된다.
		 *
		 * @param delay 각 polling 완료 후 다음 polling 시작까지의 대기 시간 (양수).
		 *              {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> fixedDelay(Duration delay) {
			Preconditions.checkNotNullArgument(delay, "delay is null");
			Preconditions.checkArgument(delay.compareTo(Duration.ZERO) > 0, "delay is not positive: %s", delay);

			m_pollInterval = delay;
			m_intervalMode = IntervalMode.FIXED_DELAY;
			return this;
		}

		/**
		 * 고정 주기({@link IntervalMode#FIXED_RATE}) 모드로 polling 간격을 설정한다.
		 *
		 * @param interval Polling 주기 (양수).
		 * @return 이 빌더.
		 * @deprecated	밀린 경우 연달아 실행된다는 성질이 호출부에서 드러나도록 {@link #fixedRate(Duration)}를 사용한다.
		 */
		@Deprecated
		public Builder<T> interval(Duration interval) {
			return fixedRate(interval);
		}

		/**
		 * 고정 지연({@link IntervalMode#FIXED_DELAY}) 모드로 polling 간격을 설정한다.
		 *
		 * @param delay 각 polling 완료 후 다음 polling 시작까지의 대기 시간 (양수).
		 * @return 이 빌더.
		 * @deprecated	{@link #fixedRate(Duration)}와 짝을 이루는 {@link #fixedDelay(Duration)}를 사용한다.
		 */
		@Deprecated
		public Builder<T> delay(Duration delay) {
			return fixedDelay(delay);
		}

		/**
		 * Polling 제한 기간을 설정한다.
		 * <p>
		 * Polling이 <b>실제로 시작된 시점</b>부터 측정한 경과 시간이 {@code timeout}을 초과하면
		 * {@link java.util.concurrent.TimeoutException}이 발생한다. 빌더 생성이나 {@code start()} 호출 이후
		 * 실행이 시작되기까지 지연된 시간은 포함되지 않는다(절대 시각 기준이 필요하면 {@link #due(Instant)}를 사용한다).
		 * {@link #due(Instant)}와 동시에 설정된 경우 이 값은 무시된다.
		 *
		 * @param timeout Polling 제한 기간 (양수). {@code null}이면 제한 없음.
		 * @return 이 빌더.
		 */
		public Builder<T> timeout(Duration timeout) {
			Preconditions.checkArgument(timeout == null || timeout.compareTo(Duration.ZERO) > 0,
										"timeout is not positive: %s", timeout);

			m_timeout = timeout;
			return this;
		}

		/**
		 * Polling 제한 시각을 설정한다.
		 * <p>
		 * 이 시각에 도달할 때까지 목표 상태가 관찰되지 않으면
		 * {@link java.util.concurrent.TimeoutException}이 발생한다.
		 * {@link #timeout(Duration)}과 동시에 설정된 경우 이 값이 우선 적용된다.
		 * <p>
		 * 빌더 시점에는 시각의 과거/미래 여부를 검증하지 않는다. Polling 시작 시점에
		 * {@code due}가 이미 과거인 경우, 시작 단계(초기화)에서 곧바로
		 * {@link java.util.concurrent.TimeoutException}으로 실패한다. 이때는 {@link #initializer initializer}와
		 * supplier가 한 번도 호출되지 않으며, {@link #cleanup cleanup}도 호출되지 않는다.
		 *
		 * @param due Polling 제한 시각. {@code null}이면 제한 없음.
		 * @return 이 빌더.
		 */
		public Builder<T> due(Instant due) {
			m_due = due;
			return this;
		}
	}
}
