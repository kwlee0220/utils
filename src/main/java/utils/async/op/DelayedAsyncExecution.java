package utils.async.op;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import utils.Preconditions;
import utils.Throwables;
import utils.async.CompletableFutureAsyncExecution;
import utils.async.Execution;
import utils.async.StartableExecution;

/**
 * 주어진 {@link Execution}을 지정된 시간만큼 지연시킨 후에 시작·완료를 따라가는 합성 실행.
 * <p>
 * {@link #start()}가 호출되면 즉시 시작되지 않고, {@link CompletableFutureAsyncExecution#getDelayedExecutor}
 * 가 제공하는 지연 executor에 작업을 제출한다. 지연 시간이 경과한 뒤 본 클래스가 target execution을
 * 시작시키고 그 결과를 기다려 자신의 결과로 사용한다.
 * <p>
 * target이 {@link StartableExecution}이 아니라면 (이미 외부에서 시작되었다는 가정), 지연 후 그 결과를
 * 단순히 따라간다.
 *
 * @param <T> 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class DelayedAsyncExecution<T> extends CompletableFutureAsyncExecution<T> {
	private final Execution<? extends T> m_target;
	private final Duration m_delay;
	private final Executor m_executor;

	DelayedAsyncExecution(Execution<? extends T> target, Duration delay, Executor delayExecutor) {
		Preconditions.checkNotNullArgument(target, "target is null");
		Preconditions.checkNotNullArgument(delay, "delay is null");
		Preconditions.checkArgument(!delay.isNegative(), "delay is negative: %s", delay);
		Preconditions.checkNotNullArgument(delayExecutor, "delayExecutor is null");

		m_target = target;
		m_delay = delay;
		m_executor = delayExecutor;
	}

	/**
	 * 지연 후 시작될 대상 실행을 반환한다.
	 *
	 * @return 대상 {@link Execution}.
	 */
	public Execution<? extends T> getTargetExecution() {
		return m_target;
	}

	/**
	 * 시작 전 적용되는 지연 시간을 반환한다.
	 *
	 * @return 지연 시간.
	 */
	public Duration getDelay() {
		return m_delay;
	}

	@Override
	protected CompletableFuture<? extends T> startExecution() {
		Supplier<? extends T> invoker = () -> {
			try {
				if ( m_target.isNotStarted() ) {
					if ( m_target instanceof StartableExecution startable ) {
						startable.start();
					}
					else {
						throw new IllegalStateException("target execution is not startable: " + m_target);
					}
				}
				return m_target.get();
			}
			catch ( Throwable e ) {
				throw Throwables.toRuntimeException(e);
			}
		};
		
		return CompletableFuture.supplyAsync(invoker, m_executor);
	}
}