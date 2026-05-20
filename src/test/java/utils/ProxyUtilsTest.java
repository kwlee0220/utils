package utils;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.sf.cglib.proxy.MethodProxy;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProxyUtilsTest {

	public interface Greeter {
		String hello(String name);
		String bye(String name);
	}

	public interface Counter {
		int count();
	}

	public static class DefaultGreeter implements Greeter {
		@Override
		public String hello(String name) {
			return "hello " + name;
		}

		@Override
		public String bye(String name) {
			return "bye " + name;
		}
	}

	private static CallHandler handlerForMethod(final String methodName, final Object replacement) {
		return new CallHandler() {
			@Override
			public boolean test(Method m) {
				return m.getName().equals(methodName);
			}

			@Override
			public Object intercept(Object obj, Method m, Object[] args, MethodProxy proxy)
				throws Throwable {
				return replacement;
			}
		};
	}

	@Test
	public void replaceAction_singleHandler_extendsSuperclass() {
		DefaultGreeter base = new DefaultGreeter();
		DefaultGreeter proxy = ProxyUtils.replaceAction(base, handlerForMethod("hello", "INTERCEPTED"));

		Assertions.assertTrue(proxy instanceof DefaultGreeter);
		Assertions.assertTrue(proxy instanceof Greeter);
		Assertions.assertEquals("INTERCEPTED", proxy.hello("x"));
		Assertions.assertEquals("bye y", proxy.bye("y"));
	}

	@Test
	public void replaceAction_specificInterface_dispatchesByHandler() {
		Greeter base = new DefaultGreeter();
		Greeter proxy = ProxyUtils.replaceAction(base, Greeter.class, handlerForMethod("hello", "X"));

		Assertions.assertTrue(proxy instanceof Greeter);
		Assertions.assertEquals("X", proxy.hello("a"));
		Assertions.assertEquals("bye b", proxy.bye("b"));
	}

	@Test
	public void replaceAction_varargs_unmatchedMethodGoesToOriginal() {
		DefaultGreeter base = new DefaultGreeter();
		Greeter proxy = ProxyUtils.replaceAction(base, handlerForMethod("hello", "X"));

		Assertions.assertEquals("bye y", proxy.bye("y"));
	}

	@Test
	public void buildObject_proxyImplementsBaseAndExtraIntfcs() {
		DefaultGreeter base = new DefaultGreeter();
		Class<?>[] extras = { Counter.class };
		CallHandler counterHandler = new CallHandler() {
			@Override
			public boolean test(Method m) {
				return m.getName().equals("count");
			}

			@Override
			public Object intercept(Object obj, Method m, Object[] args, MethodProxy proxy)
				throws Throwable {
				return 7;
			}
		};

		Object proxy = ProxyUtils.buildObject(base, extras,
												new CallHandler[] { counterHandler },
												Counter.class);

		Assertions.assertTrue(proxy instanceof Greeter);
		Assertions.assertTrue(proxy instanceof Counter);
		Assertions.assertEquals(7, ((Counter)proxy).count());
		Assertions.assertEquals("hello a", ((Greeter)proxy).hello("a"));
	}

	@Test
	public void extend_proxyExtendsClassAndImplementsExtraIntfc() {
		Counter handler = () -> 99;
		Counter proxy = ProxyUtils.extend(DefaultGreeter.class, Counter.class, handler);

		Assertions.assertTrue(proxy instanceof DefaultGreeter);
		Assertions.assertTrue(proxy instanceof Counter);
		Assertions.assertEquals(99, proxy.count());
		Assertions.assertEquals("hello z", ((DefaultGreeter)proxy).hello("z"));
	}

	@Test
	public void replaceAction_nullObj_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Object nullObj = null;
			ProxyUtils.replaceAction(nullObj, handlerForMethod("hello", "x"));
		});
	}
}
