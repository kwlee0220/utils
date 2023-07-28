package utils;

import com.google.common.base.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Indexed<T> {
	private final int m_index;
	private final T m_data;
	
	public static <T> Indexed<T> with(T data, int index) {
		return new Indexed<>(index, data);
	}
	
	private Indexed(int index, T data) {
		m_index = index;
		m_data = data;
	}
	
	public int getIndex() {
		return m_index;
	}
	
	public T getData() {
		return m_data;
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(m_index, m_data);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null  || obj.getClass() != Indexed.class ) {
			return false;
		}
		
		Indexed<T> other = (Indexed)obj;
		return m_index == other.m_index && m_data.equals(other.m_data);
	}
	
	@Override
	public String toString() {
		return String.format("%d:%s", m_index, m_data);
	}
}
