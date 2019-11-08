package utils.func;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Lazy<T> {
	private FOption<T> m_loaded;
	private final Supplier<T> m_supplier;
	
	public static <T> Lazy<T> of(Supplier<T> supplier) {
		return new Lazy<>(supplier);
	}
	
	public static <T> Lazy<T> of(T value) {
		return new Lazy<>(value);
	}
	
	private Lazy(T value) {
		m_loaded = FOption.of(value);
		m_supplier = null;
	}
	
	private Lazy(Supplier<T> supplier) {
		m_loaded = FOption.empty();
		m_supplier = supplier;
	}
	
	public synchronized boolean isLoaded() {
		return m_loaded.isPresent();
	}
	
	public synchronized T get() {
		if ( m_loaded.isAbsent() ) {
			m_loaded = FOption.of(m_supplier.get());
		}
		
		return m_loaded.get();
	}
}
