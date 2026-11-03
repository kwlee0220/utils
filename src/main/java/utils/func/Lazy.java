package utils.func;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;



/**
 * 값의 lazy initialization을 위한 thread-safe wrapper.
 * <p>
 * 두 가지 생성 모드를 지원한다.
 * <ul>
 *   <li>{@link #of(Supplier)} — supplier 기반. 첫 {@link #get()} 호출 시 값이 적재된다.
 *   <li>{@link #ofValue(Object)} — 값 기반. 생성 시점에 이미 적재된 상태이며 {@code value}는 non-null 이어야 한다.
 * </ul>
 * 적재된 값은 {@link #invalidate()} / {@link #invalidate(Consumer)} 로 비울 수 있고, {@link #set(Object)} 로 직접
 * 교체할 수도 있다. Supplier 기반 인스턴스는 invalidate 후 재호출 시 supplier로부터 재적재되지만,
 * 값 기반 인스턴스는 invalidate 후 {@link #get()} 호출 시 {@link IllegalStateException}이 발생한다.
 * <p>
 * <b>Thread-safety:</b> 모든 메소드는 thread-safe하다. 다만 supplier 기반의 {@link #get()}은 경합 시
 * "compute-discard" 패턴으로 동작하여 supplier가 두 번 이상 호출될 수 있다(첫 성공한 결과만 적재). 따라서
 * supplier는 idempotent한 것이 권장되며, 외부 자원 할당 등 부작용이 있는 supplier는 호출자가 직접 보호해야 한다.
 * <p>
 * 동적 프록시 변형이 필요하면 {@link #wrap(Supplier, Class)} 참조.
 *
 * @param <T> 적재되는 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Lazy<T> {
	private final AtomicReference<T> m_ref;
	@Nullable private final Supplier<? extends T> m_supplier;
	
	/**
	 * 주어진 {@link Supplier}를 이용하여 적재된 값을 생성하는 {@link Lazy} 인스턴스를 생성한다.
	 *
	 * @param <T>      적재되는 값의 타입.
	 * @param supplier 적재된 값을 생성하는 {@link Supplier}.
	 *                 {@code null}이면 {@link IllegalArgumentException}이 발생한다.
	 * @return         {@link Lazy} 인스턴스.
	 */
	public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
		return new Lazy<>(supplier);
	}

	/**
	 * 주어진 값으로 적재된 {@link Lazy} 인스턴스를 생성한다.
	 *
	 * @param <T>   적재되는 값의 타입.
	 * @param value 적재된 값.
	 *              {@code null}이면 {@link IllegalArgumentException}이 발생한다.
	 * @return      {@link Lazy} 인스턴스.
	 */
	public static <T> Lazy<T> ofValue(T value) {
		Preconditions.checkNotNullArgument(value, "value is null");
		return new Lazy<>(value);
	}
	
	/**
	 * 주어진 인터페이스를 구현하는 lazy 동적 프록시를 생성한다.
	 * <p>
	 * 반환된 프록시 객체에서 {@code intfc} 타입에 선언된 메소드가 처음으로 호출되는 시점에
	 * {@code supplier}가 호출되어 실제 백킹 객체가 생성되고, 이후 모든 인터페이스 메소드 호출은
	 * 그 백킹 객체로 위임된다.
	 * <p>
	 * 메소드 디스패치 동작은 다음과 같다.
	 * <ul>
	 *   <li><b>{@code intfc}와 그 상위 인터페이스에 선언된 메소드</b>(default 메소드 포함) — supplier 호출 후 backing
	 *       객체로 위임된다. backing 메소드가 던지는 예외는 {@link InvocationTargetException} 으로 감싸지지 않고
	 *       그대로 전파된다.</li>
	 *   <li><b>Object 메소드</b> — supplier를 호출하지 않는다. {@code equals}는 프록시 동일성({@code ==}),
	 *       {@code hashCode}는 {@link System#identityHashCode(Object)}이며, {@code toString}은 내부 {@link Lazy}의
	 *       표현({@code "Lazy[unloaded]"} 또는 {@code "Lazy[<value>]"})을 반환한다.</li>
	 * </ul>
	 * 백킹 객체의 동시성 안전은 내부 {@link Lazy} 인스턴스에 위임된다 — 경합 시 supplier가 두 번 이상
	 * 호출될 수 있으므로 idempotent한 것이 권장된다.
	 * <p>
	 * 구현은 JDK 동적 프록시({@link Proxy})이므로 {@code intfc}는 인터페이스여야 한다. 프록시 클래스는
	 * {@code intfc}의 classloader에 정의되며 인터페이스별로 재사용된다. public이 아닌 인터페이스(다른 패키지에 선언된
	 * 것 포함)도 감쌀 수 있다. 이때는 reflection 접근 허용({@link Method#trySetAccessible()})이 필요하므로, 모듈 경계 등으로
	 * 허용할 수 없으면 {@code wrap}이 {@link IllegalArgumentException}을 던진다.
	 *
	 * @param <T>      프록시가 구현할 인터페이스 타입.
	 * @param supplier 백킹 객체를 생성하는 supplier. {@code null}이면 안 된다.
	 * @param intfc    프록시가 구현할 인터페이스 클래스. {@code null}이면 안 된다.
	 * @return         {@code intfc}를 구현하는 lazy 프록시 객체.
	 * @throws IllegalArgumentException	{@code supplier} 또는 {@code intfc}가 {@code null}이거나, {@code intfc}가
	 * 									인터페이스가 아닌 경우.
	 */
	public static <T> T wrap(Supplier<? extends T> supplier, Class<T> intfc) {
		Preconditions.checkNotNullArgument(supplier, "supplier is null");
		Preconditions.checkNotNullArgument(intfc, "interface is null");
		Preconditions.checkArgument(intfc.isInterface(), "not an interface: %s", intfc);
		if ( !isPublicType(intfc) ) {
			// public이 아닌 인터페이스의 메소드는 이 패키지에서 바로 호출할 수 없으므로 접근 허용이 가능한지 미리 확인한다.
			for ( Method m: intfc.getMethods() ) {
				Preconditions.checkArgument(isPublicType(m.getDeclaringClass()) || m.trySetAccessible(),
											"cannot access non-public interface method: %s", m);
			}
		}

		Object proxy = Proxy.newProxyInstance(intfc.getClassLoader(), new Class<?>[] { intfc },
												new Interceptor<>(supplier));
		return intfc.cast(proxy);
	}
	
	private Lazy(T value) {
		m_ref = new AtomicReference<>(value);
		m_supplier = null;
	}
	
	private Lazy(Supplier<? extends T> supplier) {
		Preconditions.checkNotNullArgument(supplier, "supplier is null");

		m_ref = new AtomicReference<>();
		m_supplier = supplier;
	}
	
	/**
	 * 적재된 값이 있는지 여부를 반환한다.
	 *
	 * @return	적재된 값이 있으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLoaded() {
		return m_ref.getAcquire() != null;
	}
	
	/**
	 * 적재된 값을 반환한다.
	 * <p>
	 * 적재된 값이 없으면 생성 시 등록된 supplier를 호출하여 값을 적재한 후 반환한다.
	 * 경합 상황에서는 supplier가 두 번 이상 호출될 수 있으며 첫 성공한 결과만 적재된다.
	 * <p>
	 * 값 기반 생성자({@link #ofValue(Object)})로 만든 인스턴스를 {@link #invalidate()} 로 비운 뒤 본 메소드를
	 * 호출하면 supplier가 없으므로 {@link IllegalStateException}이 발생한다.
	 *
	 * @return	적재된 값. {@code null}이 아니다.
	 * @throws IllegalStateException 값이 비어 있고 supplier도 없는 경우, 또는 supplier가 {@code null}을 반환한
	 * 			경우({@code null}은 "적재 안 됨"과 구분할 수 없으므로 적재하지 않는다).
	 */
	public T get() {
		while ( true ) {
			T cached = m_ref.getAcquire();
			if ( cached != null ) {
				return cached;
			}

			Preconditions.checkState(m_supplier != null, "Value supplier is null");
			T loaded = m_supplier.get();
			Preconditions.checkState(loaded != null, "supplier returned null value");
			if ( m_ref.compareAndSet(null, loaded) ) {
				return loaded;
			}
			// 다른 스레드가 먼저 적재했다. 그 사이에 invalidate()로 다시 비었을 수 있으므로 처음부터 다시 확인한다.
		}
	}
	
	/**
	 * 주어진 값을 적재한다.
	 *
	 * @param value	적재할 값. {@code null}이면 {@link IllegalArgumentException}이 발생한다.
	 */
	public void set(T value) {
		Preconditions.checkNotNullArgument(value, "value is null");

		m_ref.set(value);
	}
	
	/**
	 * 현재 적재된 값을 제거한다.
	 */
	public void invalidate() {
		m_ref.set(null);
	}
	
	/**
	 * 현재 적재된 값을 제거하고, 제거된 값에 대해 주어진 소멸자를 호출한다.
	 * <p>
	 * 적재된 값이 없었으면 소멸자는 호출되지 않는다. 소멸자에서 발생한 예외는 호출자에게 전파되며,
	 * 그 시점에 invalidate는 이미 적용되어 값은 비워진 상태이다.
	 *
	 * @param dtor 제거된 값을 소멸시키는 콜백.
	 * @throws IllegalArgumentException	{@code dtor}가 {@code null}인 경우.
	 */
	public void invalidate(Consumer<? super T> dtor) {
		Preconditions.checkNotNullArgument(dtor, "dtor is null");
		T cached = m_ref.getAndSet(null);
		if ( cached != null ) {
			dtor.accept(cached);
		}
	}
	
	@Override
	public String toString() {
		T cached = m_ref.getAcquire();
		String vstr = ( cached != null ) ? ""+cached : "unloaded";
		
		return String.format("Lazy[%s]", vstr);
	}

	// 바깥 클래스까지 모두 public이어야 다른 패키지에서 바로 접근할 수 있다.
	private static boolean isPublicType(Class<?> cls) {
		for ( Class<?> c = cls; c != null; c = c.getEnclosingClass() ) {
			if ( !Modifier.isPublic(c.getModifiers()) ) {
				return false;
			}
		}
		return true;
	}

	private static class Interceptor<T> implements InvocationHandler {
		private final Lazy<T> m_cache;

		Interceptor(Supplier<? extends T> supplier) {
			m_cache = Lazy.of(supplier);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if ( method.getDeclaringClass() == Object.class ) {
				// JDK 프록시는 Object의 메소드 중 equals·hashCode·toString만 이 핸들러로 보낸다.
				switch ( method.getName() ) {
					case "equals": return proxy == args[0];
					case "hashCode": return System.identityHashCode(proxy);
					default: return m_cache.toString();
				}
			}

			if ( !isPublicType(method.getDeclaringClass()) ) {
				// 다른 패키지의 public이 아닌 인터페이스는 접근 허용 없이는 이 핸들러에서 호출할 수 없다.
				// Method 객체는 프록시 클래스가 재사용하므로 한 번 허용하면 유지된다. 가능 여부는 wrap()에서 확인했다.
				method.trySetAccessible();
			}
			try {
				return method.invoke(m_cache.get(), args);
			}
			catch ( IllegalAccessException e ) {
				throw new IllegalStateException("cannot access " + method, e);
			}
			catch ( InvocationTargetException e ) {
				throw e.getCause();
			}
		}
	}
}
