package utils;

import static utils.Utilities.checkArgument;
import static utils.Utilities.checkNotNullArgument;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;


/**
 *
 * @author Kang-Woo Lee
 */
public final class ProxyUtils {
	private ProxyUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + ProxyUtils.class.getName());
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	public static <T> T replaceAction(ClassLoader loader, T obj, CallHandler... handlers) {
		checkNotNullArgument(loader, "ClassLoader is null");
		checkNotNullArgument(obj, "target object is null");
		checkNotNullArgument(handlers, "CallHandler is null");
		checkArgument(handlers.length > 0, "Zero CallHandler" );
		
		Callback[] callbacks = new Callback[handlers.length+1];
		for ( int i =0; i < handlers.length; ++i ) {
			callbacks[i+1] = new Interceptor<>(obj, handlers[i]);
		}
		callbacks[0] = new NoOpHandler<>(obj);
		
		Set<Class<?>> intfcSet = Utilities.getInterfaceAllRecusively(obj.getClass());
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		checkArgument(intfcs.length > 0, () -> "object implements no interfaces: obj=" + obj);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setClassLoader(loader);
			enhancer.setInterfaces(intfcs);
			enhancer.setCallbackFilter(new CallFilter(handlers));
			enhancer.setCallbacks(callbacks);
			return (T)enhancer.create();
		}
		catch ( CodeGenerationException e ) {
			Throwable cause = Throwables.unwrapThrowable(e.getCause());
			Throwables.sneakyThrow(cause);
			
			throw new AssertionError("should not be here");
		}
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	public static <T> T replaceAction(T obj, CallHandler... handlers) {
		checkNotNullArgument(obj, "target object is null");
		checkNotNullArgument(handlers, "CallHandler is null");
		checkArgument(handlers.length > 0, "Zero CallHandler" );
		
		Callback[] callbacks = new Callback[handlers.length+1];
		for ( int i =0; i < handlers.length; ++i ) {
			callbacks[i+1] = new Interceptor<>(obj, handlers[i]);
		}
		callbacks[0] = new NoOpHandler<>(obj);

		Set<Class<?>> intfcSet = Utilities.getInterfaceAllRecusively(obj.getClass());
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		checkArgument(intfcs.length > 0, () -> "object implements no interfaces: obj=" + obj);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setInterfaces(intfcs);
			enhancer.setCallbackFilter(new CallFilter(handlers));
			enhancer.setCallbacks(callbacks);
			return (T)enhancer.create();
		}
		catch ( CodeGenerationException e ) {
			Throwable cause = Throwables.unwrapThrowable(e.getCause());
			Throwables.sneakyThrow(cause);
			
			throw new AssertionError("should not be here");
		}
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	public static <T> T replaceAction(T obj, Class<?>[] extraIntfcs, CallHandler... handlers) {
		checkNotNullArgument(obj, "target object is null");
		checkNotNullArgument(handlers, "CallHandler is null");
		checkArgument(handlers.length > 0, "Zero CallHandler" );
		
		Callback[] callbacks = new Callback[handlers.length+1];
		for ( int i =0; i < handlers.length; ++i ) {
			callbacks[i+1] = new Interceptor<>(obj, handlers[i]);
		}
		callbacks[0] = new NoOpHandler<>(obj);
		
		Set<Class<?>> intfcSet = Sets.newHashSet(Utilities.getInterfaceAllRecusively(obj.getClass()));
		intfcSet.addAll(Arrays.asList(extraIntfcs));
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setInterfaces(intfcs);
			enhancer.setCallbackFilter(new CallFilter(handlers));
			enhancer.setCallbacks(callbacks);
			return (T)enhancer.create();
		}
		catch ( CodeGenerationException e ) {
			Throwable cause = Throwables.unwrapThrowable(e.getCause());
			Throwables.sneakyThrow(cause);
			
			throw new AssertionError("should not be here");
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T buildObject(Object base, Class<?>[] extraIntfcs, CallHandler[] handlers,
									Class<T> outIntfc) {
		Preconditions.checkArgument(base != null, "target object is null");
		Preconditions.checkArgument(handlers != null, "CallHandler is null");
		Preconditions.checkArgument(handlers.length > 0, "Zero CallHandler" );
		
		Callback[] callbacks = new Callback[handlers.length+1];
		for ( int i =0; i < handlers.length; ++i ) {
			callbacks[i+1] = handlers[i];
		}
		callbacks[0] = new NoOpHandler<>(base);
		
		Set<Class<?>> intfcSet = Sets.newHashSet(Utilities.getInterfaceAllRecusively(base.getClass()));
		intfcSet.addAll(Arrays.asList(extraIntfcs));
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setInterfaces(intfcs);
			enhancer.setCallbackFilter(new CallFilter(handlers));
			enhancer.setCallbacks(callbacks);
			return (T)enhancer.create();
		}
		catch ( CodeGenerationException e ) {
			Throwable cause = Throwables.unwrapThrowable(e.getCause());
			Throwables.sneakyThrow(cause);
			
			throw new AssertionError("should not be here");
		}
	}

	/**
	 * 주어진 객체(<code>toBeExtended</code>)를 확장하여 추가의 인터페이스(<code>intfc</code>)도
	 * 지원하는 객체를 생성하여 반환한다.
	 * <p>
	 * 반환되는 객체는 확장 인터페이스도 지원하기 때문에 <code>instanceof</code> 관계도 성립되어,
	 * 인자로 주어진 객체가 제공하는 모든 메소드를 처리할 뿐만 아니라 확장 인터페이스의 메소드도
	 * 호출 가능하다. 확장된 인터페이스의 메소드가 호출되는 경우는 인자로 전달되는 핸들러 객체의 메소드를 
	 * 호출하게 되고, 그외의 메소드는 기존 객체의 메소드를 호출하게 된다.
	 * <br>
	 * 만일 확장 인터페이스를 기존의 객체가 이미 지원하는 경우, 해당 인터페이스의 메소드가 호출되는
	 * 경우는 확장 핸들러 객체의 메소드가 호출된다.
	 * 
	 * @param <T>		확장된 객체의 대표 타입.
	 * @param cloader	생성된 확장 객체의 클래스를 적재할 클래스 로더. <code>null</code>인 경우는
	 * 					<code>toBeExtended</code> 객체의 클래스 로더를 사용한다. 
	 * @param src		확장시킬 객체.
	 * @param intfc		추가될 인터페이스. 인터페이스 클래스만 사용 가능하다.
	 * @param handler	확장 인터페이스 호출을 처리할 핸들러 객체.
	 * @return	확장된 인터페이스의 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T extend(ClassLoader cloader, Object src, Class<T> intfc, T handler) {
		Set<Class<?>> intfcSet = Utilities.getInterfaceAllRecusively(src.getClass());
		intfcSet.add(intfc);
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(src.getClass());
			enhancer.setInterfaces(intfcs);
			enhancer.setCallback(new ExtendedCallHandler<>(src, intfc, handler));
			return (T)enhancer.create();
		}
		catch ( CodeGenerationException e ) {
			Throwable cause = Throwables.unwrapThrowable(e.getCause());
			Throwables.sneakyThrow(cause);
			
			throw new AssertionError("should not be here");
		}
	}
				
	private static class CallFilter implements CallbackFilter {
		private final CallHandler[] m_handlers;
		
		CallFilter(CallHandler[] handlers) {
			m_handlers = handlers;
		}
		
		@Override
		public int accept(Method method) {
			for ( int i =0; i < m_handlers.length; ++i ) {
				if ( m_handlers[i].test(method) ) {
					return i+1;
				}
			}
			
			return 0;
		}
	}
	
	private static class NoOpHandler<T> implements MethodInterceptor {
		private T m_object;
		
		NoOpHandler(T object) {
			m_object = object;
		}

		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
			throws Throwable {
			return proxy.invoke(m_object, args);
		}
	}
	
	private static class Interceptor<T> implements MethodInterceptor {
		private CallHandler m_interceptor;
		
		Interceptor(T object, CallHandler interceptor) {
			m_interceptor = interceptor;
		}

		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
			throws Throwable {
			return m_interceptor.intercept(obj, method, args, proxy);
		}
	}
	
//	static class ReplaceHandler implements InvocationHandler {
//		private final Object m_object;
//		private final Predicate<Method> m_tester;
//		private final InvocationHandler m_replacer;
//		
//		ReplaceHandler(Object object, Predicate<Method> tester, InvocationHandler replacer) {
//			m_object = object;
//			m_tester = tester;
//			m_replacer = replacer;
//		}
//
//		@Override
//		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//			if ( m_tester != null && m_tester.test(method) ) {
//				return m_replacer.invoke(proxy, method, args);
//			}
//			else {
//				return method.invoke(m_object, args);
//			}
//		}
//	}

	private static class ExtendedCallHandler<T> implements MethodInterceptor {
		private final Object m_src;
		private final Class<T> m_intfc;
		private final T m_handler;

		ExtendedCallHandler(Object orgObj, Class<T> intfc, T handler) {
			m_src = orgObj;
			m_intfc = intfc;
			m_handler = handler;
		}

		@Override
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
			throws Throwable {
			Class<?> declaring = method.getDeclaringClass();

			if ( m_intfc == declaring || declaring.isAssignableFrom(m_intfc) ) {
				return method.invoke(m_handler, args);
			}
			else {
				return proxy.invokeSuper(m_src, args);
			}
		}
	}

	/**
	 * 주어진 객체(<code>toBeExtended</code>)를 확장하여 추가의 인터페이스(<code>intfc</code>)도
	 * 지원하는 객체를 생성하여 반환한다.
	 * <p>
	 * 반환되는 객체는 확장 인터페이스도 지원하기 때문에 <code>instanceof</code> 관계도 성립되어,
	 * 인자로 주어진 객체가 제공하는 모든 메소드를 처리할 뿐만 아니라 확장 인터페이스의 메소드도
	 * 호출 가능하다. 확장된 인터페이스의 메소드가 호출되는 경우는 인자로 전달되는 핸들러 객체의 메소드를 
	 * 호출하게 되고, 그외의 메소드는 기존 객체의 메소드를 호출하게 된다.
	 * <br>
	 * 만일 확장 인터페이스를 기존의 객체가 이미 지원하는 경우, 해당 인터페이스의 메소드가 호출되는
	 * 경우는 확장 핸들러 객체의 메소드가 호출된다.
	 * 
	 * @param loader	생성된 확장 객체의 클래스를 적재할 클래스 로더. <code>null</code>인 경우는
	 * 					<code>toBeExtended</code> 객체의 클래스 로더를 사용한다. 
	 * @param toBeExtended	확장될 객체.
	 * @param extIntfcs		추가될 인터페이스. intfc는 인터페이스 클래스만 사용 가능하다.
	 * @param handlers	확장 인터페이스 호출을 처리할 핸들러 객체.
	 * @return	확장된 인터페이스의 객체.
	 */
