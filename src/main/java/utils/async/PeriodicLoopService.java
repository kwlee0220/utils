package utils.async;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.math.LongMath;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Service;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.func.CheckedRunnable;
import utils.func.Optionals;


/**
 * 주어진 작업을 지정된 주기로 반복 수행하는 Guava {@link Service} 구현체.
 * <p>
 * {@link AbstractExecutionThreadService}를 기반으로 하기 때문에 전용 쓰레드 하나가
 * 서비스 시작({@link #startAsync()})부터 중지({@link #stopAsync()})까지 loop을 수행한다.
 * 서비스가 시작되면 (초기 지연이 설정된 경우 그 시간만큼 대기한 뒤) 곧바로 첫 iteration이 수행되고,
 * 이후 설정된 주기마다 작업이 반복된다.
 * <p>
 * 인스턴스는 {@link Builder}를 통해서 생성한다.
 * <pre>{@code
 * PeriodicLoopService svc = PeriodicLoopService.builder(() -> collectStatistics())
 *                                              .name("stat-collector")
 *                                              .fixedRate(Duration.ofSeconds(10))
 *                                              .initialDelay(Duration.ofSeconds(3))
 *                                              .build();
 * svc.startAsync().awaitRunning();
 * ...
 * svc.stopAsync().awaitTerminated();
 * }</pre>
 *
 * <h2>주기 계산 방식</h2>
 * <ul>
 *   <li>{@link Builder#fixedRate(Duration)} ({@link IntervalMode#FIXED_RATE}, 기본값): loop 시작 시각을 기준으로
 *       매 iteration의 시작 시각을 누적 계산한다. 즉 {@code n}번째 iteration은
 *       {@code loop시작시각 + rate * n}에 시작한다. 한 iteration의 수행 시간이 주기보다 길어지면
 *       다음 iteration은 대기 없이 곧바로 시작된다(catch-up).</li>
 *   <li>{@link Builder#fixedDelay(Duration)} ({@link IntervalMode#FIXED_DELAY}): 매 iteration이 <b>종료된 시각</b>부터
 *       고정된 시간만큼 대기한 뒤 다음 iteration을 시작한다. 따라서 실제 반복 간격은
 *       {@code 작업 수행 시간 + delay}가 된다.</li>
 * </ul>
 * 주기와 초기 지연은 시스템 시계가 아닌 단조 시계({@link System#nanoTime()})로 측정한다. 따라서 NTP 동기화 등으로
 * 시스템 시계가 앞뒤로 점프해도 iteration이 몰려서 실행되거나(catch-up 폭주) 대기가 길어지지 않는다.
 *
 * <h2>종료 및 예외 처리</h2>
 * <ul>
 *   <li>{@link #stopAsync()}가 호출되면 {@link #triggerShutdown()}을 통해 중지 요청이 기록되고,
 *       주기 대기 중이던 쓰레드가 즉시 깨어나 loop을 종료한다. 이미 시작된 iteration은
 *       중간에 중단되지 않고 끝까지 수행된다.</li>
 *   <li>작업 수행 중 {@link InterruptedException}이 발생하거나 대기 중 쓰레드가 인터럽트된 경우에는
 *       중지 요청과 동일하게 취급하여 loop을 정상 종료한다 (서비스는 {@code TERMINATED} 상태가 된다).</li>
 *   <li>그 외의 예외가 발생한 경우의 동작은 {@link Builder#stopOnError(boolean)} 설정에 따른다.
 *       {@code true}(기본값)이면 loop을 중단하고 서비스를 {@code FAILED} 상태로 전이시키며
 *       ({@link Service#failureCause()}로 원인 조회 가능), {@code false}이면 예외를 로그로 남기고
 *       다음 iteration을 계속 수행한다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopService extends AbstractLoopService implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(PeriodicLoopService.class);

	private final String m_name;
	private final Duration m_interval;
	private final IntervalMode m_intervalMode;
	private final @Nullable Duration m_initialDelay;
	private final boolean m_stopOnError;
	private final @Nullable CheckedRunnable m_task;
	private final @Nullable CheckedRunnable m_initializer;
	private final @Nullable CheckedRunnable m_finalizer;

	private volatile long m_iterationCount = 0;
	private volatile Logger m_logger = null;

	/**
	 * 주어진 빌더의 설정으로 서비스를 생성한다.
	 * <p>
	 * 상속 클래스에서 {@link #performPeriodicAction(long)}를 재정의하는 경우에 사용한다.
	 *
	 * @param builder 서비스 설정을 담은 빌더.
	 */
	protected PeriodicLoopService(Builder builder) {
		Preconditions.checkNotNullArgument(builder, "builder is null");

		m_name = builder.m_name;
		m_interval = builder.m_interval;
		m_intervalMode = builder.m_intervalMode;
		// 초기 지연이 0인 경우는 지연이 없는 것으로 간주한다.
		m_initialDelay = ( builder.m_initialDelay != null && !builder.m_initialDelay.isZero() )
						? builder.m_initialDelay : null;
		m_stopOnError = builder.m_stopOnError;
		m_task = builder.m_task;
		m_initializer = builder.m_initializer;
		m_finalizer = builder.m_finalizer;
	}

	/**
	 * 새 {@link Builder}를 생성한다.
	 * <p>
	 * 반복 수행할 작업을 설정하지 않는 경우에는 반드시 {@link #performPeriodicAction(long)}를
	 * 재정의해야 한다.
	 *
	 * @return 새 빌더.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 주어진 작업을 반복 수행하는 서비스를 위한 {@link Builder}를 생성한다.
	 *
	 * @param task 주기적으로 반복 수행할 작업.
	 *             {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static Builder builder(CheckedRunnable task) {
		return new Builder().task(task);
	}

	/**
	 * 서비스의 이름을 반환한다.
	 * <p>
	 * 이 이름은 Guava가 loop 수행 쓰레드의 이름을 구성할 때 사용한다.
	 *
	 * @return 서비스 이름.
	 */
	@Override
	protected String serviceName() {
		return m_name;
	}

	/**
	 * 설정된 반복 주기를 반환한다.
	 *
	 * @return 반복 주기.
	 */
	public Duration getLoopInterval() {
		return m_interval;
	}

	/**
	 * 주기 계산 방식을 반환한다.
	 *
	 * @return {@link Builder#fixedRate(Duration)}로 설정된 경우는 {@link IntervalMode#FIXED_RATE},
	 *         {@link Builder#fixedDelay(Duration)}로 설정된 경우는 {@link IntervalMode#FIXED_DELAY}.
	 */
	public IntervalMode getIntervalMode() {
		return m_intervalMode;
	}

	/**
	 * 누적 주기(fixed-rate) 모드 여부를 반환한다.
	 *
	 * @return {@link IntervalMode#FIXED_RATE}이면 {@code true}, {@link IntervalMode#FIXED_DELAY}이면 {@code false}.
	 * @deprecated	{@link #getIntervalMode()}를 사용한다.
	 */
	@Deprecated
	public boolean isCumulativeInterval() {
		return m_intervalMode == IntervalMode.FIXED_RATE;
	}

	/**
	 * 첫 iteration 수행 전 대기 시간을 반환한다.
	 *
	 * @return 초기 지연 시간. 설정되지 않은 경우는 {@code null}.
	 */
	public @Nullable Duration getInitialDelay() {
		return m_initialDelay;
	}

	/**
	 * 지금까지 완료된 iteration의 횟수를 반환한다.
	 * <p>
	 * 작업 수행 중 예외가 발생한 iteration도 완료된 것으로 계수한다.
	 *
	 * @return 완료된 iteration 횟수.
	 */
	public long getIterationCount() {
		return m_iterationCount;
	}

	@Override
	public Logger getLogger() {
		return Optionals.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	@Override
	public String toString() {
		String intervalStr = (m_intervalMode == IntervalMode.FIXED_RATE)
								? "fixedRate=" + m_interval : "fixedDelay=" + m_interval;
		return String.format("%s[%s, iterations=%d]", m_name, intervalStr, m_iterationCount);
	}

	/**
	 * 주기적으로 반복 수행할 작업을 수행한다.
	 * <p>
	 * 기본 구현은 빌더를 통해 설정된 작업을 수행한다. 작업이 설정되지 않은 경우에는 본 메소드를
	 * 반드시 재정의해야 하며, 그렇지 않은 경우 {@link IllegalStateException}이 발생한다.
	 * <p>
	 * 본 메소드 수행 중에는 중지 요청이 있어도 loop이 중단되지 않기 때문에, 오래 걸리는 작업의 경우에는
	 * 내부에서 주기적으로 {@link #isStopRequested()}를 검사하여 조기 반환하도록 구현하는 것이 좋다.
	 *
	 * @param loopIndex loop 인덱스. 0부터 시작하여 매 iteration마다 1씩 증가한다.
	 * @throws Exception 작업 수행 중 예외가 발생한 경우.
	 *                   이후 동작은 {@link Builder#stopOnError(boolean)} 설정에 따른다.
	 */
	protected void performPeriodicAction(long loopIndex) throws Exception {
		Preconditions.checkState(m_task != null,
								"no task is set: override performPeriodicAction(): service=%s", this);

		m_task.run();
	}

	/**
	 * 서비스 시작 시 1회 호출되는 초기화 작업.
	 * <p>
	 * 기본 구현은 빌더를 통해 설정된 initializer를 수행한다.
	 * 본 메소드에서 예외가 발생하면 서비스는 {@code FAILED} 상태로 전이되고
	 * loop과 {@link #shutDown()}은 수행되지 않는다.
	 *
	 * @throws Exception 초기화 과정에서 예외가 발생한 경우.
	 */
	@Override
	protected void startUp() throws Exception {
		if ( m_initializer != null ) {
			m_initializer.run();
		}
	}

	/**
	 * 서비스 종료 시 1회 호출되는 정리 작업.
	 * <p>
	 * 기본 구현은 빌더를 통해 설정된 finalizer를 수행한다.
	 * {@link #startUp()}이 성공한 이후라면 loop의 종료 사유(중지 요청/인터럽트/작업 실패)와 무관하게
	 * 항상 호출되며, finalizer 수행 중 발생한 예외는 로그만 남기고 무시한다.
	 */
	@Override
	protected void shutDown() {
		if ( m_finalizer != null ) {
			try {
				m_finalizer.run();
			}
			catch ( Throwable e ) {
				getLogger().warn("finalizer failed: service=" + this, e);
			}
		}
	}

	/**
	 * 중지 요청을 기록하여 주기 대기 중인 loop 쓰레드를 깨운다.
	 * <p>
	 * Guava {@link Service}가 {@link #stopAsync()} 처리 과정에서 호출한다.
	 */
	@Override
	protected void triggerShutdown() {
		markStopRequested();
	}

	@Override
	protected final void run() throws Exception {
		// 일정은 시스템 시계가 아닌 단조 시계(System.nanoTime) 기준으로, loop 시작 시점부터의 경과 시간(ns)으로
		// 계산한다. 시스템 시계가 NTP 동기화 등으로 점프해도 iteration이 몰려서 실행되거나 대기가 길어지지 않는다.
		long originNanos = System.nanoTime();

		// 초기 지연이 설정된 경우에는 그 시간만큼 대기한 후 첫 iteration을 시작한다.
		// FIXED_RATE 모드의 주기 계산 기준 시점은 초기 지연이 끝나는 시점이다.
		long baseNanos = (m_initialDelay != null) ? toNanosSaturated(m_initialDelay) : 0;
		if ( baseNanos > 0 && !awaitNanos(baseNanos) ) {
			return;
		}

		long intervalNanos = toNanosSaturated(m_interval);
		for ( long loopIndex = 0; !isStopRequested(); ++loopIndex ) {
			try {
				performPeriodicAction(loopIndex);
			}
			catch ( InterruptedException e ) {
				// 작업 수행 중 인터럽트된 경우는 중지 요청과 동일하게 취급하여 loop을 종료한다.
				Thread.currentThread().interrupt();
				markStopRequested();
				getLogger().info("interrupted while performing the periodic action: service={}", this);

				return;
			}
			catch ( Exception e ) {
				if ( m_stopOnError ) {
					// 예외를 그대로 전파시켜 서비스를 FAILED 상태로 전이시킨다.
					getLogger().warn("periodic action failed, stopping the service: service=" + this, e);
					throw e;
				}

				getLogger().warn("periodic action failed, continuing the loop: service=" + this, e);
			}
			finally {
				m_iterationCount = loopIndex + 1;
			}

			//
			// 다음 iteration의 시작 시점(loop 시작 시점부터의 경과 시간)을 계산하고, 그때까지 대기한다.
			//
			long elapsedNanos = System.nanoTime() - originNanos;
			long nextStartNanos;
			if ( m_intervalMode == IntervalMode.FIXED_RATE ) {
				// FIXED_RATE 모드인 경우는 기준 시점에 'interval*(loopIndex+1)'을 더해서
				// 다음 iteration의 시작 시점을 계산한다.
				// 작업 수행 시간이 주기보다 긴 경우에는 계산된 시점이 이미 지났으므로
				// 대기 없이 곧바로 다음 iteration이 시작된다.
				nextStartNanos = LongMath.saturatedAdd(baseNanos, LongMath.saturatedMultiply(intervalNanos, loopIndex + 1));
			}
			else {
				// FIXED_DELAY 모드인 경우는 이번 iteration이 종료된 시점을 기준으로
				// 다음 iteration의 시작 시점을 계산한다.
				nextStartNanos = LongMath.saturatedAdd(elapsedNanos, intervalNanos);
			}

			if ( !awaitNanos(Math.max(0, nextStartNanos - elapsedNanos)) ) {
				return;
			}
		}
	}

	/**
	 * 주어진 시간만큼 대기한다.
	 * <p>
	 * 대기 시간은 단조 시계 기준으로 측정되므로 시스템 시계 변경의 영향을 받지 않는다.
	 * 대기 중에 중지 요청이 발생하거나 쓰레드가 인터럽트된 경우에는 즉시 대기를 종료한다.
	 *
	 * @param waitNanos 대기 시간(ns). {@code 0}이면 중지 요청 여부만 확인한다.
	 * @return 중지 요청 없이 대기 시간이 경과한 경우는 {@code true},
	 *         중지 요청 또는 인터럽트로 대기가 중단된 경우는 {@code false}.
	 */
	private boolean awaitNanos(long waitNanos) {
		try {
			m_guard.awaitCondition(() -> m_stopRequested, Duration.ofNanos(waitNanos)).andReturn();

			// 중지 요청으로 대기가 종료된 경우.
			return false;
		}
		catch ( TimeoutException expected ) {
			// 중지 요청 없이 대기 시간이 경과한 경우.
			return true;
		}
		catch ( InterruptedException e ) {
			// 대기 중 인터럽트된 경우는 중지 요청과 동일하게 취급한다.
			Thread.currentThread().interrupt();
			markStopRequested();
			getLogger().info("interrupted while waiting for the next iteration: service={}", this);

			return false;
		}
	}

	/**
	 * {@link Duration}을 nano 초로 변환한다. {@code long} 범위를 넘는 경우에는 최댓값으로 포화시킨다.
	 */
	private static long toNanosSaturated(Duration duration) {
		try {
			return duration.toNanos();
		}
		catch ( ArithmeticException e ) {
			return Long.MAX_VALUE;
		}
	}

	/**
	 * {@link PeriodicLoopService}의 빌더.
	 * <p>
	 * {@link #fixedRate(Duration)}과 {@link #fixedDelay(Duration)}는 상호 배타적이며,
	 * 나중에 호출된 쪽이 적용된다. 미설정 시 {@code fixedRate(1초)}가 사용된다.
	 */
	public static class Builder {
		private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);
		private static final String DEFAULT_NAME = "periodic-service";

		private String m_name = DEFAULT_NAME;
		private Duration m_interval = DEFAULT_INTERVAL;
		private IntervalMode m_intervalMode = IntervalMode.FIXED_RATE;
		private @Nullable Duration m_initialDelay = null;
		private boolean m_stopOnError = true;
		private @Nullable CheckedRunnable m_task = null;
		private @Nullable CheckedRunnable m_initializer = null;
		private @Nullable CheckedRunnable m_finalizer = null;

		private Builder() { }

		/**
		 * 현재 설정으로 새 {@link PeriodicLoopService}를 생성한다.
		 *
		 * @return 새로 생성된 서비스.
		 */
		public PeriodicLoopService build() {
			return new PeriodicLoopService(this);
		}

		/**
		 * 서비스의 이름을 설정한다.
		 * <p>
		 * 설정된 이름은 loop 수행 쓰레드의 이름 등에 사용된다.
		 * 미설정 시 기본값 {@code "periodic-service"}가 사용된다.
		 *
		 * @param name 설정할 이름. {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder name(String name) {
			Preconditions.checkNotNullArgument(name, "name is null");

			m_name = name;
			return this;
		}

		/**
		 * 주기적으로 반복 수행할 작업을 설정한다.
		 *
		 * @param task 반복 수행할 작업.
		 *             {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder task(CheckedRunnable task) {
			Preconditions.checkNotNullArgument(task, "task is null");

			m_task = task;
			return this;
		}

		/**
		 * 고정 주기({@link IntervalMode#FIXED_RATE}) 모드로 반복 주기를 설정한다.
		 * <p>
		 * Loop 시작 시각을 기준으로 매 iteration의 시작 시각을 누적 계산한다. 한 iteration의 수행 시간이
		 * {@code rate}보다 길어지면 밀린 만큼 다음 iteration을 <b>대기 없이 연달아</b> 시작한다(catch-up).
		 * <p>
		 * 이 메소드와 {@link #fixedDelay(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 * 둘 다 설정하지 않으면 {@code fixedRate(1초)}가 적용된다.
		 *
		 * @param rate 반복 주기 (양수).
		 *             {@code null}이거나 양수가 아닌 경우 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder fixedRate(Duration rate) {
			Preconditions.checkArgument(rate != null && DurationUtils.isPositive(rate),
										"invalid rate: %s", rate);

			m_interval = rate;
			m_intervalMode = IntervalMode.FIXED_RATE;
			return this;
		}

		/**
		 * 고정 지연({@link IntervalMode#FIXED_DELAY}) 모드로 반복 주기를 설정한다.
		 * <p>
		 * {@link #fixedRate(Duration)}과 달리 이전 iteration의 수행 시간을 고려하지 않고,
		 * 매 iteration이 종료된 시각부터 고정된 {@code delay}만큼 대기한 뒤 다음 iteration을 시작한다.
		 * 한 iteration이 오래 걸려도 연달아 수행되지 않는다.
		 * <p>
		 * 이 메소드와 {@link #fixedRate(Duration)}는 상호 배타적이며, 나중에 호출된 쪽이 적용된다.
		 *
		 * @param delay 각 iteration 종료 후 다음 iteration 시작까지의 대기 시간 (양수).
		 *              {@code null}이거나 양수가 아닌 경우 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder fixedDelay(Duration delay) {
			Preconditions.checkArgument(delay != null && DurationUtils.isPositive(delay),
										"invalid delay: %s", delay);

			m_interval = delay;
			m_intervalMode = IntervalMode.FIXED_DELAY;
			return this;
		}

		/**
		 * 고정 주기({@link IntervalMode#FIXED_RATE}) 모드로 반복 주기를 설정한다.
		 *
		 * @param interval 반복 주기 (양수).
		 * @return 이 빌더.
		 * @deprecated	밀린 경우 연달아 실행된다는 성질이 호출부에서 드러나도록 {@link #fixedRate(Duration)}를 사용한다.
		 */
		@Deprecated
		public Builder interval(Duration interval) {
			return fixedRate(interval);
		}

		/**
		 * 고정 지연({@link IntervalMode#FIXED_DELAY}) 모드로 반복 주기를 설정한다.
		 *
		 * @param delay 각 iteration 종료 후 다음 iteration 시작까지의 대기 시간 (양수).
		 * @return 이 빌더.
		 * @deprecated	{@link #fixedRate(Duration)}와 짝을 이루는 {@link #fixedDelay(Duration)}를 사용한다.
		 */
		@Deprecated
		public Builder delay(Duration delay) {
			return fixedDelay(delay);
		}

		/**
		 * 서비스 시작 후 첫 iteration이 수행되기까지의 대기 시간을 설정한다.
		 * <p>
		 * 미설정 시에는 서비스가 시작되면 곧바로 첫 iteration이 수행된다.
		 * {@link IntervalMode#FIXED_RATE} 모드인 경우 초기 지연이 끝나는 시각이 주기 계산의 기준 시각이 된다.
		 *
		 * @param initialDelay 첫 iteration 수행 전 대기 시간 (음수가 아니어야 함).
		 *                     {@code null} 또는 {@link Duration#ZERO}이면 지연 없음.
		 * @return 이 빌더.
		 */
		public Builder initialDelay(@Nullable Duration initialDelay) {
			Preconditions.checkArgument(initialDelay == null || !initialDelay.isNegative(),
										"invalid initialDelay: %s", initialDelay);

			m_initialDelay = initialDelay;
			return this;
		}

		/**
		 * 작업 수행 중 예외가 발생한 경우의 처리 방식을 설정한다.
		 *
		 * @param stopOnError {@code true}인 경우 (기본값) loop을 중단하고 서비스를 {@code FAILED}
		 *                    상태로 전이시키고, {@code false}인 경우 예외를 로그로 남기고
		 *                    다음 iteration을 계속 수행한다.
		 * @return 이 빌더.
		 */
		public Builder stopOnError(boolean stopOnError) {
			m_stopOnError = stopOnError;
			return this;
		}

		/**
		 * 서비스 시작 시 1회 수행될 초기화 작업을 설정한다.
		 * <p>
		 * 초기화 중 발생한 예외는 서비스를 {@code FAILED} 상태로 전이시키며, 이 경우
		 * {@link #finalizer(CheckedRunnable) finalizer}는 수행되지 않는다.
		 *
		 * @param initializer 초기화 작업.
		 *                    {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder initializer(CheckedRunnable initializer) {
			Preconditions.checkNotNullArgument(initializer, "initializer is null");

			m_initializer = initializer;
			return this;
		}

		/**
		 * 서비스 종료 시 1회 수행될 정리 작업을 설정한다.
		 * <p>
		 * 초기화가 성공한 이후라면 loop의 종료 사유와 무관하게 항상 수행되며,
		 * 수행 중 발생한 예외는 로그만 남기고 무시된다.
		 *
		 * @param finalizer 정리 작업.
		 *                  {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder finalizer(CheckedRunnable finalizer) {
			Preconditions.checkNotNullArgument(finalizer, "finalizer is null");

			m_finalizer = finalizer;
			return this;
		}
	}
}
