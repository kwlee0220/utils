package utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
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

	@SuppressWarnings("unchecked")
	public static <T> T replaceAction(T obj, CallHandler handler) {
		Preconditions.checkNotNullArgument(obj, "target object is null");
		Preconditions.checkNotNullArgument(handler, "CallHandler is null");

		Callback[] callbacks = List.of(new NoOpHandler<>(obj), new Interceptor<>(obj, handler))
									.toArray(new Callback[2]);

		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setClassLoader(obj.getClass().getClassLoader());
			enhancer.setSuperclass(obj.getClass());
			enhancer.setCallbackFilter(new CallFilter(handler));
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
	 * {@link #replaceAction(Object, CallHandler)}의 ByteBuddy 기반 구현이다.
	 * <p>
	 * cglib 대신 ByteBuddy로 동적 서브클래스를 생성하므로, JDK 9+의 강한 캡슐화 환경에서
	 * {@code --add-opens=java.base/java.lang=ALL-UNNAMED} 옵션 없이도 동작한다.
	 * <p>
	 * {@code handler.test(method)}가 {@code true}인 메소드는 {@code handler}가 처리하고, 그 외의
	 * 메소드는 원본 객체 {@code obj}로 위임된다.
	 * <p>
	 * 단, cglib의 {@code MethodProxy}를 제공하지 않으므로 {@code handler}의
	 * {@link CallHandler#intercept}에 전달되는 네 번째 인자({@code MethodProxy})는 항상 {@code null}이다.
	 * (원본 메소드로의 위임은 프록시가 직접 수행하므로 핸들러에서 별도 위임이 필요 없다.)
	 * <p>
	 * 대상 클래스에 접근 가능한 public 기본 생성자가 있으면 해당 클래스를 상속한 프록시를 생성하고,
	 * (JDBC {@code ResultSet} 구현체처럼) 기본 생성자가 없거나 {@code final}인 경우에는 대상 클래스가
	 * 구현한 모든 인터페이스를 구현하는 프록시로 폴백한다. 후자의 경우 반환 객체는 구체 클래스 타입이
	 * 아니라 인터페이스 타입으로만 사용할 수 있다.
	 *
	 * @param <T>		대상 객체 타입.
	 * @param obj		동작을 교체할 원본 객체.
	 * @param handler	교체할 동작을 정의하는 {@link CallHandler}.
	 * @return 동작이 교체된 프록시 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T replaceActionByteBuddy(T obj, CallHandler handler) {
		Preconditions.checkNotNullArgument(obj, "target object is null");
		Preconditions.checkNotNullArgument(handler, "CallHandler is null");

		Class<?> targetCls = obj.getClass();
		try {
			DynamicType.Builder<?> builder;
			if ( hasPublicDefaultConstructor(targetCls) ) {
				builder = new ByteBuddy().subclass(targetCls);
			}
			else {
				// 기본 생성자가 없거나 final인 클래스(JDBC ResultSet 구현 등)는 인터페이스 기반으로 프록시한다.
				Set<Class<?>> intfcSet = Sets.newHashSet(ReflectionUtils.getAllInterfaces(targetCls));
				// 다른 클래스로더/패키지에서 구현할 수 없는 비공개 인터페이스는 제외한다.
				intfcSet.removeIf(intfc -> !Modifier.isPublic(intfc.getModifiers()));
				Preconditions.checkState(!intfcSet.isEmpty(),
						"Cannot proxy a class without a public default constructor nor public interfaces: %s",
						targetCls.getName());
				builder = new ByteBuddy().subclass(Object.class)
										.implement(intfcSet.toArray(new Class<?>[intfcSet.size()]));
			}

			Class<?> proxyCls = builder
					.method(ElementMatchers.isPublic()
											.and(ElementMatchers.not(ElementMatchers.isStatic()))
											.and(ElementMatchers.not(ElementMatchers.isFinal())))
					.intercept(MethodDelegation.to(new ByteBuddyDispatcher(obj, handler)))
					.make()
					.load(targetCls.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
					.getLoaded();
			return (T)proxyCls.getDeclaredConstructor().newInstance();
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			Throwables.sneakyThrow(cause);

			throw new AssertionError("should not be here");
		}
	}

	private static boolean hasPublicDefaultConstructor(Class<?> cls) {
		if ( Modifier.isFinal(cls.getModifiers()) ) {
			return false;
		}
		try {
			Constructor<?> ctor = cls.getDeclaredConstructor();
			return Modifier.isPublic(ctor.getModifiers());
		}
		catch ( NoSuchMethodException e ) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T replaceAction(T obj, Class<?> intfc, CallHandler... handler) {
		Preconditions.checkNotNullArgument(obj, "target object is null");
		Preconditions.checkNotNullArgument(handler, "CallHandler is null");

		Callback[] callbacks = new Callback[handler.length+1];
		callbacks[0] = new NoOpHandler<>(obj);
        for ( int i =0; i < handler.length; ++i ) {
        	callbacks[i+1] = new Interceptor<>(obj, handler[i]);
        }
		Class<?>[] intfcs = new Class<?>[] { intfc };

		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setClassLoader(obj.getClass().getClassLoader());
			enhancer.setInterfaces(intfcs);
			enhancer.setCallbackFilter(new CallFilter(handler));
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
		Preconditions.checkNotNullArgument(base, "base object is null");
		Preconditions.checkNotNullArgument(handlers, "CallHandler is null");
		Preconditions.checkArgument(handlers.length > 0, "Zero CallHandler" );

		Callback[] callbacks = new Callback[handlers.length+1];
		for ( int i =0; i < handlers.length; ++i ) {
			callbacks[i+1] = handlers[i];
		}
		callbacks[0] = new NoOpHandler<>(base);

		Set<Class<?>> intfcSet = Sets.newHashSet(ReflectionUtils.getAllInterfaces(base.getClass()));
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
	 * @param baseCls   확장될 객체의 클래스.
	 * @param intfc		추가될 인터페이스. 인터페이스 클래스만 사용 가능하다.
	 * @param handler	확장 인터페이스 호출을 처리할 핸들러 객체.
	 * @return	확장된 인터페이스의 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T extend(Class<?> baseCls, Class<T> intfc, T handler) {
		Set<Class<?>> intfcSet = Sets.newHashSet(ReflectionUtils.getAllInterfaces(baseCls));
		intfcSet.add(intfc);
		Class<?>[] intfcs = intfcSet.toArray(new Class<?>[intfcSet.size()]);
		
		try {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(baseCls);
			enhancer.setInterfaces(intfcs);
			enhancer.setCallback(new ExtendedCallHandler<>(intfc, handler));
			return (T)enhancer.create();
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			String msg = String.format("Failed to extend the object: baseClass=%s, intfc=%s, handler=%s",
										baseCls, intfc, handler);
			throw new InternalException(msg, cause);
		}
	}
				
	private static class CallFilter implements CallbackFilter {
		private final CallHandler[] m_handlers;
		
		CallFilter(CallHandler... handlers) {
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

	/**
	 * {@link #replaceActionByteBuddy(Object, CallHandler)}가 생성하는 ByteBuddy 프록시의 메소드 호출을
	 * 처리하는 디스패처이다. 매칭되는 메소드는 {@link CallHandler}로, 그 외는 원본 객체로 위임한다.
	 */
	public static class ByteBuddyDispatcher {
		private final Object m_target;
		private final CallHandler m_handler;

		ByteBuddyDispatcher(Object target, CallHandler handler) {
			m_target = target;
			m_handler = handler;
		}

		@RuntimeType
		public Object intercept(@This Object self, @Origin Method method, @AllArguments Object[] args)
			throws Throwable {
			if ( m_handler.test(method) ) {
				// ByteBuddy 프록시에는 cglib MethodProxy가 없으므로 null을 전달한다.
				return m_handler.intercept(self, method, args, null);
			}
			else {
				try {
					return method.invoke(m_target, args);
				}
				catch ( InvocationTargetException e ) {
					throw e.getTargetException();
				}
			}
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
		private final Class<T> m_intfc;
		private final T m_handler;

		ExtendedCallHandler(Class<T> intfc, T handler) {
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
				return proxy.invokeSuper(obj, args);
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
