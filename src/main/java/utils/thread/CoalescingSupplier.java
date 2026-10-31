package utils.thread;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.Throwables;
import utils.func.CheckedSupplier;


/**
 * 동일한 값 생성 작업이 여러 쓰레드에서 중복 수행되지 않도록 조율하는 {@link CheckedSupplier}.
 *
 * <p>한 쓰레드가 {@link #get()} 을 호출해 생성 함수로부터 값을 생성하는 동안
 * 다른 쓰레드가 {@code get()} 을 호출하면, 새 생성을 시작하지 않고 진행 중인 생성이
 * 완료될 때까지 대기한 뒤 그 결과(또는 예외)를 공유받아 반환한다. 생성이 겹치지 않는
 * 순차적 호출에는 매번 새롭게 생성 함수를 호출한다 — 즉 본 클래스는 결과를 영구
 * 캐시(memoize)하지 않으며 "동시에 진행 중인 호출 간의 결과 공유(call coalescing)"
 * 만을 제공한다.
 *
 * <p>생성 한 번마다 그 결과를 담는 future 를 하나 만들고, 그동안 들어온 호출은 모두 그
 * future 를 기다린다. 따라서 대기 중인 호출은 자신이 합류한 바로 그 생성의 결과를 받으며,
 * 그 사이에 다음 생성이 시작되어도 영향을 받지 않는다. 생성 함수가 {@code null} 을
 * 반환하면 {@code null} 을 정상 결과로 공유한다. 모든 호출자가 <b>같은 객체</b>를 받으므로,
 * 가변 객체를 반환하는 경우 그 객체를 수정하는 호출자가 다른 호출자에 대한 영향을 책임져야 한다.
 *
 * <p>생성 함수가 예외로 끝나면 생성한 호출과 기다린 호출 모두 원인이 같은
 * {@link ExecutionException}을 받는다({@link CancellationException}도 같다). 단, 생성하던
 * 쓰레드 자신이 인터럽트되어 끝났으면 그 호출은 {@link InterruptedException}을 받는다.
 *
 * <p><b>재진입은 지원하지 않는다.</b> 생성 함수 안에서 같은 supplier 의 {@code get()} 을
 * 다시 호출하면 자신이 완료해야 할 결과를 기다리게 되므로 {@link IllegalStateException}으로
 * 즉시 거부한다.
 *
 * @param <T>  생성되는 값의 타입
 *
 * @author Kang-Woo Lee
 */
