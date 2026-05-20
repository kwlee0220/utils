package utils.stream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.RuntimeInterruptedException;
import utils.Suppliable;
import utils.Throwables;
import utils.thread.Guard;
import utils.func.FOption;

/**
 * 외부에서 데이터를 push하고 {@link FStream} 인터페이스로 pull 소비하는 bounded buffer 스트림.
 * <p>
 * 지정된 capacity 만큼의 슬롯을 가지는 thread-safe 큐로 동작한다. {@link #supply(Object)}로
 * 데이터를 적재하고 {@link #next()}로 소비한다. 큐가 가득 찬 상태에서의 {@code supply}는 빈 슬롯이
 * 생길 때까지, 큐가 비어 있는 상태에서의 {@code next}는 데이터가 들어오거나 스트림이 종료될 때까지
 * 호출 쓰레드가 대기한다.
 * <p>
 * 대기 없이 비동기적으로 소비하고자 하는 경우는 {@link #poll()}을 사용한다 — 큐가 비어 있으면
 * 즉시 {@link FOption#empty()}를 반환한다. 또한 {@link #setSupplyListener(Runnable)}로 supply
 * 시점에 호출될 listener를 등록하여 데이터 적재 이벤트를 비동기로 감지할 수 있다.
 * <p>
 * 스트림 종료 경로는 두 가지가 있다.
 * <ul>
 *   <li>producer 측: {@link #endOfSupply()} 또는 {@link #endOfSupply(Throwable)}.
 *       이미 큐에 적재된 데이터는 그대로 소비 가능하며, 모두 소비되면 {@code next}는
 *       {@link FOption#empty()}를 반환한다. 단 {@code endOfSupply(Throwable)}로 종료된 경우는
 *       잔여 데이터가 모두 소비된 이후 등록된 에러를 RuntimeException으로 래핑해 던진다.</li>
 *   <li>consumer 측: {@link #close()}. 큐에 남아 있는 모든 미소비 데이터를 즉시 폐기하며,
 *       이후 {@code supply}는 {@link IllegalStateException}을 발생시킨다.</li>
 * </ul>
 * 두 종료 경로가 순차적으로 호출된 경우 {@code endOfSupply(Throwable)}로 등록된 에러는
 * 이후 {@link #close()}가 호출되어도 보존되어, 후속 {@code next} 호출 시 RuntimeException으로
 * 래핑되어 전달된다.
 * <p>
 * Producer 측 종료 호출 ({@code endOfSupply}, {@code endOfSupply(error)}) 끼리도 멱등하다 —
 * 두 번째 이후의 호출은 무시된다. 따라서 {@code endOfSupply()} 가 먼저 호출된 뒤
 * {@code endOfSupply(error)} 가 호출되어도 에러는 등록되지 않는다 (첫 종료 신호가 우선).
 * 에러 종료가 의도라면 {@code endOfSupply(error)} 를 먼저 호출해야 한다.
 * <p>
 * 모든 public 메소드는 thread-safe하므로 다수의 producer와 consumer가 동시에 사용해도 안전하다.
 *
 * @param <T>	스트림 원소 타입
 * @author Kang-Woo Lee (ETRI)
 */
public class SuppliableFStream<T> implements TimedFStream<T>, Suppliable<T> {
	private final int m_length;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final Deque<T> m_deque;
	@GuardedBy("m_guard") private boolean m_closed = false;
	@GuardedBy("m_guard") private boolean m_eos = false;
	@GuardedBy("m_guard") private @Nullable Throwable m_error = null;
	@GuardedBy("m_guard") private @Nullable Runnable m_onSupply = null;
	
	/**
	 * 지정된 capacity를 가지는 {@link SuppliableFStream}를 생성한다.
	 *
	 * @param length	큐 capacity. 양의 정수여야 한다.
	 * @throws IllegalArgumentException	{@code length}가 {@code 0} 이하인 경우.
	 */
	public SuppliableFStream(int length) {
		Preconditions.checkArgument(length > 0, String.format("invalid length: %d", length));

		m_deque = new ArrayDeque<>(length);
		m_length = length;
	}

