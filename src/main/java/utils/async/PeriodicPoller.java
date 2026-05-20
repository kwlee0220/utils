package utils.async;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import utils.Preconditions;
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
 * Builder를 통해 polling 간격({@link Builder#interval(Duration) interval} 또는
 * {@link Builder#delay(Duration) delay}), 제한 시간({@link Builder#timeout(Duration) timeout}),
 * 마감 시각({@link Builder#due(Instant) due}), 초기화 작업({@link Builder#initializer initializer}),
 * 이름({@link Builder#name(String) name}) 등을 설정할 수 있다.
 * <p>
 * 사용 예:
 * <pre>{@code
 * PeriodicPoller<Connection> poller = PeriodicPoller.poll(this::tryConnect)
 *         .interval(Duration.ofSeconds(2))
 *         .timeout(Duration.ofMinutes(1))
 *         .build();
 * poller.start();
 * }</pre>
 *
 * @param <T> Polling 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicPoller<T> extends AbstractPeriodicPoller<T> {
	private final CheckedRunnable m_initializer;
	private final CheckedSupplier<T> m_poller;

	private PeriodicPoller(Builder<T> builder) {
		super(builder.m_pollInterval, builder.m_cumulativeInterval);
		
		m_initializer = builder.m_initializer;
		
		StringBuilder nameBuilder = new StringBuilder(builder.m_name);
		if ( builder.m_cumulativeInterval ) {
			nameBuilder.append(String.format("(interval=%s", builder.m_pollInterval));
		}
		else {
			nameBuilder.append(String.format("(delay=%s", builder.m_pollInterval));
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
	protected FOption<T> tryPoll() throws InterruptedException, CancellationException, ExecutionException {
		try {
			T result = m_poller.get();
			return FOption.ofNullable(result);
		}
		catch ( InterruptedException | CancellationException | ExecutionException e ) {
			throw e;
		}
		catch ( Exception e ) {
			throw new ExecutionException("polling failed", e);
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
		return new Builder<>(poller);
	}
	
	/**
	 * 주어진 조건이 {@code true}가 될 때까지 polling을 반복하는 {@link Builder}를 생성한다.
	 * <p>
	 * {@code condition}이 {@code false}를 반환하는 동안에는 polling이 계속되고,
	 * {@code true}를 반환하면 polling이 종료되며 결과로 {@link Boolean#TRUE}가 반환된다.
	 * 제한 시간({@link Builder#timeout(Duration)} / {@link Builder#due(Instant)})이 설정된 경우
	 * 그 시각까지 조건이 {@code true}가 되지 못하면 {@link java.util.concurrent.TimeoutException}이
	 * 발생한다.
	 *
	 * @param condition 종료 조건을 반환하는 함수. 검사 중 예외가 발생하면 polling이 중단되고
	 *                  최종 상태는 {@link AsyncState#FAILED}가 된다.
	 * @return 새 빌더.
	 */
	public static Builder<Boolean> pollUntil(CheckedSupplier<Boolean> condition) {
		return poll(() -> condition.get() ? Boolean.TRUE : null);
	}

	/**
	 * 주어진 조건이 {@code true}인 동안 polling을 반복하는 {@link Builder}를 생성한다.
	 * <p>
	 * {@code condition}이 {@code true}를 반환하는 동안에는 polling이 계속되고,
	 * {@code false}를 반환하면 polling이 종료되며 결과로 {@link Boolean#TRUE}가 반환된다.
	 * 제한 시간({@link Builder#timeout(Duration)} / {@link Builder#due(Instant)})이 설정된 경우
	 * 그 시각까지 조건이 {@code false}가 되지 못하면 {@link java.util.concurrent.TimeoutException}이
	 * 발생한다.
	 *
	 * @param condition 지속 조건을 반환하는 함수. 검사 중 예외가 발생하면 polling이 중단되고
	 *                  최종 상태는 {@link AsyncState#FAILED}가 된다.
	 * @return 새 빌더.
	 */
	public static Builder<Boolean> pollWhile(CheckedSupplier<Boolean> condition) {
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
		private static final boolean DEFAULT_CUMULATIVE_INTERVAL = true;

		private final CheckedSupplier<T> m_poller;
		private CheckedRunnable m_initializer = null;
		private String m_name = "poller";
		private Duration m_pollInterval = DEFAULT_POLL_INTERVAL;
		private Duration m_timeout = null;
		private Instant m_due = null;
		private boolean m_cumulativeInterval = DEFAULT_CUMULATIVE_INTERVAL;

		private Builder(CheckedSupplier<T> poller) {
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
		 * Polling 간격을 누적(cumulative) 모드로 설정한다.
		 * <p>
		 * Loop 시작 시각을 기준으로 매 iteration의 시작 시각을 누적 계산한다. 한 iteration의 수행 시간이
		 * {@code interval}보다 길어지면 다음 iteration은 대기 없이 곧바로 시작된다(catch-up).
		 * <p>
		 * 이 메소드와 {@link #delay(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 * 미설정 시 default 값 1초로 누적 모드가 적용된다.
		 *
		 * @param interval Polling 시간 간격 (양수).
		 *                 {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> interval(Duration interval) {
			Preconditions.checkNotNullArgument(interval, "interval is null");
			Preconditions.checkArgument(interval.isPositive(), "interval is not positive: %s", interval);

			m_pollInterval = interval;
			m_cumulativeInterval = true;
			return this;
		}

		/**
		 * Polling 간격을 비누적(non-cumulative) 모드로 설정한다.
		 * <p>
		 * {@link #interval(Duration)}과 달리 이전 iteration의 실행 시간을 고려하지 않고,
		 * 매 iteration 완료 후 고정된 {@code delay} 만큼 대기한 뒤 다음 iteration을 시작한다.
		 * <p>
		 * 이 메소드와 {@link #interval(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 *
		 * @param delay 각 iteration 완료 후 다음 iteration 시작까지의 대기 시간 (양수).
		 *              {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder<T> delay(Duration delay) {
			Preconditions.checkNotNullArgument(delay, "delay is null");
			Preconditions.checkArgument(delay.isPositive(), "delay is not positive: %s", delay);

			m_pollInterval = delay;
			m_cumulativeInterval = false;
			return this;
		}

		/**
		 * Polling 제한 기간을 설정한다.
		 * <p>
		 * 빌더 생성 시점부터 측정한 경과 시간이 {@code timeout}을 초과하면
		 * {@link java.util.concurrent.TimeoutException}이 발생한다.
		 * {@link #due(Instant)}와 동시에 설정된 경우 이 값은 무시된다.
		 *
		 * @param timeout Polling 제한 기간 (양수). {@code null}이면 제한 없음.
		 * @return 이 빌더.
		 */
		public Builder<T> timeout(Duration timeout) {
			Preconditions.checkArgument(timeout == null || timeout.isPositive(),
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
		 * {@code due}가 이미 과거인 경우, 첫 iteration에서 즉시
		 * {@link java.util.concurrent.TimeoutException}이 발생한다.
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