public class CoalescingSupplier<T> implements CheckedSupplier<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(CoalescingSupplier.class);

	@NotNull private final CheckedSupplier<? extends T> m_source;
	@NotNull private volatile Logger m_logger = s_logger;

	private final Guard m_guard = Guard.create();
	/** 진행 중인 생성의 결과를 받을 future. 진행 중인 생성이 없으면 {@code null}. */
	@GuardedBy("m_guard") private @Nullable CompletableFuture<T> m_inFlightResult;
	/** 진행 중인 생성을 수행하는 쓰레드. 재진입을 거부하는 데 쓴다. */
	@GuardedBy("m_guard") private @Nullable Thread m_producerThread;

	/**
	 * 주어진 생성 함수의 동시 호출을 하나로 합치는 supplier를 생성한다.
	 *
	 * @param source	값을 생성하는 함수 (non-null)
	 * @throws IllegalArgumentException	{@code source}가 {@code null}인 경우
	 */
	public CoalescingSupplier(@NotNull CheckedSupplier<? extends T> source) {
		Preconditions.checkNotNullArgument(source, "source is null");
		m_source = source;
	}

	/**
	 * 공유된 생성 결과를 반환한다. 진행 중인 생성이 있으면 끝날 때까지 제한 없이 기다린다.
	 *
	 * <p>호출 시점에 다른 쓰레드가 이미 생성 함수로부터 값을 생성 중이면,
	 * 그 생성이 끝날 때까지 대기한 뒤 동일한 결과(또는 예외)를 공유받아 반환한다.
	 * 진행 중인 생성이 없으면 현재 쓰레드가 직접 생성 함수를 호출하여 값을
	 * 생성하며, 이 동안 들어오는 다른 {@code get()} 호출은 위 규칙에 따라
	 * 같은 결과를 공유받는다.
	 *
	 * @return 생성 함수가 생성한 값. 생성 함수가 {@code null} 을 반환했으면 {@code null}.
	 * @throws InterruptedException  대기 중 쓰레드가 인터럽트된 경우, 또는 직접 생성하던 중 인터럽트된 경우
	 * @throws ExecutionException    생성 함수가 예외로 종료된 경우
	 *                               (원인 예외는 {@link ExecutionException#getCause()} 로 조회)
	 * @throws IllegalStateException 생성 함수 안에서 같은 supplier 를 다시 호출한 경우
	 */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		try {
			return get(null);
		}
		catch ( TimeoutException neverHappens ) {
			throw new AssertionError(neverHappens);
		}
	}

	/**
	 * 공유된 생성 결과를 반환한다. 진행 중인 생성에 합류해 기다리는 시간을 {@code joinTimeout} 으로 제한한다.
	 *
	 * <p>{@code joinTimeout} 은 <b>다른 쓰레드가 진행 중인 생성을 기다리는 시간</b>에만 적용된다. 진행 중인
	 * 생성이 없어 이 호출이 직접 생성하는 경우에는 생성 함수의 수행 시간을 제한하지 않는다. 전체 호출 시간을
	 * 제한해야 하면 생성 함수 자체에 제한을 두어야 한다. 대기를 포기해도 진행 중인 생성은 취소되지 않는다.
	 * {@link Long#MAX_VALUE} 나노초(약 292년)를 넘는 값은 그 값으로 제한한다.
	 *
	 * @param joinTimeout	진행 중인 생성을 기다리는 최대 시간. {@code null}이면 제한 없이 기다린다.
	 * 						음수는 허용하지 않는다.
	 * @return 생성 함수가 생성한 값. 생성 함수가 {@code null} 을 반환했으면 {@code null}.
	 * @throws InterruptedException  대기 중 쓰레드가 인터럽트된 경우, 또는 직접 생성하던 중 인터럽트된 경우
	 * @throws TimeoutException      {@code joinTimeout} 안에 진행 중인 생성이 끝나지 않은 경우
	 * @throws ExecutionException    생성 함수가 예외로 종료된 경우
	 * @throws IllegalArgumentException	{@code joinTimeout}이 음수인 경우
	 * @throws IllegalStateException 생성 함수 안에서 같은 supplier 를 다시 호출한 경우
	 */
	public T get(@Nullable Duration joinTimeout)
		throws InterruptedException, TimeoutException, ExecutionException {
		Preconditions.checkArgument(joinTimeout == null || !joinTimeout.isNegative(),
									"negative join timeout: %s", joinTimeout);

		CompletableFuture<T> sharedResult;
		boolean isProducer = false;
		m_guard.lock();
		try {
			if ( m_inFlightResult == null ) {
				m_inFlightResult = new CompletableFuture<>();
				m_producerThread = Thread.currentThread();
				isProducer = true;
			}
			else if ( m_producerThread == Thread.currentThread() ) {
				throw new IllegalStateException("reentrant call from the source supplier is not supported: "
												+ "it would wait for its own result");
			}
			sharedResult = m_inFlightResult;
		}
		finally {
			m_guard.unlock();
		}

		if ( isProducer ) {
			return produceAndPublish(sharedResult);
		}

		m_logger.debug("waiting for the in-flight production");
		try {
			return (joinTimeout != null) ? sharedResult.get(toNanosSaturated(joinTimeout), TimeUnit.NANOSECONDS)
										: sharedResult.get();
		}
		catch ( CancellationException e ) {
			// 이 future는 외부에 노출되지 않으므로 취소될 수 없다. 생성 함수가 CancellationException을 던진 경우이며,
			// 생성한 호출과 같이 ExecutionException으로 전달한다(CompletableFuture.get()은 이 예외만 감싸지 않는다).
			throw new ExecutionException(e);
		}
	}

	/**
	 * 진행 중인 생성의 결과를 기다리고 있는 호출 수의 추정치를 반환한다(진단·테스트용).
	 *
	 * @return	기다리는 호출 수. 진행 중인 생성이 없으면 0.
	 */
	int getWaitingCount() {
		return m_guard.get(() -> (m_inFlightResult != null) ? m_inFlightResult.getNumberOfDependents() : 0);
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}

	/**
	 * 생성 함수로 값을 생성하고, 그 결과로 {@code sharedResult} 를 완료시켜 기다리던 호출들에게 공유한다.
	 * <p>
	 * future 를 완료시키기 전에 진행 중 표시를 지우므로, 완료 이후의 호출은 이 결과에 합류하지 않고 새로
	 * 생성한다.
	 */
	private T produceAndPublish(CompletableFuture<T> sharedResult)
		throws InterruptedException, ExecutionException {
		T value;
		try {
			value = m_source.get();
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			clearInFlightIfCurrent(sharedResult);
			sharedResult.completeExceptionally(cause);

			if ( cause instanceof InterruptedException ie ) {
				// 생성하던 쓰레드 자신이 인터럽트된 것이므로 그대로 알린다. 기다리던 호출들은 ExecutionException으로 받는다.
				throw ie;
			}
			Throwables.throwIfInstanceOf(cause, VirtualMachineError.class);
			throw new ExecutionException(cause);
		}

		clearInFlightIfCurrent(sharedResult);
		sharedResult.complete(value);
		return value;
	}

	/**
	 * 주어진 결과가 진행 중인 생성의 결과이면 진행 중 표시를 지운다. future는 완료시키지 않는다.
	 */
	private void clearInFlightIfCurrent(CompletableFuture<T> sharedResult) {
		m_guard.run(() -> {
			// 현재 구현에서는 항상 참이다: m_inFlightResult는 비어 있을 때만 채워지고, 생성을 맡은 스레드만 여기서
			// 지운다. 진행 중인 생성을 버리고 새로 시작하는 경로(예: reset)가 생기더라도, 늦게 끝난 앞 생성이
			// 새 생성의 표시를 지우지 않도록 남겨 둔 방어 코드다.
			if ( m_inFlightResult == sharedResult ) {
				m_inFlightResult = null;
				m_producerThread = null;
			}
		});
	}

	private static long toNanosSaturated(Duration duration) {
		try {
			return duration.toNanos();
		}
		catch ( ArithmeticException e ) {
			return Long.MAX_VALUE;
		}
	}
}