	/**
	 * Capacity 제한이 없는({@code Integer.MAX_VALUE}) {@link SuppliableFStream}를 생성한다.
	 */
	SuppliableFStream() {
		m_deque = new ArrayDeque<>();
		m_length = Integer.MAX_VALUE;
	}

	/**
	 * 큐의 capacity를 반환한다.
	 *
	 * @return	큐 capacity. 무제한 capacity 인 경우 {@code Integer.MAX_VALUE}.
	 */
	public int capacity() {
		return m_length;
	}

	/**
	 * 큐에 현재 적재되어 있는 원소의 수를 반환한다.
	 * <p>
	 * 본 메소드의 결과는 호출 직후 다른 쓰레드의 {@code supply} / {@code next} 호출에 의해
	 * 즉시 변할 수 있다.
	 *
	 * @return	적재된 원소의 수
	 */
	public int size() {
		return m_guard.get(m_deque::size);
	}

	/**
	 * 큐의 빈 슬롯 수를 반환한다.
	 * <p>
	 * 본 메소드의 결과는 호출 직후 다른 쓰레드의 {@code supply} / {@code next} 호출에 의해
	 * 즉시 변할 수 있다.
	 *
	 * @return	빈 슬롯 수
	 */
	public int emptySlots() {
		return m_guard.get(() -> m_length - m_deque.size());
	}

	/**
	 * supply 시 호출될 listener를 설정한다.
	 * <p>
	 * listener는 "supply가 일어났다"는 신호만 전달하며 적재된 값은 직접 받지 않는다 — 값이 필요하면
	 * listener 내부에서 {@link #poll()} 또는 {@link #next()}로 직접 꺼내야 한다. listener 호출 시점에
	 * enqueue는 이미 끝나 있으므로 즉시 같은 값을 받아갈 수 있다.
	 * <p>
	 * listener는 supply 호출자 thread에서, <b>본 객체의 lock 밖에서</b> 호출된다 — 따라서 listener
	 * 안에서 본 객체의 메소드를 호출해도 deadlock은 발생하지 않으나, 그 사이에 다른 thread가 큐 상태를
	 * 변경할 수 있음에 유의한다. 여러 producer가 동시에 supply하면 listener는 각 supply마다 한 번씩
	 * 호출되지만, 호출 순서가 큐 적재 순서와 일치한다는 보장은 없다. listener가 예외를 던지면 silent
	 * swallow되며 supply 흐름과 큐 상태에는 영향을 주지 않는다 ({@link Error}는 호출자에게 전파).
	 * <p>
	 * 이미 등록된 listener가 있으면 새 listener로 교체된다.
	 *
	 * @param listener supply 시 호출될 listener. {@code null}이면 기존 listener를 제거한다.
	 */
	public void setSupplyListener(@Nullable Runnable listener) {
		m_guard.run(() -> m_onSupply = listener);
	}

	private void notifySupplyListener(@Nullable Runnable listener) {
		if ( listener != null ) {
			try {
				listener.run();
			}
			catch ( Exception ignored ) {
				// listener 예외는 supply 흐름에 영향을 주지 않도록 swallow한다.
				// Error (OOM, StackOverflow 등)는 catch하지 않고 호출자에게 전파한다.
			}
		}
	}

	/**
	 * 소비자 측에서 스트림을 강제 종료시킨다.
	 * <p>
	 * 호출되면 큐에 남아 있는 모든 미소비 데이터를 즉시 폐기하고, 이후 {@code supply} 호출은
	 * {@link IllegalStateException}을 발생시킨다. {@link #endOfSupply()}와 달리 큐의 잔여 데이터를
	 * 보존하지 않는다.
	 * <p>
	 * 등록된 supply listener는 {@code close()}에 의해 해제되지 않는다. close 이후에는 supply 자체가
	 * 실패하므로 listener는 더 이상 호출되지 않으나, listener 객체가 외부 리소스를 보유하고 있다면
	 * {@code close()} 전에 별도로 {@link #setSupplyListener(Runnable) setSupplyListener(null)}를
	 * 호출해 정리해야 한다.
	 * <p>
	 * 멱등하며, 이미 종료된 경우 두 번째 호출 이후는 무시된다.
	 */
	@Override
	public void close() {
		m_guard.run(() -> {
			if ( !m_closed ) {
				m_closed = true;
				m_deque.clear();
			}
		});
	}

