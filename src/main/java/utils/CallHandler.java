package utils;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import net.sf.cglib.proxy.MethodInterceptor;

/**
 * 동적 프록시에서 메소드 호출을 가로채 처리하는 핸들러.
 * <p>
 * {@link Predicate#test(Object)}가 {@code true}를 반환하는 메소드의 호출만
 * {@link MethodInterceptor#intercept(Object, Method, Object[], net.sf.cglib.proxy.MethodProxy)}로 처리하며,
 * 그 밖의 호출은 프록시가 원본 객체로 위임한다. {@link ProxyUtils}에서 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CallHandler extends Predicate<Method>, MethodInterceptor {
}
