package utils.func;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 값의 lazy initialization에 TTL(time-to-live)을 더한 thread-safe wrapper.
 * <p>
 * {@link #get()} 호출 시 적재된 값의 적재 시점으로부터 TTL이 경과했으면 supplier를 다시 호출하여
 * 값을 재적재한 후 반환하고, 그렇지 않으면 기존 값을 그대로 반환한다. 적재된 값이 한 번도 없는
 * 경우에도 supplier를 호출하여 적재한다.
 * <p>
 * <b>Thread-safety:</b> 모든 메소드는 thread-safe하다. 재적재가 필요한 시점에 다수의 스레드가
 * 동시에 {@link #get()}을 호출하면 supplier가 여러 번 호출될 수 있으므로 (재적재는 직렬화되지만
 * 중복 호출은 차단하지 않음), supplier는 idempotent한 것이 권장된다.
 * <p>
 * {@link #peek()} / {@link #peekUnchecked()} 는 supplier 호출 없이 현재 적재된 값을 그대로 반환하므로
 * 부수효과·고비용을 피하고 싶은 상황(예: {@code toString()})에서 유용하다. {@link #set(Object)}로
 * 외부에서 받은 값을 캐시에 직접 주입할 수 있으며, 이 경우 적재 시각도 같이 갱신된다.
 *
 * @param <T> 적재되는 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimedLazy<T> {
	private final Duration m_ttl;
	private final Supplier<? extends T> m_supplier;
	@Nullable private volatile Holder<T> m_holder;

	private record Holder<T>(T value, Instant loadedAt) {}

	/**
	 * 주어진 TTL과 {@link Supplier}로 {@link TimedLazy} 인스턴스를 생성한다.
	 *
	 * @param <T>      적재되는 값의 타입.
	 * @param ttl      적재된 값의 유효 기간. 양수이어야 한다.
	 * @param supplier 값을 적재하는 {@link Supplier}.
	 * @return         {@link TimedLazy} 인스턴스.
	 * @throws IllegalArgumentException {@code ttl}이 {@code null} 또는 0 이하이거나,
	 *                                  {@code supplier}가 {@code null}인 경우.
	 */
	public static <T> TimedLazy<T> of(Duration ttl, Supplier<? extends T> supplier) {
		return new TimedLazy<>(ttl, supplier);
	}

	private TimedLazy(Duration ttl, Supplier<? extends T> supplier) {
		Preconditions.checkNotNullArgument(ttl, "ttl is null");
		Preconditions.checkArgument(!ttl.isNegative() && !ttl.isZero(), "ttl must be positive: ttl=%s", ttl);
		Preconditions.checkNotNullArgument(supplier, "supplier is null");

		m_ttl = ttl;
		m_supplier = supplier;
		m_holder = null;
	}

	/**
	 * TTL을 반환한다.
	 *
	 * @return 적재된 값의 유효 기간.
	 */
	public Duration getTtl() {
		return m_ttl;
	}

	/**
	 * 적재된 값이 있는지 여부를 반환한다.
	 * <p>
	 * TTL 만료 여부와 무관하게 한 번이라도 적재된 값이 남아 있으면 {@code true}를 반환한다.
	 * 만료 여부까지 같이 확인하려면 {@link #isExpired()}를 함께 사용한다.
	 *
	 * @return 적재된 값이 있으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLoaded() {
		return m_holder != null;
	}

	/**
	 * 현재 적재된 값이 TTL을 초과하여 만료되었는지 여부를 반환한다.
	 *
	 * @return 적재된 값이 없거나 TTL을 초과한 경우 {@code true},
	 *         아직 유효한 값이 적재되어 있으면 {@code false}.
	 */
	public boolean isExpired() {
		Holder<T> h = m_holder;
		return h == null || isExpired(h, Instant.now());
	}

	/**
	 * 유효한 적재 값을 반환한다.
	 * <p>
	 * 적재된 값이 없거나 TTL이 만료된 경우 supplier를 호출하여 값을 재적재한 후 반환한다.
	 * 다수의 스레드가 동시에 호출하여 재적재가 필요한 시점에도 supplier는 한 번만 호출된다.
	 *
	 * @return 적재된 값. supplier가 {@code null}을 반환한 경우 {@code null}이 적재되며 그대로 반환된다
	 *         (이 경우 다음 호출 시 TTL이 만료되기 전까지는 supplier를 다시 호출하지 않는다).
	 */
	@Nullable
	public T get() {
		Holder<T> h = m_holder;
		Instant now = Instant.now();
		if ( h != null && !isExpired(h, now) ) {
			return h.value();
		}
		return reload();
	}

	/**
	 * 현재 적재된 값을 반환한다. TTL 만료 여부와 무관하게 supplier를 호출하지 않는다.
	 * <p>
	 * 적재된 값이 없는 경우 빈 {@link Optional}을 반환한다.
	 *
	 * @return 현재 적재된 값을 담은 {@link Optional}. 적재된 값이 없으면 {@link Optional#empty()}.
	 */
	public Optional<T> peek() {
		Holder<T> h = m_holder;
		return ( h != null ) ? Optional.ofNullable(h.value()) : Optional.empty();
	}

	/**
	 * 현재 적재된 값을 그대로 반환한다. TTL 만료 여부와 무관하게 supplier를 호출하지 않는다.
	 *
	 * @return 현재 적재된 값. 적재된 값이 없으면 {@code null}.
	 */
	@Nullable
	public T peekUnchecked() {
		Holder<T> h = m_holder;
		return ( h != null ) ? h.value() : null;
	}

	/**
	 * 주어진 값을 캐시에 직접 적재한다. 적재 시각은 현재 시각으로 갱신된다.
	 * <p>
	 * 외부에서 이미 최신 값을 알고 있어 supplier 호출을 생략하고 싶을 때 사용한다.
	 *
	 * @param value 적재할 값.
	 */
	public void set(@Nullable T value) {
		m_holder = new Holder<>(value, Instant.now());
	}

	/**
	 * 현재 적재된 값을 제거한다.
	 * <p>
	 * 이후 {@link #get()} 호출 시 supplier를 통해 값이 다시 적재된다.
	 */
	public void invalidate() {
		m_holder = null;
	}

	@Override
	public String toString() {
		Holder<T> h = m_holder;
		if ( h == null ) {
			return "TimedLazy[unloaded]";
		}
		String state = isExpired(h, Instant.now()) ? "expired" : "fresh";
		return String.format("TimedLazy[%s, %s]", state, h.value());
	}

	/**
	 * TTL과 무관하게 supplier를 즉시 호출하여 값을 재적재한 뒤 반환한다.
	 * <p>
	 * 적재된 값이 만료되지 않은 상태에서도 강제로 새 값을 가져와야 하는 경우 (예: 외부 상태가
	 * 바뀐 직후 최신 값으로 동기화) 사용한다. 재적재 후 적재 시각은 현재 시각으로 갱신된다.
	 *
	 * @return supplier로부터 새로 적재한 값.
	 */
	@Nullable
	public synchronized T reload() {
		T value = m_supplier.get();
		m_holder = new Holder<>(value, Instant.now());
		return value;
	}

	private boolean isExpired(Holder<T> h, Instant now) {
		return Duration.between(h.loadedAt(), now).compareTo(m_ttl) > 0;
	}
}
