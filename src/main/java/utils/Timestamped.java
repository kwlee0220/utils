package utils;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * 값과 그 타임스탬프(milliseconds)를 함께 보관하는 불변(immutable) 페어.
 *
 * @param <T> 값의 타입
 * @author Kang-Woo Lee (ETRI)
 */
public final class Timestamped<T> {
	private final long m_ts;
	private @Nullable final T m_value;

	/**
	 * 값과 타임스탬프로 {@link Timestamped}를 만든다.
	 *
	 * @param <T>	값의 타입.
	 * @param data	값. {@code null}을 허용한다.
	 * @param ts	타임스탬프(milliseconds).
	 * @return	새 {@link Timestamped} 객체.
	 */
	public static <T> Timestamped<T> of(T data, long ts) {
		return new Timestamped<>(data, ts);
	}

	/**
	 * 값과 현재 시각({@link System#currentTimeMillis()})으로 {@link Timestamped}를 만든다.
	 *
	 * @param <T>	값의 타입.
	 * @param data	값. {@code null}을 허용한다.
	 * @return	새 {@link Timestamped} 객체.
	 */
	public static <T> Timestamped<T> of(T data) {
		return new Timestamped<>(data, System.currentTimeMillis());
	}

	private Timestamped(T data, long ts) {
		m_ts = ts;
		m_value = data;
	}

	/**
	 * 타임스탬프를 반환한다.
	 *
	 * @return	타임스탬프(milliseconds).
	 */
	public long timestamp() {
		return m_ts;
	}

	/**
	 * 값을 반환한다.
	 *
	 * @return	값. {@code null}일 수 있다.
	 */
	public @Nullable T value() {
		return m_value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(m_ts, m_value);
	}

	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null  || obj.getClass() != Timestamped.class ) {
			return false;
		}

		Timestamped<?> other = (Timestamped<?>)obj;
		return m_ts == other.m_ts && Objects.equals(m_value, other.m_value);
	}

	@Override
	public String toString() {
		return String.format("%s(%d)", m_value, m_ts);
	}
}
