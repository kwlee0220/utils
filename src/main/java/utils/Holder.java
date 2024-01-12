package utils;

import java.io.Serializable;
import java.util.function.Function;

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
	
	private Holder(T value) {
		m_value = value;
	}
	
	public T get() {
		return m_value;
	}
	
	public Holder<T> set(T value) {
		m_value = value;
		return this;
	}
	
	public Holder<T> update(Function<? super T,? extends T> func) {
		m_value = func.apply(m_value);
		return this;
	}
	
	@Override
	public String toString() {
		return (m_value != null)
					? String.format("Holder:{%s}", m_value)
					: "Holder:{}";
	}
}
