package utils.async;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.DurationUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.math.LongMath;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;

import utils.LoggerSettable;
import utils.RuntimeInterruptedException;
import utils.Preconditions;
import utils.func.CheckedConsumer;
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
 * 작업이 현재 iteration 번호나 중지 요청 여부를 알아야 하는 경우에는 {@link LoopContext}를 인자로 받는
 * {@link #builder(CheckedConsumer)}를 사용한다. 오래 걸리는 작업은 {@link LoopContext#isStopRequested()}를
 * 확인하여 {@link #stopAsync()}에 빠르게 반응할 수 있다.
 * <pre>{@code
 * PeriodicLoopService.builder(ctx -> {
 *         for ( Item item: fetchBatch() ) {
 *             if ( ctx.isStopRequested() ) {
 *                 return;
 *             }
 *             process(item);
 *         }
 *     })
 *     .fixedDelay(Duration.ofSeconds(5))
 *     .build();
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
 *       중지 요청과 동일하게 취급하여 loop을 정상 종료한다 (서비스는 {@code TERMINATED} 상태가 된다).
 *       작업이 인터럽트를 {@link RuntimeInterruptedException}으로 감싸 던지거나, 다른 예외를 던졌더라도 그 시점에
 *       loop 쓰레드가 인터럽트된 상태이면 마찬가지로 중지로 취급한다. 단, 원인 사슬에 {@link InterruptedException}이
 *       있다는 것만으로는 중지로 보지 않는다(다른 쓰레드의 작업이 인터럽트된 결과일 수 있기 때문이다).
 *       작업이 예외 없이 인터럽트 플래그만 설정하고 반환한 경우에도 다음 iteration을 시작하기 전에 중지된다.</li>
 *   <li>정리 작업({@link Builder#cleanup(CheckedRunnable)})은 인터럽트로 종료된 경우에도 인터럽트 상태를 지운 채
 *       수행되므로, 정리 중의 블로킹 호출이 곧바로 중단되지 않는다.</li>
 *   <li>그 외의 예외가 발생한 경우의 동작은 {@link Builder#errorPolicy(ErrorPolicy)}로 설정한 {@link ErrorPolicy}에 따른다.
 *       작업이 연속으로 실패한 횟수가 정책의 한도에 이르면 loop을 중단하고 서비스를 {@code FAILED} 상태로
 *       전이시키며({@link Service#failureCause()}로 마지막 원인 조회 가능), 한도에 이르기 전에는 예외를 로그로
 *       남기고 다음 iteration을 계속 수행한다. 연속 실패 횟수는 작업이 한 번이라도 성공하면 초기화된다.
 *       기본값은 첫 실패에서 중단하는 {@link ErrorPolicy#STOP}이다.</li>
 *   <li>작업이 연속으로 실패하면서 loop을 계속하는 경우, 같은 경고가 매 iteration마다 쌓이지 않도록
 *       로그를 줄인다. 연속 실패의 첫 번째는 스택 트레이스와 함께 {@code WARN}으로, 이후 실패는 {@code DEBUG}로
 *       남기되 연속 실패 횟수가 10, 100, 1000, … 이 될 때마다 아직 실패 중임을 {@code WARN}으로 한 줄 남긴다.
 *       다시 성공하면 연속 실패 횟수와 함께 복구되었음을 {@code INFO}로 남긴다.
 *       현재 연속 실패 횟수는 {@link #getConsecutiveFailureCount()}로 조회할 수 있다.</li>
 * </ul>
 *
 * <h2>Loop 쓰레드</h2>
 * Loop을 수행하는 쓰레드는 기본적으로 <b>daemon</b> 쓰레드이며, 이름은 서비스 이름({@link Builder#name(String)})이다.
 * 따라서 {@link #stopAsync()}를 호출하지 않은 서비스가 남아 있어도 JVM 종료를 막지 않는다. 서비스가 JVM의
 * 생존을 유지해야 하는 경우(예: {@code main}에서 서비스만 시작하고 반환하는 프로그램)에는
 * {@link Builder#daemon(boolean) daemon(false)}로 설정한다. (Guava {@link AbstractExecutionThreadService}의
 * 기본 쓰레드는 non-daemon이다.)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicLoopService extends AbstractLoopService implements LoggerSettable {
	/**
	 * 작업이 실패했을 때 서비스를 계속 수행할지 결정하는 정책.
	 * <p>
	 * 작업의 <b>연속</b> 실패 횟수가 한도({@link #getMaxConsecutiveFailures()})에 이르면 loop을 중단하고 서비스를
	 * {@code FAILED}로 전이시킨다. 한도에 이르기 전에는 예외를 로그로 남기고 다음 iteration을 계속 수행한다.
	 * 연속 실패 횟수는 작업이 한 번이라도 성공하면 초기화된다.
	 */
	public static final class ErrorPolicy {
		/** 첫 실패에서 서비스를 중단한다 ({@code failAfterConsecutiveFailures(1)}과 같다). 기본 정책이다. */
		public static final ErrorPolicy STOP = new ErrorPolicy(1);
		/** 실패해도 서비스를 중단하지 않고 계속 수행한다. */
		public static final ErrorPolicy CONTINUE = new ErrorPolicy(Long.MAX_VALUE);

		private final long m_maxConsecutiveFailures;

		private ErrorPolicy(long maxConsecutiveFailures) {
			m_maxConsecutiveFailures = maxConsecutiveFailures;
		}

		/**
		 * 연속 실패가 {@code maxConsecutiveFailures}회에 이르면 서비스를 중단하는 정책을 반환한다.
		 * <p>
		 * 일시적인 장애는 견디되, 장애가 계속되면 서비스를 {@code FAILED}로 끝내 상위에서 감지할 수 있게 할 때 사용한다.
		 *
		 * @param maxConsecutiveFailures	서비스를 중단할 연속 실패 횟수 (1 이상). {@code 1}이면 {@link #STOP}과 같다.
		 * @return	정책 객체.
		 * @throws IllegalArgumentException	{@code maxConsecutiveFailures}가 1보다 작은 경우.
		 */
		public static ErrorPolicy failAfterConsecutiveFailures(long maxConsecutiveFailures) {
			Preconditions.checkArgument(maxConsecutiveFailures >= 1,
										"maxConsecutiveFailures must be >= 1: %s", maxConsecutiveFailures);
			return (maxConsecutiveFailures == 1) ? STOP : new ErrorPolicy(maxConsecutiveFailures);
		}

		/**
		 * 서비스를 중단할 연속 실패 횟수를 반환한다.
		 *
		 * @return	연속 실패 한도. {@link #CONTINUE}이면 {@link Long#MAX_VALUE}.
		 */
		public long getMaxConsecutiveFailures() {
			return m_maxConsecutiveFailures;
		}

		/**
		 * 연속 실패가 주어진 횟수에 이르렀을 때 서비스를 중단해야 하는지 여부를 반환한다.
		 *
		 * @param consecutiveFailures	현재까지의 연속 실패 횟수.
		 * @return	중단해야 하면 {@code true}.
		 */
		public boolean shouldStop(long consecutiveFailures) {
			return consecutiveFailures >= m_maxConsecutiveFailures;
		}

		@Override
		public String toString() {
			if ( this == STOP ) {
				return "STOP";
			}
			else if ( m_maxConsecutiveFailures == Long.MAX_VALUE ) {
				return "CONTINUE";
			}
			else {
				return "failAfterConsecutiveFailures(" + m_maxConsecutiveFailures + ")";
			}
		}
	}

	/**
	 * Builder로 설정한 작업({@link Builder#task(CheckedConsumer)})에 전달되는 iteration 문맥.
	 * <p>
	 * 작업이 수행되는 동안(loop 쓰레드)에만 의미가 있다.
	 */
	public interface LoopContext {
		/**
		 * 현재 iteration 번호를 반환한다. 첫 iteration은 {@code 0}이며 매 iteration마다 1씩 증가한다.
		 *
		 * @return iteration 번호.
		 */
		public long iteration();

		/**
		 * 서비스에 중지가 요청되었는지 여부를 반환한다.
		 * <p>
		 * {@link PeriodicLoopService#stopAsync()}는 수행 중인 작업을 중단시키지 않으므로, 오래 걸리는 작업은
		 * 이 값을 주기적으로 확인하여 조기 반환하는 것이 좋다. lock 없이 읽으므로 자주 호출해도 비용이 거의 없다.
		 *
		 * @return 중지가 요청되었으면 {@code true}.
		 */
		public boolean isStopRequested();
	}

	private static final Logger s_logger = LoggerFactory.getLogger(PeriodicLoopService.class);

	private final String m_name;
	private final Duration m_interval;
	private final IntervalMode m_intervalMode;
	private final @Nullable Duration m_initialDelay;
	private final ErrorPolicy m_errorPolicy;
	private final boolean m_daemon;
	private final @Nullable CheckedConsumer<LoopContext> m_task;
	private final @Nullable CheckedRunnable m_initializer;
	private final @Nullable CheckedRunnable m_cleanup;

	private volatile long m_iterationCount = 0;
	private volatile long m_consecutiveFailures = 0;	// loop 쓰레드에서만 갱신된다.
	private volatile Logger m_logger = null;

	/**
	 * 이름과 반복 주기만으로 서비스를 생성한다.
	 * <p>
	 * 상속 클래스에서 {@link #performPeriodicAction(long)}를 재정의하는 경우에 사용한다. 나머지 설정은 기본값
	 * (초기 지연 없음, {@link ErrorPolicy#STOP}, daemon 쓰레드)을 따른다. 초기화·정리 작업은 {@link #startUp()}/
	 * {@link #shutDown()}을 재정의하여 구현한다. 그 밖의 설정(초기 지연, 오류 정책 등)이 필요하면
	 * {@link #PeriodicLoopService(Builder)}를 사용한다.
	 * <pre>{@code
	 * class StatCollector extends PeriodicLoopService {
	 *     StatCollector() {
	 *         super("stat-collector", Duration.ofSeconds(10), IntervalMode.FIXED_RATE);
	 *     }
	 *     @Override
	 *     protected void performPeriodicAction(long loopIndex) throws Exception {
	 *         collectStatistics();
	 *     }
	 * }
	 * }</pre>
	 *
	 * @param name		서비스 이름. loop 쓰레드의 이름으로도 사용된다.
	 * @param interval	반복 주기 (양수).
	 * @param mode		주기 계산 방식.
	 * @throws IllegalArgumentException	인자가 {@code null}이거나 {@code interval}이 양수가 아닌 경우.
	 */
	protected PeriodicLoopService(String name, Duration interval, IntervalMode mode) {
		this(newBuilder(name, interval, mode));
	}

	private static Builder newBuilder(String name, Duration interval, IntervalMode mode) {
		Preconditions.checkNotNullArgument(mode, "interval mode is null");

		Builder builder = new Builder().name(name);
		return (mode == IntervalMode.FIXED_RATE) ? builder.fixedRate(interval) : builder.fixedDelay(interval);
	}

	/**
	 * 주어진 빌더의 설정으로 서비스를 생성한다.
	 * <p>
	 * 상속 클래스에서 {@link #performPeriodicAction(long)}를 재정의하면서 초기 지연, 오류 정책 등 빌더의 설정을
	 * 함께 사용하는 경우에 사용한다. 이름과 주기만 필요하면 {@link #PeriodicLoopService(String, Duration, IntervalMode)}가
	 * 간단하다.
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
		m_errorPolicy = builder.m_errorPolicy;
		m_daemon = builder.m_daemon;
		m_task = builder.m_task;
		m_initializer = builder.m_initializer;
		m_cleanup = builder.m_cleanup;
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
	 * 주어진 작업을 반복 수행하는 서비스를 위한 {@link Builder}를 생성한다.
	 * <p>
	 * {@link #builder(CheckedRunnable)}와 같지만, 작업이 {@link LoopContext}를 통해 현재 iteration 번호와
	 * 중지 요청 여부를 알 수 있다.
	 *
	 * @param task 주기적으로 반복 수행할 작업. iteration 문맥을 인자로 받는다.
	 *             {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
	 * @return 새 빌더.
	 */
	public static Builder builder(CheckedConsumer<LoopContext> task) {
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
	 * 첫 iteration 수행 전 대기 시간을 반환한다.
	 *
	 * @return 초기 지연 시간. 설정되지 않은 경우는 {@code null}.
	 */
	public @Nullable Duration getInitialDelay() {
		return m_initialDelay;
	}

	/**
	 * Loop 쓰레드가 daemon 쓰레드인지 여부를 반환한다.
	 *
	 * @return daemon 쓰레드이면 {@code true}.
	 */
	public boolean isDaemon() {
		return m_daemon;
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

	/**
	 * 현재까지 이어지고 있는 작업의 연속 실패 횟수를 반환한다.
	 * <p>
	 * 작업이 성공하면 {@code 0}으로 초기화된다. 실패해도 계속 수행하도록 설정된 서비스에서
	 * 작업이 계속 실패하고 있는지 모니터링하는 용도로 사용한다.
	 *
	 * @return 연속 실패 횟수. 마지막 iteration이 성공했거나 아직 수행되지 않았으면 {@code 0}.
	 */
	public long getConsecutiveFailureCount() {
		return m_consecutiveFailures;
	}

	/**
	 * 작업 실패 시의 처리 정책을 반환한다.
	 *
	 * @return 오류 처리 정책.
	 */
	public ErrorPolicy getErrorPolicy() {
		return m_errorPolicy;
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
	 * Builder로 설정한 작업은 {@link LoopContext#isStopRequested()}로 같은 값을 확인할 수 있다.
	 *
	 * @param loopIndex loop 인덱스. 0부터 시작하여 매 iteration마다 1씩 증가한다.
	 * @throws Exception 작업 수행 중 예외가 발생한 경우.
	 *                   이후 동작은 {@link Builder#errorPolicy(ErrorPolicy)} 설정에 따른다.
	 */
	protected void performPeriodicAction(long loopIndex) throws Exception {
		Preconditions.checkState(m_task != null,
								"no task is set: override performPeriodicAction(): service=%s", this);

		m_task.accept(new LoopContextImpl(loopIndex));
	}

	/** {@link LoopContext} 구현. 중지 요청 여부는 서비스의 현재 값을 그대로 돌려준다. */
	private final class LoopContextImpl implements LoopContext {
		private final long m_iteration;

		LoopContextImpl(long iteration) {
			m_iteration = iteration;
		}

		@Override
		public long iteration() {
			return m_iteration;
		}

		@Override
		public boolean isStopRequested() {
			return PeriodicLoopService.this.isStopRequested();
		}

		@Override
		public String toString() {
			return String.format("LoopContext[iteration=%d, service=%s]", m_iteration, PeriodicLoopService.this);
		}
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
	 * 기본 구현은 빌더를 통해 설정된 정리 작업({@link Builder#cleanup(CheckedRunnable)})을 수행한다.
	 * {@link #startUp()}이 성공한 이후라면 loop의 종료 사유(중지 요청/인터럽트/작업 실패)와 무관하게
	 * 항상 호출되며, 정리 작업 수행 중 발생한 예외는 로그만 남기고 무시한다.
	 * <p>
	 * 인터럽트로 loop이 끝난 경우에도 정리 작업의 블로킹 호출({@code sleep()}, {@code await()}, {@code join()} 등)이
	 * 곧바로 중단되지 않도록, 인터럽트 상태를 지운 채로 정리 작업을 수행하고 끝나면 인터럽트 상태를 복원한다.
	 */
	@Override
	protected void shutDown() {
		if ( m_cleanup != null ) {
			boolean interrupted = Thread.interrupted();
			try {
				m_cleanup.run();
			}
			catch ( Throwable e ) {
				getLogger().warn("cleanup failed: service=" + this, e);
			}
			finally {
				if ( interrupted ) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * Loop을 수행할 쓰레드를 생성하는 {@link Executor}를 반환한다.
	 * <p>
	 * Guava의 기본 구현과 같이 서비스 이름을 쓰레드 이름으로 사용하되, 쓰레드의 daemon 여부를
	 * {@link Builder#daemon(boolean)} 설정에 따른다.
	 */
	@Override
	protected Executor executor() {
		return command -> {
			Thread thread = MoreExecutors.platformThreadFactory().newThread(command);
			thread.setName(serviceName());
			thread.setDaemon(m_daemon);
			thread.start();
		};
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
			// 인터럽트 여부는 대기에 의존하지 않고 iteration마다 직접 확인한다. FIXED_RATE에서 밀린 iteration을 대기 없이
			// 연달아 수행하는 동안에는 대기(awaitNanos(0))가 실제로 일어나지 않아 인터럽트가 감지되지 않기 때문이다.
			if ( Thread.currentThread().isInterrupted() ) {
				markStopRequested();
				getLogger().info("interrupted, stopping the loop: service={}", this);
				return;
			}

			try {
				performPeriodicAction(loopIndex);
				onActionSucceeded();
			}
			catch ( InterruptedException | RuntimeInterruptedException e ) {
				// 작업 수행 중 인터럽트된 경우는 중지 요청과 동일하게 취급하여 loop을 종료한다.
				// RuntimeInterruptedException은 InterruptedException을 unchecked로 감싼 것이므로 같게 취급한다.
				stopOnInterrupt();
				return;
			}
			catch ( Exception e ) {
				if ( Thread.currentThread().isInterrupted() ) {
					// 작업이 인터럽트를 다른 예외로 바꾸어 던졌더라도 이 쓰레드가 인터럽트된 상태라면
					// 작업 실패가 아니라 중지로 취급한다.
					stopOnInterrupt();
					return;
				}
				long failures = ++m_consecutiveFailures;
				if ( m_errorPolicy.shouldStop(failures) ) {
					// 연속 실패가 정책의 한도에 이르렀으므로 예외를 그대로 전파시켜 서비스를 FAILED 상태로 전이시킨다.
					if ( failures == 1 ) {
						getLogger().warn("periodic action failed, stopping the service: service=" + this, e);
					}
					else {
						getLogger().warn(String.format("periodic action failed %d consecutive times, "
														+ "stopping the service (policy=%s): service=%s",
														failures, m_errorPolicy, this), e);
					}
					throw e;
				}

				onActionFailedAndContinuing(e, failures);
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
	 * 작업 수행 중 인터럽트된 경우, 인터럽트 상태를 유지한 채 중지 요청을 기록한다.
	 */
	private void stopOnInterrupt() {
		Thread.currentThread().interrupt();
		markStopRequested();
		getLogger().info("interrupted while performing the periodic action: service={}", this);
	}

	/**
	 * 작업이 성공한 경우, 직전까지 연속 실패가 있었으면 복구되었음을 로그로 남기고 실패 횟수를 초기화한다.
	 */
	private void onActionSucceeded() {
		long failures = m_consecutiveFailures;
		if ( failures > 0 ) {
			m_consecutiveFailures = 0;
			getLogger().info("periodic action recovered after {} consecutive failure(s): service={}",
							failures, this);
		}
	}

	/**
	 * 작업이 실패했지만 loop을 계속하는 경우의 로그를 남긴다.
	 * <p>
	 * 같은 경고가 매 iteration마다 쌓이지 않도록, 연속 실패의 첫 번째만 스택 트레이스와 함께 {@code WARN}으로
	 * 남기고 이후는 {@code DEBUG}로 남긴다. 단, 장애가 이어지는 동안 완전히 조용해지지 않도록 연속 실패 횟수가
	 * 10의 거듭제곱(10, 100, 1000, …)이 될 때마다 {@code WARN}을 한 줄 남긴다.
	 */
	private void onActionFailedAndContinuing(Exception e, long failures) {
		if ( failures == 1 ) {
			getLogger().warn("periodic action failed, continuing the loop: service=" + this, e);
		}
		else if ( isPowerOfTen(failures) ) {
			getLogger().warn("periodic action still failing: {} consecutive failures, last cause={}, service={}",
							failures, e.toString(), this);
		}
		else {
			getLogger().debug("periodic action failed again ({} consecutive failures): cause={}, service={}",
							failures, e.toString(), this);
		}
	}

	private static boolean isPowerOfTen(long n) {
		if ( n < 10 ) {
			return false;
		}
		while ( n % 10 == 0 ) {
			n /= 10;
		}
		return n == 1;
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
		private ErrorPolicy m_errorPolicy = ErrorPolicy.STOP;
		private boolean m_daemon = true;
		private @Nullable CheckedConsumer<LoopContext> m_task = null;
		private @Nullable CheckedRunnable m_initializer = null;
		private @Nullable CheckedRunnable m_cleanup = null;

		private Builder() { }

		/**
		 * 현재 설정으로 새 {@link PeriodicLoopService}를 생성한다.
		 * <p>
		 * 반복 수행할 작업({@link #task(CheckedRunnable)} 또는 {@link #task(CheckedConsumer)})이 설정되어 있어야 한다.
		 * 작업 없이 {@link PeriodicLoopService#performPeriodicAction(long)}를 재정의하는 하위 클래스는 본 메소드 대신
		 * 빌더를 하위 클래스 생성자의 {@code super(builder)}로 넘긴다.
		 *
		 * @return 새로 생성된 서비스.
		 * @throws IllegalStateException	작업이 설정되지 않은 경우.
		 */
		public PeriodicLoopService build() {
			Preconditions.checkState(m_task != null,
									"no task is set: call task(...) or builder(task), "
									+ "or subclass PeriodicLoopService and override performPeriodicAction()");
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

			m_task = ctx -> task.run();
			return this;
		}

		/**
		 * 주기적으로 반복 수행할 작업을 설정한다.
		 * <p>
		 * {@link #task(CheckedRunnable)}와 같지만, 작업이 {@link LoopContext}를 통해 현재 iteration 번호와
		 * 중지 요청 여부를 알 수 있다. 두 메소드 중 나중에 호출된 쪽이 적용된다.
		 *
		 * @param task 반복 수행할 작업. iteration 문맥을 인자로 받는다.
		 *             {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder task(CheckedConsumer<LoopContext> task) {
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
		 * 작업 수행 중 예외가 발생한 경우의 처리 정책을 설정한다.
		 * <pre>{@code
		 * .errorPolicy(ErrorPolicy.STOP)                             // 첫 실패에서 FAILED (기본값)
		 * .errorPolicy(ErrorPolicy.CONTINUE)                         // 실패해도 계속 수행
		 * .errorPolicy(ErrorPolicy.failAfterConsecutiveFailures(5))  // 연속 5회째 실패에서 FAILED
		 * }</pre>
		 * 연속 실패 시의 로그 정책은 클래스 Javadoc 참조.
		 *
		 * @param policy	오류 처리 정책. {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder errorPolicy(ErrorPolicy policy) {
			Preconditions.checkNotNullArgument(policy, "error policy is null");

			m_errorPolicy = policy;
			return this;
		}

		/**
		 * 작업 수행 중 예외가 발생한 경우의 처리 방식을 설정한다.
		 * <p>
		 * {@code errorPolicy(stopOnError ? ErrorPolicy.STOP : ErrorPolicy.CONTINUE)}와 같다.
		 *
		 * @param stopOnError {@code true}인 경우 (기본값) 첫 실패에서 loop을 중단하고 서비스를 {@code FAILED}
		 *                    상태로 전이시키고, {@code false}인 경우 예외를 로그로 남기고
		 *                    다음 iteration을 계속 수행한다.
		 * @return 이 빌더.
		 */
		public Builder stopOnError(boolean stopOnError) {
			return errorPolicy(stopOnError ? ErrorPolicy.STOP : ErrorPolicy.CONTINUE);
		}

		/**
		 * Loop 쓰레드를 daemon 쓰레드로 생성할지 설정한다.
		 * <p>
		 * {@code true}(기본값)이면 {@link Service#stopAsync()}를 호출하지 않은 서비스가 남아 있어도 JVM이 종료될 수 있다.
		 * 서비스가 JVM의 생존을 유지해야 하는 경우에는 {@code false}로 설정한다.
		 *
		 * @param daemon daemon 쓰레드 여부.
		 * @return 이 빌더.
		 */
		public Builder daemon(boolean daemon) {
			m_daemon = daemon;
			return this;
		}

		/**
		 * 서비스 시작 시 1회 수행될 초기화 작업을 설정한다.
		 * <p>
		 * 초기화 중 발생한 예외는 서비스를 {@code FAILED} 상태로 전이시키며, 이 경우
		 * {@link #cleanup(CheckedRunnable) cleanup}은 수행되지 않는다.
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
		 * @param cleanup 정리 작업.
		 *                {@code null}을 전달하면 {@link IllegalArgumentException}이 발생한다.
		 * @return 이 빌더.
		 */
		public Builder cleanup(CheckedRunnable cleanup) {
			Preconditions.checkNotNullArgument(cleanup, "cleanup is null");

			m_cleanup = cleanup;
			return this;
		}
	}
}