//	public static Object extendObject(ClassLoader loader, Object toBeExtended, Class<?>[] extIntfcs,
//									Object[] handlers) {
//		if ( toBeExtended == null ) {
//			throw new IllegalArgumentException("toBeExtended is null");
//		}
//		if ( extIntfcs == null ) {
//			throw new IllegalArgumentException("extIntfcs is null");
//		}
//		if ( handlers == null ) {
//			throw new IllegalArgumentException("handlers is null");
//		}
//		if ( loader == null ) {
//			loader = toBeExtended.getClass().getClassLoader();
//		}
//
//		Set<Class<?>> intfcSet = Utilities.getInterfaceAllRecusively(toBeExtended.getClass());
//		intfcSet.addAll(Arrays.asList(extIntfcs));
//		
//		return Proxy.newProxyInstance(loader, intfcSet.toArray(new Class<?>[intfcSet.size()]),
//										new ExtendedCallHandler(toBeExtended, extIntfcs, handlers));
//	}
//
//	private static class ExtendedCallHandler implements InvocationHandler {
//		private final Object m_orgObj;
//		private final Class<?>[] m_extIntfcs;
//		private final Object[] m_handlers;
//
//		ExtendedCallHandler(Object orgObj, Class<?>[] extIntfcs, Object[] handlers) {
//			m_orgObj = orgObj;
//			m_extIntfcs = extIntfcs;
//			m_handlers = handlers;
//		}
//
//		@Override
//		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//			Class<?> declaring = method.getDeclaringClass();
//			
//			for ( int i =0; i < m_extIntfcs.length; ++i ) {
//				Class<?> intfc = m_extIntfcs[i];
//
//				if ( intfc == declaring || declaring.isAssignableFrom(intfc) ) {
//					return method.invoke(m_handlers[i], args);
//				}
//			}
//			
//			return method.invoke(m_orgObj, args);
//		}
//	}
//	
//	
//
//	@SuppressWarnings("unchecked")
//	public static <T> T addAction(ClassLoader loader, Object obj, Class<T> intfc,
//									InvocationHandler handler) {
//		if ( obj == null ) {
//			throw new IllegalArgumentException("obj was null");
//		}
//		if ( intfc == null ) {
//			throw new IllegalArgumentException("intfc was null");
//		}
//		if ( handler == null ) {
//			throw new IllegalArgumentException("handler was null");
//		}
//		if ( loader == null ) {
//			loader = obj.getClass().getClassLoader();
//		}
//
//		Set<Class<?>> intfcSet = Utilities.getInterfaceAllRecusively(obj.getClass());
//		if ( !intfcSet.add(intfc) ) {
//			throw new IllegalArgumentException("obj has supported the interface already: obj="
//												+ obj + ", intfc=" + intfc.getName());
//		}
//
//		return (T)Proxy.newProxyInstance(loader, intfcSet.toArray(new Class<?>[intfcSet.size()]),
//										new Handler<T>(obj, intfc, handler));
//	}

//	private static class Handler<T> implements InvocationHandler {
//		private final Object m_obj;
//		private final Class<T> m_intfc;
//		private final InvocationHandler m_handler;
//
//		public Handler(Object obj, Class<T> intfc, InvocationHandler handler) {
//			m_obj = obj;
//			m_intfc = intfc;
//			m_handler = handler;
//		}
//
//		@Override
//		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//			if ( method.getDeclaringClass().isAssignableFrom(m_intfc) ) {
//				return m_handler.invoke(m_obj, method, args);
//			}
//			else {
//				return method.invoke(m_obj, args);
//			}
//		}
//	}
}
