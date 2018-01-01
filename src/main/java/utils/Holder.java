package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Holder<T> {
	private T m_value;
	
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
