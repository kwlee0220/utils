package utils.func;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReloadableLazy<T> {
	private FOption<T> m_loaded;
	private Supplier<T> m_supplier;
	
	public static <T> ReloadableLazy<T> of(Supplier<T> supplier) {
		return new ReloadableLazy<>(supplier);
	}
	
	private ReloadableLazy(Supplier<T> supplier) {
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
	
	public synchronized void set(T value) {
		m_loaded = FOption.of(value);
	}
	
	public synchronized void invalidate() {
		m_loaded = FOption.empty();
	}
}
