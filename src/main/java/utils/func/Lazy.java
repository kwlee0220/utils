package utils.func;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Lazy<T> {
	private final AtomicReference<FOption<T>> m_ref;
	private final Supplier<? extends T> m_supplier;
	
	/**
     * 주어진 {@link Supplier}를 이용하여 적재된 값을 생성하는 {@link Lazy} 인스턴스를 생성한다.
     * 
     * @param supplier	적재된 값을 생성하는 {@link Supplier}
     * @return	{@link Lazy} 인스턴스.
     */
	public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
		return new Lazy<>(supplier);
	}
	
	/**
	 * 주어진 값으로 적재된 {@link Lazy} 인스턴스를 생성한다.
	 * 
	 * @param value 적재된 값
	 * @return {@link Lazy} 인스턴스.
	 */
	public static <T> Lazy<T> of(T value) {
		return new Lazy<>(value);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T wrap(Supplier<? extends T> supplier, Class<T> intfc) {
		Enhancer enhancer = new Enhancer();
		enhancer.setClassLoader(supplier.getClass().getClassLoader());
		enhancer.setInterfaces(new Class[] {intfc});
		enhancer.setCallback(new Interceptor(supplier, intfc));
		return (T)enhancer.create();
	}
	
	private Lazy(T value) {
		m_ref = new AtomicReference<>(FOption.of(value));
		m_supplier = null;
	}
	
	private Lazy(Supplier<? extends T> supplier) {
		m_ref = new AtomicReference<>();
		m_supplier = supplier;
	}
	
	/**
	 * 적재된 값이 있는지 여부를 반환한다.
	 *
	 * @return	적재된 값이 있으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLoaded() {
		return m_ref.getAcquire().isPresent();
	}
	
	/**
	 * 적재된 값을 반환한다.
	 * <p>
	 * 적재된 값이 없으면 주어진 {@link Supplier}를 이용하여 값을 적재한 후 반환한다.
	 *
	 * @return	적재된 값.
	 */
	public T get() {
		FOption<T> wrapped = m_ref.get();
		if ( wrapped == null ) {
			synchronized ( m_ref ) {
				wrapped = FOption.of(m_supplier.get());
				if ( !m_ref.compareAndSet(null, wrapped) ) {
					wrapped = m_ref.get();
				}
			}
		}
		return wrapped.get();
	}
	
	/**
	 * 주어진 값을 적재시키고, 이전 값을 {@link FOption} 형태로 반환한다.
	 * <p>
	 * 설정된 값이 없었으면 {@link FOption#empty()}를 반환한다.
	 *
	 * @param value	설정할 값
	 * @return	이전 값. 설정된 값이 없었으면 {@link FOption#empty()} 반환.
	 */
	public FOption<T> set(T value) {
        return m_ref.getAndSet(FOption.of(value));
	}
	
	/**
	 * 현재	적재된 값을 제거한다.
	 */
	public void unload() {
		m_ref.set(null);
	}
	
	/**
	 * 현재 적재된 값을 제거하고, 주어진 소멸자를 이용하여 제거된 값을 소멸시킨다.
	 *
	 * @param dtor 소멸자
	 */
	public void unload(Consumer<T> dtor) {
		FOption<T> wrapped = m_ref.getAndSet(null);
		if ( wrapped != null ) {
			dtor.accept(wrapped.get());
		}
	}
	
	@Override
	public String toString() {
		FOption<T> wrapped = m_ref.get();
		String vstr = ( wrapped != null ) ? ""+wrapped.get() : "unloaded";
		
		return String.format("Lazy[%s]", vstr);
	}

	private static class Interceptor<T> implements MethodInterceptor {
		private final Supplier<T> m_supplier;
		private final Class<T> m_intfc;
		private T m_obj = null;

		Interceptor(Supplier<T> supplier, Class<T> intfc) {
			m_supplier = supplier;
			m_intfc = intfc;
		}

		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
			throws Throwable {
			Class<?> declaring = method.getDeclaringClass();

			if ( m_intfc == declaring || declaring.isAssignableFrom(m_intfc) ) {
				if ( m_obj == null ) {
					m_obj = m_supplier.get();
				}
				
				return method.invoke(m_obj, args);
			}
			else {
				return proxy.invokeSuper(obj, args);
			}
		}
	}
}
