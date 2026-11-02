package utils.thread;

import java.time.Duration;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 항상 가장 최근에 {@link #add(Object)}된 항목 하나만 보관하는 길이 1의 큐.
 * <p>
 * 새 항목이 들어오면 아직 소비되지 않은 이전 항목은 별도 통지 없이 폐기된다 (latest-wins).
 * 따라서 본 큐는 producer가 빠르게 갱신하는 상태/측정값을 consumer가 "가장 최신 값" 기준으로
 * 처리하면 충분한 경우 (예: 센서 샘플링, 진행률 알림, coalescing되어도 무방한 이벤트) 에 적합하다.
 * 모든 이벤트의 손실 없는 전달이 필요하면 본 큐 대신 {@link java.util.concurrent.BlockingQueue}를 사용한다.
 * <p>
 * 꺼내는 메소드는 {@link java.util.concurrent.BlockingQueue}의 관례를 따른다: {@link #take()}는 블록하고,
 * {@link #poll()}은 항목 도착을 기다리지 않으며, {@link #poll(Duration)}은 시간 초과 시 {@code null}을 반환한다.
 * 기다리는 메소드({@code take}, {@code poll(Duration)})는 이미 인터럽트된 쓰레드라면 항목이 있어도 꺼내지 않는다.
 * ({@link java.util.Queue}를 구현하지는 않는다.)
 * <p>
 * 모든 public 메소드는 thread-safe하며 {@link Guard} 기반 lock으로 직렬화된다.
 * {@code null}은 "비어 있음"을 표현하는 sentinel이므로 {@link #add}는 {@code null}을 거부한다.
 *
 * @param <T>	항목 타입 (non-null)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SingleSlotOverwriteQueue<T> {
	private final Guard m_guard = Guard.create();
	@Nullable @GuardedBy("m_guard") private T m_data = null;

	/**
	 * 항목을 큐에 넣는다.
	 * <p>
	 * 큐에 이미 미수신 항목이 있으면 별도 통지 없이 폐기되고 새 항목으로 대체된다.
	 * {@link #take()} 또는 {@link #poll(Duration)}로 대기 중인 쓰레드가 있으면 깨워진다.
	 *
	 * @param item	저장할 항목 (non-null)
	 * @throws IllegalArgumentException	{@code item}이 {@code null}인 경우
	 */
	public void add(T item) {
		Preconditions.checkNotNullArgument(item, "item is null");

		m_guard.run(() -> {
			m_data = item;
		});
	}

	/**
	 * 항목이 도착할 때까지 대기한 후 반환하며 큐를 비운다.
	 * <p>
	 * 호출 시점에 항목이 이미 존재하면 즉시 반환한다. 없으면 {@link #add}가 항목을 넣을 때까지 블록한다.
	 * ({@link java.util.concurrent.BlockingQueue#take()}와 같은 의미.)
	 * <p>
	 * <b>인터럽트가 우선한다.</b> 호출할 때 이미 인터럽트된 쓰레드는 항목이 있어도 꺼내지 않고
	 * {@link InterruptedException}을 던진다(인터럽트 플래그는 지운다). JDK {@code BlockingQueue} 구현과 같은 정책이다.
	 *
	 * @return	큐에 보관돼 있던 항목 (non-null)
	 * @throws InterruptedException	호출할 때 이미 인터럽트된 경우, 또는 대기 중 인터럽트된 경우
	 */
	public T take() throws InterruptedException {
		throwIfInterrupted();

		m_guard.lock();
		try {
			while ( m_data == null ) {
				m_guard.awaitSignal();
			}
			return removeInGuard();
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 항목이 있으면 꺼내 반환하며 큐를 비운다. 항목 도착을 기다리지 않는다.
	 * <p>
	 * {@link java.util.Queue#poll()}과 같은 의미다. 인터럽트를 확인하지 않는다.
	 *
	 * @return	큐에 보관돼 있던 항목. 비어 있으면 {@code null}.
	 */
	public @Nullable T poll() {
		m_guard.lock();
		try {
			return removeInGuard();
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 주어진 시간만큼 항목 도착을 대기한 후 반환하며 큐를 비운다.
	 * <p>
	 * {@link java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)}과 같은 의미로, 시간 안에
	 * 항목이 오지 않으면 {@code null}을 반환한다.
	 * <p>
	 * 시간 제한은 <b>항목 도착을 기다리는 시간</b>에 적용되며, 내부 lock을 얻기까지의 시간은 포함하지 않는다. lock을 잡는
	 * 구간은 값 하나를 읽고 쓰는 동안뿐이라 실질적으로는 차이가 없다. 인터럽트는 {@link #take()}와 같이 우선한다.
	 *
	 * @param timeout	항목 도착을 기다릴 최대 시간 (0 이상. 0이면 기다리지 않는다)
	 * @return			큐에 보관돼 있던 항목. 시간 안에 오지 않으면 {@code null}.
	 * @throws InterruptedException	호출할 때 이미 인터럽트된 경우, 또는 대기 중 인터럽트된 경우
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 음수인 경우
	 */
	public @Nullable T poll(Duration timeout) throws InterruptedException {
		Preconditions.checkNotNullArgument(timeout, "timeout is null");
		Preconditions.checkArgument(!timeout.isNegative(), "timeout is negative: %s", timeout);
		throwIfInterrupted();

		long remainingNanos = toNanosSaturated(timeout);
		m_guard.lock();
		try {
			while ( m_data == null ) {
				if ( remainingNanos <= 0 ) {
					return null;
				}
				remainingNanos = m_guard.awaitSignalNanos(remainingNanos);
			}
			return removeInGuard();
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 큐에 보관된 항목을 비파괴적으로 조회한다.
	 * <p>
	 * 대기하지 않으며 큐를 비우지 않는다. 비어 있는 경우 {@code null}을 반환한다. 상태를 바꾸지 않으므로 대기자를
	 * 깨우지 않는다.
	 *
	 * @return	현재 보관된 항목 또는 비어 있으면 {@code null}
	 */
	public @Nullable T peek() {
		return m_guard.read(() -> m_data);
	}

	/**
	 * 보관된 항목을 꺼내고 큐를 비운다. lock을 잡은 상태에서 호출한다.
	 * <p>
	 * 대기자를 깨우지 않는다. 대기 조건은 "항목이 있다" 하나뿐이라, 항목을 꺼낸 뒤 깨워도 조건이 충족되지 않기
	 * 때문이다. 알림은 {@link #add(Object)}만 보낸다.
	 *
	 * @return	보관돼 있던 항목. 비어 있으면 {@code null}.
	 */
	@GuardedBy("m_guard")
	private @Nullable T removeInGuard() {
		T data = m_data;
		m_data = null;
		return data;
	}

	private static void throwIfInterrupted() throws InterruptedException {
		if ( Thread.interrupted() ) {
			throw new InterruptedException();
		}
	}

	/**
	 * {@link Duration}을 nanosecond로 바꾼다. 넘치면 {@link Long#MAX_VALUE}로 제한한다.
	 */
	private static long toNanosSaturated(Duration duration) {
		try {
			return duration.toNanos();
		}
		catch ( ArithmeticException e ) {
			return Long.MAX_VALUE;
		}
	}
}
