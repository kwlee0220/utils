package utils;

import com.google.common.base.Objects;

/**
 * 값과 그 인덱스를 함께 보관하는 불변(immutable) 페어.
 *
 * @param <T> 값의 타입
 * @author Kang-Woo Lee (ETRI)
 */
public final class Indexed<T> {
	private final int m_index;
	private final T m_value;
	
	public static <T> Indexed<T> with(T data, int index) {
		return new Indexed<>(data, index);
	}
	
	private Indexed(T data, int index) {
		m_index = index;
		m_value = data;
	}
	
	public int index() {
		return m_index;
	}
	
	public T value() {
		return m_value;
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(m_index, m_value);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null  || obj.getClass() != Indexed.class ) {
			return false;
		}
		
		Indexed<?> other = (Indexed<?>)obj;
		return m_index == other.m_index && Objects.equal(m_value, other.m_value);
	}
	
	@Override
	public String toString() {
		return String.format("%s(%d)", m_value, m_index);
	}
}
