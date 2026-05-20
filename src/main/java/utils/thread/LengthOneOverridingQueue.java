package utils.thread;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.thread.Guard;


/**
 * 항상 가장 최근에 {@link #add(Object)}된 항목 하나만 보관하는 길이 1의 큐.
 * <p>
 * 새 항목이 들어오면 아직 소비되지 않은 이전 항목은 별도 통지 없이 폐기된다 (latest-wins).
 * 따라서 본 큐는 producer가 빠르게 갱신하는 상태/측정값을 consumer가 "가장 최신 값" 기준으로
 * 처리하면 충분한 경우 (예: 센서 샘플링, 진행률 알림, coalescing되어도 무방한 이벤트) 에 적합하다.
 * 모든 이벤트의 손실 없는 전달이 필요하면 본 큐 대신 {@link java.util.concurrent.BlockingQueue}를 사용한다.
 * <p>
 * 모든 public 메소드는 thread-safe하며 {@link Guard} 기반 lock으로 직렬화된다.
 * {@code null}은 "비어 있음"을 표현하는 sentinel이므로 {@link #add}는 {@code null}을 거부한다.
 *
 * @param <T>	항목 타입 (non-null)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LengthOneOverridingQueue<T> {
	private final Guard m_guard = Guard.create();
	@Nullable @GuardedBy("m_guard") private T m_data = null;

	/**
	 * 항목을 큐에 넣는다.
	 * <p>
	 * 큐에 이미 미수신 항목이 있으면 별도 통지 없이 폐기되고 새 항목으로 대체된다.
	 * {@link #poll()} 또는 {@link #poll(Duration)}로 대기 중인 쓰레드가 있으면 깨워진다.
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
	 *
	 * @return	큐에 보관돼 있던 항목 (non-null)
	 * @throws InterruptedException	대기 중 쓰레드가 인터럽트된 경우
	 */
	public T poll() throws InterruptedException {
		return m_guard.awaitCondition(() -> m_data != null)
						.andGet(this::takeAndClearInGuard);
	}

	/**
	 * 주어진 시간만큼 항목 도착을 대기한 후 반환하며 큐를 비운다.
	 *
	 * @param timeout	최대 대기 시간 (양수)
	 * @return			큐에 보관돼 있던 항목 (non-null)
	 * @throws InterruptedException	대기 중 쓰레드가 인터럽트된 경우
	 * @throws TimeoutException		{@code timeout} 안에 항목이 도착하지 않은 경우
	 * @throws IllegalArgumentException	{@code timeout}이 음수인 경우
	 */
	public T poll(Duration timeout) throws InterruptedException, TimeoutException {
		return m_guard.awaitCondition(() -> m_data != null, timeout)
						.andGet(this::takeAndClearInGuard);
	}

	/**
	 * 큐에 보관된 항목을 비파괴적으로 조회한다.
	 * <p>
	 * 대기하지 않으며 큐를 비우지 않는다. 비어 있는 경우 {@code null}을 반환한다.
	 *
	 * @return	현재 보관된 항목 또는 비어 있으면 {@code null}
	 */
	public @Nullable T peek() {
		return m_guard.get(() -> m_data);
	}

	@GuardedBy("m_guard")
	private T takeAndClearInGuard() {
		T data = m_data;
		m_data = null;
        return data;
	}
}