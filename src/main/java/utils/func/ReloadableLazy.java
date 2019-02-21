package utils.func;

import java.util.function.Supplier;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReloadableLazy<T> {
	private Option<T> m_loaded;
	private Supplier<T> m_supplier;
	
	public static <T> ReloadableLazy<T> of(Supplier<T> supplier) {
		return new ReloadableLazy<>(supplier);
	}
	
	private ReloadableLazy(Supplier<T> supplier) {
		m_loaded = Option.none();
		m_supplier = supplier;
	}
	
	public synchronized boolean isLoaded() {
		return m_loaded.isDefined();
	}
	
	public synchronized T get() {
		if ( m_loaded.isEmpty() ) {
			m_loaded = Option.some(m_supplier.get());
		}
		
		return m_loaded.get();
	}
	
	public synchronized void set(T value) {
		m_loaded = Option.some(value);
	}
	
	public synchronized void invalidate() {
		m_loaded = Option.none();
	}
}