	/**
	 * 스트림의 다음 원소를 반환한다.
	 * <p>
	 * 큐에 원소가 없는 경우는 새 원소가 적재되거나 스트림이 종료될 때까지 호출 쓰레드가 대기한다.
	 * Producer가 {@link #endOfSupply(Throwable)}로 에러 종료시킨 경우는 잔여 데이터가 모두 소비된
	 * 후 등록된 에러를 RuntimeException으로 래핑해 던진다.
	 *
	 * @return	다음 원소. 스트림이 종료되어 더 이상 원소가 없는 경우는 {@link FOption#empty()}.
	 * @throws RuntimeInterruptedException	대기 중 쓰레드가 인터럽트된 경우.
	 */
	@Override
	public FOption<T> next() {
		try {
			return m_guard.awaitCondition(() -> m_deque.size() > 0 || m_closed || m_eos)
						.andGet(() -> {
							if ( m_deque.size() > 0 ) {
								T value = m_deque.removeFirst();
								return FOption.of(value);
							}
							else if ( m_closed || m_eos ) {
								if ( m_error == null ) {
									return FOption.empty();
								}
								else {
									throw Throwables.toRuntimeException(m_error);
								}
							}
							else {
								throw new AssertionError("should not reach here");
							}
						});
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeInterruptedException(e);
		}
	}
	
	/**
	 * 스트림의 다음 원소를 지정된 시간 내에 반환한다.
	 * <p>
	 * 큐에 원소가 없는 경우는 지정된 시간만큼 새 원소가 적재되거나 스트림이 종료될 때까지
	 * 호출 쓰레드가 대기한다. 지정된 시간동안 원소가 적재되지 않고 스트림도 종료되지 않으면
	 * {@link TimeoutException} 예외가 발생된다. Producer가 {@link #endOfSupply(Throwable)}로
	 * 에러 종료시킨 경우는 잔여 데이터가 모두 소비된 후 등록된 에러를 RuntimeException으로
	 * 래핑해 던진다.
	 *
	 * @param timeout	제한 시간
	 * @param tu		제한 시간 단위
	 * @return	다음 원소. 스트림이 종료되어 더 이상 원소가 없는 경우는 {@link FOption#empty()}.
	 * @throws TimeoutException	제한된 시간 동안 스트림이 비어있었던 경우.
	 * @throws RuntimeInterruptedException	대기 중 쓰레드가 인터럽트된 경우.
	 */
	@Override
	public FOption<T> next(long timeout, TimeUnit tu) throws TimeoutException {
		try {
			return m_guard.awaitCondition(() -> m_deque.size() > 0 || m_closed || m_eos, timeout, tu)
						.andGet(() -> {
							if ( m_deque.size() > 0 ) {
								T value = m_deque.removeFirst();
								return FOption.of(value);
							}
							else if ( m_closed || m_eos ) {
								if ( m_error == null ) {
									return FOption.empty();
								}
								else {
									throw Throwables.toRuntimeException(m_error);
								}
							}
							else {
								throw new AssertionError("should not reach here");
							}
						});
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeInterruptedException(e);
		}
	}
	
	/**
	 * 스트림의 다음 원소를 반환한다.
	 * <p>
	 * 원소가 없는 경우는 {@link FOption#empty()}가 반환된다.
	 * 
	 * @return	다음 원소. 스트림이 빈 경우는 {@link FOption#empty()}
	 */
	public FOption<T> poll() {
		return m_guard.get(() -> {
			if ( m_deque.size() > 0 ) {
				return FOption.of(m_deque.removeFirst());
			}
			else if ( m_closed || m_eos ) {
				if ( m_error == null ) {
					return FOption.empty();
				}
				else {
					throw Throwables.toRuntimeException(m_error);
				}
			}
			else {
				return FOption.empty();
			}
		});
	}
	
