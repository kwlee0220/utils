package utils.func;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LazyTest {

	// ----- 생성 -----

	@Test
	public void testOfValue() {
		Lazy<String> lazy = Lazy.ofValue("hello");
		assertTrue(lazy.isLoaded());
		assertEquals("hello", lazy.get());
	}

	@Test
	public void testOfSupplier() {
		Lazy<String> lazy = Lazy.of(() -> "world");
		assertFalse(lazy.isLoaded());
		assertEquals("world", lazy.get());
		assertTrue(lazy.isLoaded());
	}

	@Test
	public void testOfNullValueRejected() {
		assertThrows(IllegalArgumentException.class, () -> Lazy.ofValue((String)null));
	}

	@Test
	public void testOfNullSupplierRejected() {
		assertThrows(IllegalArgumentException.class, () -> Lazy.of((Supplier<String>)null));
	}

	// ----- isLoaded -----

	@Test
	public void testIsLoadedTransitions() {
		Lazy<String> lazy = Lazy.of(() -> "x");
		assertFalse(lazy.isLoaded());
		lazy.get();
		assertTrue(lazy.isLoaded());
		lazy.invalidate();
		assertFalse(lazy.isLoaded());
	}

	// ----- get -----

	@Test
	public void testGetCachesValueAcrossCalls() {
		AtomicInteger callCount = new AtomicInteger(0);
		Lazy<Integer> lazy = Lazy.of(() -> callCount.incrementAndGet());

		assertEquals(Integer.valueOf(1), lazy.get());
		assertEquals(Integer.valueOf(1), lazy.get());
		assertEquals(Integer.valueOf(1), lazy.get());
		assertEquals(1, callCount.get());
	}

	@Test
	public void testGetReloadsAfterUnloadForSupplier() {
		AtomicInteger callCount = new AtomicInteger(0);
		Lazy<Integer> lazy = Lazy.of(() -> callCount.incrementAndGet());

		assertEquals(Integer.valueOf(1), lazy.get());
		lazy.invalidate();
		assertEquals(Integer.valueOf(2), lazy.get());
		assertEquals(2, callCount.get());
	}

	@Test
	public void testGetThrowsAfterUnloadOnValueLazy() {
		Lazy<String> lazy = Lazy.ofValue("init");
		lazy.invalidate();
		assertThrows(IllegalStateException.class, lazy::get);
	}

	@Test
	public void testGetWithSupplierReturningNullFails() {
		AtomicInteger callCount = new AtomicInteger(0);
		Lazy<String> lazy = Lazy.of(() -> {
			callCount.incrementAndGet();
			return null;
		});

		// null은 "적재 안 됨"과 구분할 수 없으므로 적재하지 않고 IllegalStateException을 던진다.
		assertThrows(IllegalStateException.class, lazy::get);
		assertFalse(lazy.isLoaded());
		assertThrows(IllegalStateException.class, lazy::get);
		assertEquals(2, callCount.get());
	}

	// ----- set -----

	@Test
	public void testSetReplacesValue() {
		Lazy<String> lazy = Lazy.ofValue("a");
		lazy.set("b");
		assertEquals("b", lazy.get());
	}

	@Test
	public void testSetBeforeFirstGetSkipsSupplier() {
		Lazy<String> lazy = Lazy.of(() -> "lazy-value");
		// 첫 get 전에 set
		lazy.set("explicit");
		assertEquals("explicit", lazy.get());
	}

	@Test
	public void testSetAfterInvalidate() {
		Lazy<String> lazy = Lazy.ofValue("init");
		lazy.invalidate();
		lazy.set("new");
		assertEquals("new", lazy.get());
	}

	@Test
	public void testSetNullRejected() {
		Lazy<String> lazy = Lazy.ofValue("init");
		assertThrows(IllegalArgumentException.class, () -> lazy.set(null));
	}

	@Test
	public void testSetRehydratesUnloadedValueLazy() {
		// 값-Lazy를 unload한 뒤 set으로 다시 채우면 get()이 정상 동작해야 한다.
		Lazy<String> lazy = Lazy.ofValue("init");
		lazy.invalidate();
		lazy.set("rehydrated");
		assertTrue(lazy.isLoaded());
		assertEquals("rehydrated", lazy.get());
	}

	// ----- unload -----

	@Test
	public void testUnloadClearsValue() {
		Lazy<String> lazy = Lazy.ofValue("x");
		lazy.invalidate();
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadIsIdempotent() {
		Lazy<String> lazy = Lazy.ofValue("x");
		lazy.invalidate();
		lazy.invalidate();   // 예외 없이 통과해야 한다.
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadWithDtorCallsDtor() {
		List<String> destroyed = new ArrayList<>();
		Lazy<String> lazy = Lazy.ofValue("alive");
		lazy.invalidate(destroyed::add);

		assertEquals(1, destroyed.size());
		assertEquals("alive", destroyed.get(0));
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadWithDtorSkipsWhenNotLoaded() {
		AtomicInteger called = new AtomicInteger(0);
		Lazy<String> lazy = Lazy.of(() -> "value");

		lazy.invalidate(v -> called.incrementAndGet());

		assertEquals(0, called.get());
	}

	@Test
	public void testUnloadWithDtorPropagatesExceptionAfterUnloading() {
		Lazy<String> lazy = Lazy.ofValue("x");
		RuntimeException ex = assertThrows(RuntimeException.class,
											() -> lazy.invalidate(v -> { throw new RuntimeException("boom"); }),
											"dtor 예외가 전파되어야 한다");
		assertEquals("boom", ex.getMessage());
		// 예외가 던져졌더라도 unload는 이미 적용되어야 한다.
		assertFalse(lazy.isLoaded());
	}

	// ----- toString -----

	@Test
	public void testToStringWhenLoaded() {
		Lazy<Integer> lazy = Lazy.ofValue(42);
		assertEquals("Lazy[42]", lazy.toString());
	}

	@Test
	public void testToStringWhenUnloaded() {
		Lazy<Integer> lazy = Lazy.of(() -> 1);
		assertEquals("Lazy[unloaded]", lazy.toString());
		lazy.get();
		assertEquals("Lazy[1]", lazy.toString());
		lazy.invalidate();
		assertEquals("Lazy[unloaded]", lazy.toString());
	}

	// ----- 동시성 (compute-discard 패턴) -----

	@Test
	public void testConcurrentGetReturnsConsistentValue() throws Exception {
		// 여러 스레드가 동시에 get() 해도 모두 동일한 값을 받아야 한다.
		// Supplier는 경합 시 두 번 이상 호출될 수 있지만, 첫 성공한 결과만 적재된다.
		final int threadCount = 16;
		AtomicInteger seq = new AtomicInteger(0);
		Lazy<Integer> lazy = Lazy.of(() -> {
			// 각 호출마다 다른 값을 반환 — 적재된 결과가 일관되는지 검증하기 위해.
			try { Thread.sleep(5); } catch ( InterruptedException ignored ) { }
			return seq.incrementAndGet();
		});

		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for ( int i = 0; i < threadCount; ++i ) {
			futures.add(CompletableFuture.supplyAsync(() -> {
				try { start.await(); } catch ( InterruptedException e ) { Thread.currentThread().interrupt(); }
				return lazy.get();
			}));
		}
		start.countDown();

		Integer first = futures.get(0).get(5, TimeUnit.SECONDS);
		for ( int i = 1; i < threadCount; ++i ) {
			Integer v = futures.get(i).get(5, TimeUnit.SECONDS);
			final int idx = i;
			assertEquals(first, v, () -> "스레드 " + idx + "가 다른 값을 받음");
		}
		// 한 번 적재된 후로는 supplier가 호출되지 않으므로 첫 결과 == 최종 적재된 값.
		assertEquals(first, lazy.get());
	}

	// ----- wrap (lazy 동적 프록시) -----

	public interface Greet {
		String hello();
		String bye();
	}

	@Test
	public void testWrapDelaysSupplierUntilFirstCall() {
		AtomicInteger called = new AtomicInteger(0);
		Greet g = Lazy.wrap(() -> {
			called.incrementAndGet();
			return new Greet() {
				@Override public String hello() { return "hi"; }
				@Override public String bye() { return "see ya"; }
			};
		}, Greet.class);

		assertEquals(0, called.get(), "supplier는 메소드 호출 전엔 실행되지 않아야 한다");

		assertEquals("hi", g.hello());
		assertEquals(1, called.get());

		// 두 번째 호출에서는 supplier가 다시 호출되지 않아야 한다.
		assertEquals("see ya", g.bye());
		assertEquals(1, called.get());
	}

	public interface LoudGreet extends Greet {
		String shout();
		default String helloTwice() { return hello() + hello(); }
	}

	@Test
	public void testWrapInterfaceMethodFromSuperInterfaceTriggersSupplier() {
		// 상위 인터페이스(Greet)에서 상속한 메소드와 default 메소드도 backing 객체로 위임된다.
		AtomicInteger called = new AtomicInteger(0);
		LoudGreet g = Lazy.wrap(() -> {
			called.incrementAndGet();
			return new LoudGreet() {
				@Override public String hello() { return "hi"; }
				@Override public String bye() { return "bye"; }
				@Override public String shout() { return "HI"; }
			};
		}, LoudGreet.class);

		assertEquals("hi", g.hello());
		assertEquals("bye", g.bye());
		assertEquals("HI", g.shout());
		assertEquals("hihi", g.helloTwice());
		assertEquals(1, called.get());
	}

	@Test
	public void testWrapNullSupplierRejected() {
		assertThrows(IllegalArgumentException.class, () -> Lazy.wrap(null, Greet.class));
	}

	@Test
	public void testWrapNullInterfaceRejected() {
		assertThrows(IllegalArgumentException.class, () -> Lazy.wrap(() -> null, null));
	}

	@Test
	public void testWrapPropagatesBackingException() {
		// backing 객체의 메소드가 던진 예외는 InvocationTargetException 안에 wrap되지 않고
		// 호출자에게 그대로 전달되어야 한다.
		Greet g = Lazy.wrap(() -> new Greet() {
			@Override public String hello() { throw new IllegalStateException("nope"); }
			@Override public String bye() { return "bye"; }
		}, Greet.class);

		IllegalStateException ex = assertThrows(IllegalStateException.class, g::hello,
												"backing의 IllegalStateException이 전파되어야 한다");
		assertEquals("nope", ex.getMessage());
	}

	@Test
	public void testWrapSupplierCalledOnlyOnceAcrossMultipleCalls() {
		// 첫 호출 후 캐시되어야 하므로 supplier는 한 번만 호출.
		AtomicInteger called = new AtomicInteger(0);
		Greet g = Lazy.wrap(() -> {
			called.incrementAndGet();
			return new Greet() {
				@Override public String hello() { return "hi"; }
				@Override public String bye() { return "bye"; }
			};
		}, Greet.class);

		g.hello();
		g.bye();
		g.hello();
		g.bye();
		assertEquals(1, called.get());
	}

	/**
	 * wrap 의 Object 메소드(toString/hashCode/equals)는 supplier를 호출하지 않는다.
	 */
	@Test
	public void testWrapObjectMethodsDoNotTriggerSupplier() {
		AtomicInteger called = new AtomicInteger(0);
		Greet g = Lazy.wrap(() -> {
			called.incrementAndGet();
			return new Greet() {
				@Override public String hello() { return "hi"; }
				@Override public String bye() { return "bye"; }
			};
		}, Greet.class);

		// Object 메소드 호출 — javadoc에 따르면 supplier가 호출되지 않아야 한다.
		g.toString();
		g.hashCode();
		g.equals(g);

		assertEquals(0, called.get(),
					"Object 메소드만 호출했을 때 supplier는 호출되지 않아야 한다");
	}

	/**
	 * 프록시의 equals는 프록시 동일성, hashCode는 identity hash, toString은 내부 Lazy의 표현이다.
	 */
	@Test
	public void testWrapObjectMethodSemantics() {
		Greet backing = new Greet() {
			@Override public String hello() { return "hi"; }
			@Override public String bye() { return "bye"; }
		};
		Greet g = Lazy.wrap(() -> backing, Greet.class);
		Greet other = Lazy.wrap(() -> backing, Greet.class);

		assertTrue(g.equals(g));
		assertFalse(g.equals(other));
		assertFalse(g.equals(backing));
		assertEquals(System.identityHashCode(g), g.hashCode());
		assertEquals("Lazy[unloaded]", g.toString());
		g.hello();
		assertTrue(g.toString().startsWith("Lazy["));
		assertFalse(g.toString().equals("Lazy[unloaded]"));
	}

	interface PackagePrivateGreet {
		String hello();
	}

	/**
	 * 같은 인터페이스의 프록시 클래스는 재사용되고, public이 아닌 인터페이스도 감쌀 수 있다.
	 */
	@Test
	public void testWrapReusesProxyClassAndAcceptsNonPublicInterface() {
		Supplier<Greet> supplier = () -> new Greet() {
			@Override public String hello() { return "hi"; }
			@Override public String bye() { return "bye"; }
		};
		Class<?> proxyClass = Lazy.wrap(supplier, Greet.class).getClass();
		assertTrue(proxyClass == Lazy.wrap(supplier, Greet.class).getClass());

		PackagePrivateGreet g = Lazy.wrap(() -> () -> "pp", PackagePrivateGreet.class);
		assertEquals("pp", g.hello());
	}

	@Test
	public void testWrapRejectsNonInterface() {
		assertThrows(IllegalArgumentException.class, () -> Lazy.wrap(() -> "x", String.class));
	}

	/**
	 * {@link UnsafeLazy}는 {@link Lazy}와 같은 API를 가진다(동기화만 하지 않는다).
	 */
	@Test
	public void testUnsafeLazyMirrorsLazy() {
		AtomicInteger calls = new AtomicInteger();
		UnsafeLazy<String> lazy = UnsafeLazy.of(() -> "v" + calls.incrementAndGet());
		assertFalse(lazy.isLoaded());
		assertEquals("v1", lazy.get());
		assertEquals("v1", lazy.get());
		lazy.invalidate();
		assertEquals("v2", lazy.get());
		lazy.set("x");
		assertEquals("x", lazy.get());

		List<String> destroyed = new ArrayList<>();
		lazy.invalidate(destroyed::add);
		assertEquals(List.of("x"), destroyed);

		UnsafeLazy<String> value = UnsafeLazy.ofValue("a");
		value.invalidate();
		assertThrows(IllegalStateException.class, value::get);
		assertThrows(IllegalStateException.class, () -> UnsafeLazy.of(() -> null).get());
	}
}
