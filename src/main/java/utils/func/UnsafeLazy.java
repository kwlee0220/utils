package utils.func;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;


/**
 * 동기화 없이 값을 지연 적재(lazy loading)하는 클래스이다.
 * <p>
 * {@link #get()}이 처음 호출될 때 {@link Supplier}로 값을 생성하여 보관한다. 동기화하지 않으므로
 * 여러 쓰레드가 동시에 {@code get()}을 호출하면 {@code Supplier}가 여러 번 호출될 수 있다.
 * 내부적으로 {@code null}을 '적재되지 않음' 상태로 사용하므로, {@code Supplier}가 {@code null}을
 * 반환하면 다음 {@code get()} 호출에서 다시 생성을 시도한다.
 *
 * @param <T>	적재되는 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UnsafeLazy<T> {
	private volatile T m_ref;
	private final Supplier<? extends T> m_supplier;
	
	/**
     * 주어진 {@link Supplier}를 이용하여 적재된 값을 생성하는 {@link UnsafeLazy} 인스턴스를 생성한다.
     * 
     * @param <T>	적재되는 값의 타입.
     * @param supplier	적재된 값을 생성하는 {@link Supplier}
     * @return	{@link UnsafeLazy} 인스턴스.
     */
	public static <T> UnsafeLazy<T> of(Supplier<? extends T> supplier) {
		return new UnsafeLazy<>(supplier);
	}
	
	/**
	 * 주어진 값으로 적재된 {@link UnsafeLazy} 인스턴스를 생성한다.
	 * 
	 * <p>
	 * <b>주의:</b> 이렇게 생성된 객체는 {@code Supplier}가 없으므로, {@link #unload()} 후
	 * {@link #get()}을 호출하면 {@link NullPointerException}이 발생한다.
	 * 
	 * @param <T>	적재되는 값의 타입.
	 * @param value 적재된 값
	 * @return {@link UnsafeLazy} 인스턴스.
	 */
	public static <T> UnsafeLazy<T> of(T value) {
		return new UnsafeLazy<>(value);
	}
	
	/**
	 * 주어진 인터페이스를 구현하는 지연 적재 프록시 객체를 생성한다.
	 * <p>
	 * cglib {@code Enhancer}로 {@code intfc}를 구현한 프록시를 만들며, {@code intfc}(또는 그 상위 타입,
	 * {@link Object} 포함)에 선언된 메소드가 처음 호출될 때 {@code supplier}로 실제 객체를 생성하여
	 * 이후 호출을 그 객체에 위임한다. 실제 객체 생성은 동기화되지 않으므로 여러 쓰레드가 동시에 처음
	 * 호출하면 {@code supplier}가 여러 번 호출될 수 있다.
	 *
	 * @param <T>		프록시가 구현할 인터페이스 타입.
	 * @param supplier	실제 객체를 생성하는 {@link Supplier}.
	 * @param intfc		프록시가 구현할 인터페이스.
	 * @return		{@code intfc}를 구현한 프록시 객체.
	 * @throws NullPointerException	{@code supplier}가 {@code null}인 경우.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T wrap(Supplier<? extends T> supplier, Class<T> intfc) {
		Enhancer enhancer = new Enhancer();
		enhancer.setClassLoader(supplier.getClass().getClassLoader());
		enhancer.setInterfaces(new Class[] {intfc});
		enhancer.setCallback(new Interceptor(supplier, intfc));
		return (T)enhancer.create();
	}
	
	private UnsafeLazy(T value) {
		m_ref = value;
		m_supplier = null;
	}
	
	private UnsafeLazy(Supplier<? extends T> supplier) {
		m_ref = null;
		m_supplier = supplier;
	}
	
	/**
	 * 적재된 값이 있는지 여부를 반환한다.
	 *
	 * @return	적재된 값이 있으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLoaded() {
		return m_ref != null;
	}
	
	/**
	 * 적재된 값을 반환한다.
	 * <p>
	 * 적재된 값이 없으면 주어진 {@link Supplier}를 이용하여 값을 적재한 후 반환한다.
	 *
	 * @return	적재된 값.
	 */
	public T get() {
		if ( m_ref == null ) {
			T value = m_supplier.get();
			if ( m_ref == null ) {
				m_ref = value;
			}
		}
		return m_ref;
	}
	
	/**
	 * 주어진 값을 적재시키고, 새로 적재된 값을 그대로 반환한다.
	 * <p>
	 * {@code null}을 지정하면 {@link #unload()}와 같이 적재되지 않은 상태가 된다.
	 *
	 * @param value	설정할 값
	 * @return	새로 적재된 값({@code value}).
	 */
	public T set(T value) {
        return m_ref = value;
	}
	
	/**
	 * 현재	적재된 값을 제거한다.
	 */
	public void unload() {
		m_ref = null;
	}
	
	/**
	 * 현재 적재된 값을 제거하고, 주어진 소멸자를 이용하여 제거된 값을 소멸시킨다.
	 *
	 * @param dtor 소멸자
	 */
	public void unload(Consumer<T> dtor) {
		if ( m_ref != null ) {
			T value = m_ref;
			m_ref = null;
			dtor.accept(value);
		}
	}
	
	@Override
	public String toString() {
		String vstr = ( m_ref != null ) ? ""+m_ref : "unloaded";
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