	/**
	 * 소비자 측에서 {@link #close()}가 호출되어 스트림이 종료되었는지 여부를 반환한다.
	 *
	 * @return	소비자 측이 close된 경우 {@code true}, 그렇지 않은 경우 {@code false}.
	 */
	public boolean isClosed() {
		return m_guard.get(() -> m_closed);
	}

	@Override
	public boolean isEndOfSupply() {
		return m_guard.get(() -> m_eos);
	}
	
	@Override
	public void supply(T data) throws IllegalStateException, InterruptedException {
		Runnable listener = m_guard.awaitCondition(() -> m_deque.size() < m_length || m_closed || m_eos)
				.andGet(() -> {
					if ( m_closed ) {
						throw new IllegalStateException("closed at consumer-side");
					}
					else if ( m_eos ) {
						throw new IllegalStateException("Supplier has closed the stream");
					}

					m_deque.addLast(data);
					return m_onSupply;
				});

		notifySupplyListener(listener);
	}

	@Override
	public void supply(T data, long timeout, TimeUnit tu) throws IllegalStateException, InterruptedException,
																TimeoutException {
		Runnable listener = m_guard.awaitCondition(() -> m_deque.size() < m_length || m_closed || m_eos, timeout, tu)
				.andGet(() -> {
					if ( m_closed ) {
						throw new IllegalStateException("closed at consumer-side");
					}
					else if ( m_eos ) {
						throw new IllegalStateException("Supplier has closed the stream");
					}

					m_deque.addLast(data);
					return m_onSupply;
				});

		notifySupplyListener(listener);
	}

	/**
	 * Supplier로부터 얻은 데이터를 큐에 적재한다.
	 * <p>
	 * 빈 슬롯이 생길 때까지 호출 쓰레드가 대기한다. 대기 사이 supplier 호출이 지연되는 것을
	 * 막기 위해 {@code supplier.get()}은 빈 슬롯이 확보된 직후 lock 안에서 호출된다.
	 * 따라서 supplier는 비교적 빠르게 동작해야 하며, supplier가 예외를 던지면 그 예외가
	 * 호출자에게 그대로 전파되고 큐 상태는 변경되지 않는다.
	 * <p>
	 * <b>주의</b>: supplier는 본 객체의 lock 안에서 실행되므로, supplier 안에서 외부 lock을
	 * 획득하거나 본 객체의 다른 메소드 (다른 thread가 본 객체의 lock을 기다리고 있을 수 있는)
	 * 를 호출하면 deadlock이 발생할 수 있다. supplier는 자신만의 로컬 계산만 수행하도록 유지할 것.
	 *
	 * @param supplier	적재할 값을 공급할 supplier
	 * @return	실제 큐에 적재된 값 (supplier가 반환한 값)
	 * @throws IllegalStateException	이미 close되었거나 endOfSupply된 경우.
	 * @throws InterruptedException	대기 중 쓰레드가 인터럽트된 경우.
	 */
	public T supply(Supplier<T> supplier) throws IllegalStateException, InterruptedException {
		Runnable[] listenerHolder = new Runnable[1];
		T value = m_guard.awaitCondition(() -> m_deque.size() < m_length || m_closed || m_eos)
				.andGet(() -> {
					if ( m_closed ) {
						throw new IllegalStateException("closed at consumer-side");
					}
					else if ( m_eos ) {
						throw new IllegalStateException("Supplier has closed the stream");
					}

					T v = supplier.get();
					m_deque.addLast(v);
					listenerHolder[0] = m_onSupply;
					return v;
				});

		notifySupplyListener(listenerHolder[0]);
		return value;
	}

	@Override
	public void endOfSupply() {
		m_guard.run(() -> {
			if ( !m_eos ) {
				m_eos = true;
			}
		});
	}

	@Override
	public void endOfSupply(Throwable error) throws IllegalArgumentException {
		Preconditions.checkNotNullArgument(error, "error must not be null");
		
		m_guard.run(() -> {
			if ( !m_eos ) {
				m_eos = true;
				m_error = error;
			}
		});
	}
	
	@Override
	public String toString() {
		String state = m_guard.get(() -> String.format("size=%d, closed=%s, eos=%s",
														m_deque.size(), m_closed, m_eos));
		return String.format("%s[%s]", getClass().getSimpleName(), state);
	}
}
