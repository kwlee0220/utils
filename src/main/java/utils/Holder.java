package utils;

import java.io.Serializable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Holder<T> implements Serializable {
	private static final long serialVersionUID = -102726106391846640L;
	
	private transient T m_value;
	
	public static final <T> Holder<T> of(T value) {
		return new Holder<>(value);
	}
	
	public Holder() {
		m_value = null;
	}
	
	public Holder(T value) {
		m_value = value;
	}
	
	public T get() {
		return m_value;
	}
	
	public Holder<T> set(T value) {
		m_value = value;
		return this;
	}
	
	@Override
	public String toString() {
		return (m_value != null)
					? String.format("Holder:{%s}", m_value)
					: "Holder:{}";
	}
}
