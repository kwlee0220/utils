package utils;

import com.google.common.base.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Timestamped<T> {
	private final long m_ts;
	private final T m_data;
	
	public static <T> Timestamped<T> of(T data, long ts) {
		return new Timestamped<>(ts, data);
	}
	
	public static <T> Timestamped<T> of(T data) {
		return new Timestamped<>(System.currentTimeMillis(), data);
	}
	
	private Timestamped(long ts, T data) {
		m_ts = ts;
		m_data = data;
	}
	
	public long timestamp() {
		return m_ts;
	}
	
	public T value() {
		return m_data;
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(m_ts, m_data);
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
		return m_ts == other.m_ts && m_data.equals(other.m_data);
	}
	
	@Override
	public String toString() {
		return String.format("%s(%s)", m_data, m_ts);
	}
}
