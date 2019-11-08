package utils.func;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T wrap(Supplier<? extends T> supplier, Class<T> intfc) {
		Enhancer enhancer = new Enhancer();
		enhancer.setClassLoader(supplier.getClass().getClassLoader());
		enhancer.setInterfaces(new Class[] {intfc});
		enhancer.setCallback(new Interceptor(supplier, intfc));
		return (T)enhancer.create();
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
