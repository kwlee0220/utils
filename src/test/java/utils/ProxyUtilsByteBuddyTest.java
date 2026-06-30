package utils;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.sf.cglib.proxy.MethodProxy;

public class ProxyUtilsByteBuddyTest {
	// --- 클래스 기반 (public 기본 생성자 보유) ---
	public static class Sample {
		public String greet() { return "real-greet"; }
		public String other() { return "real-other"; }
	}

	@Test
	public void classBased_replacesMatchedAndForwardsOthers() {
		Sample sample = new Sample();
		CallHandler handler = handlerFor("greet", "proxied-greet");

		Sample proxy = ProxyUtils.replaceActionByteBuddy(sample, handler);
		Assertions.assertTrue(proxy instanceof Sample);
		Assertions.assertEquals("proxied-greet", proxy.greet());
		Assertions.assertEquals("real-other", proxy.other());
	}

	// --- 인터페이스 기반 폴백 (no-arg 생성자 없음, ResultSet 시나리오) ---
	public interface Service {
		String run();
		String name();
	}
	public static class ServiceImpl implements Service {
		private final String m_id;
		public ServiceImpl(String id) { m_id = id; }	// 기본 생성자 없음
		@Override public String run() { return "real-run-" + m_id; }
		@Override public String name() { return "real-name-" + m_id; }
	}

	@Test
	public void interfaceBased_replacesMatchedAndForwardsOthers() {
		Service svc = new ServiceImpl("x");
		CallHandler handler = handlerFor("run", "proxied-run");

		Service proxy = ProxyUtils.replaceActionByteBuddy(svc, handler);
		Assertions.assertTrue(proxy instanceof Service);
		Assertions.assertEquals("proxied-run", proxy.run());          // 매칭 → 핸들러
		Assertions.assertEquals("real-name-x", proxy.name());         // 그 외 → 원본 위임
	}

	private static CallHandler handlerFor(String methodName, String replaced) {
		return new CallHandler() {
			@Override public boolean test(Method m) { return m.getName().equals(methodName); }
			@Override public Object intercept(Object obj, Method m, Object[] args, MethodProxy proxy) {
				return replaced;
			}
		};
	}
}
