package utils.func;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;


/**
 * {@code ReloadableLazy}는 지연 초기화(lazy initialization)된 값을 저장하고,
 * 필요에 따라 값을 다시 로드할 수 있는 기능을 제공하는 클래스이다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ReloadableLazy<T> {
	@Nullable private T m_loaded;	// 적재되지 않은 경우 null
	private final Supplier<T> m_supplier;
	
	/**
	 * 지정된 공급자(supplier)를 사용하여 {@code ReloadableLazy} 인스턴스를 생성한다.
	 * <p>
	 * {@link #get()} 메서드가 처음 호출될 때 공급자에서 값을 {@code supplier}를	통해
	 * 획득하여 저장하며, 이후에는 저장된 값을 반환한다.
	 * 
	 * @param supplier 값을 제공하는 공급자
	 * @return {@code ReloadableLazy} 인스턴스
	 */
	public static <T> ReloadableLazy<T> of(Supplier<T> supplier) {
		return new ReloadableLazy<>(supplier);
	}
	
	private ReloadableLazy(Supplier<T> supplier) {
		Preconditions.checkNotNull(supplier, "supplier is null");
		
		m_loaded = null;
		m_supplier = supplier;
	}
	
	/**
	 * 현재 값이 로드되었는지 여부를 반환한다.
	 * 
	 * @return 값이 로드된 경우에 {@code true}, 그렇지 않은 경우에 {@code false}
	 */
	public synchronized boolean isLoaded() {
		return m_loaded != null;
	}
	
	/**
	 * 현재 저장된 값을 반환한다.
	 * <p>
	 * 만약 값이 아직 로드되지 않은 경우에는 공급자에서 값을 획득하여 저장한 후 반환한다.
	 * 
	 * @return 현재 저장된 값
	 */
	public synchronized T get() {
		if ( m_loaded == null ) {
			m_loaded = m_supplier.get();
			Preconditions.checkState(m_loaded != null, "supplier returned null value");
		}
		
		return m_loaded;
	}
	
	/**
	 * 지정된 값으로 현재 저장된 값을 설정한다.
	 * 
	 * @param value 새로 설정할 값
	 */
	public synchronized void set(T value) {
		Preconditions.checkNotNull(value, "value is null");
		m_loaded = value;
	}
	
	/**
	 * 현재 저장된 값을 무효화(invalidate)한다.
	 * <p>
	 * 이후 {@link #get()} 메서드가 호출되면 공급자에서 값을 다시 획득하여 저장한다.
	 */
	public synchronized void invalidate() {
		m_loaded = null;
	}
}
