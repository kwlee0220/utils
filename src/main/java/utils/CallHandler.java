package utils;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import net.sf.cglib.proxy.MethodInterceptor;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface CallHandler extends Predicate<Method>, MethodInterceptor {
}
