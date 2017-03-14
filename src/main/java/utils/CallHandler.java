package utils;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import net.sf.cglib.proxy.MethodProxy;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface CallHandler<T> extends Predicate<Method> {
	public Object intercept(T baseObject, Method method, Object[] args, MethodProxy proxy)
		throws Throwable;

}
