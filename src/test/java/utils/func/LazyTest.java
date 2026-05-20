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
		Lazy<String> lazy = Lazy.of("hello");
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
		assertThrows(IllegalArgumentException.class, () -> Lazy.of((String)null));
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
		lazy.unload();
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
		lazy.unload();
		assertEquals(Integer.valueOf(2), lazy.get());
		assertEquals(2, callCount.get());
	}

	@Test
	public void testGetThrowsAfterUnloadOnValueLazy() {
		Lazy<String> lazy = Lazy.of("init");
		lazy.unload();
		assertThrows(IllegalStateException.class, lazy::get);
	}

	@Test
	public void testGetWithSupplierReturningNullKeepsRetrying() {
		AtomicInteger callCount = new AtomicInteger(0);
		Lazy<String> lazy = Lazy.of(() -> {
			callCount.incrementAndGet();
			return null;
		});

		assertNull(lazy.get());
		assertNull(lazy.get());
		assertNull(lazy.get());
		// supplier가 매번 다시 호출되어야 한다 (null은 "적재 안 됨"으로 취급).
		assertEquals(3, callCount.get());
		assertFalse(lazy.isLoaded());
	}

	// ----- set -----

	@Test
	public void testSetReplacesValueAndReturnsPrevious() {
		Lazy<String> lazy = Lazy.of("a");
		String prev = lazy.set("b");
		assertEquals("a", prev);
		assertEquals("b", lazy.get());
	}

	@Test
	public void testSetReturnsNullWhenUnloaded() {
		Lazy<String> lazy = Lazy.of(() -> "lazy-value");
		// 첫 get 전에 set
		String prev = lazy.set("explicit");
		assertNull(prev);
		assertEquals("explicit", lazy.get());
	}

	@Test
	public void testSetReturnsNullAfterUnload() {
		Lazy<String> lazy = Lazy.of("init");
		lazy.unload();
		String prev = lazy.set("new");
		assertNull(prev);
		assertEquals("new", lazy.get());
	}

	@Test
	public void testSetNullRejected() {
		Lazy<String> lazy = Lazy.of("init");
		assertThrows(IllegalArgumentException.class, () -> lazy.set(null));
	}

	@Test
	public void testSetRehydratesUnloadedValueLazy() {
		// 값-Lazy를 unload한 뒤 set으로 다시 채우면 get()이 정상 동작해야 한다.
		Lazy<String> lazy = Lazy.of("init");
		lazy.unload();
		lazy.set("rehydrated");
		assertTrue(lazy.isLoaded());
		assertEquals("rehydrated", lazy.get());
	}

	// ----- unload -----

	@Test
	public void testUnloadClearsValue() {
		Lazy<String> lazy = Lazy.of("x");
		lazy.unload();
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadIsIdempotent() {
		Lazy<String> lazy = Lazy.of("x");
		lazy.unload();
		lazy.unload();   // 예외 없이 통과해야 한다.
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadWithDtorCallsDtor() {
		List<String> destroyed = new ArrayList<>();
		Lazy<String> lazy = Lazy.of("alive");
		lazy.unload(destroyed::add);

		assertEquals(1, destroyed.size());
		assertEquals("alive", destroyed.get(0));
		assertFalse(lazy.isLoaded());
	}

	@Test
	public void testUnloadWithDtorSkipsWhenNotLoaded() {
		AtomicInteger called = new AtomicInteger(0);
		Lazy<String> lazy = Lazy.of(() -> "value");

		lazy.unload(v -> called.incrementAndGet());

		assertEquals(0, called.get());
	}

	@Test
	public void testUnloadWithDtorPropagatesExceptionAfterUnloading() {
		Lazy<String> lazy = Lazy.of("x");
		RuntimeException ex = assertThrows(RuntimeException.class,
											() -> lazy.unload(v -> { throw new RuntimeException("boom"); }),
											"dtor 예외가 전파되어야 한다");
		assertEquals("boom", ex.getMessage());
		// 예외가 던져졌더라도 unload는 이미 적용되어야 한다.
		assertFalse(lazy.isLoaded());
	}

	// ----- toString -----

	@Test
	public void testToStringWhenLoaded() {
		Lazy<Integer> lazy = Lazy.of(42);
		assertEquals("Lazy[42]", lazy.toString());
	}

	@Test
	public void testToStringWhenUnloaded() {
		Lazy<Integer> lazy = Lazy.of(() -> 1);
		assertEquals("Lazy[unloaded]", lazy.toString());
		lazy.get();
		assertEquals("Lazy[1]", lazy.toString());
		lazy.unload();
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

	@Test
	public void testWrapInterfaceMethodFromSuperInterfaceTriggersSupplier() {
		// 인터페이스 메소드(super-interface에서 상속된 것 포함)는 supplier를 호출하고 캐시로 위임된다.
		AtomicInteger called = new AtomicInteger(0);
		Greet g = Lazy.wrap(() -> {
			called.incrementAndGet();
			return new Greet() {
				@Override public String hello() { return "hi"; }
				@Override public String bye() { return "bye"; }
			};
		}, Greet.class);

		assertEquals("hi", g.hello());
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
	 * wrap 의 Object 메소드(toString/hashCode/equals) 동작을 관찰한다.
	 * <p>
	 * Lazy 클래스 javadoc 은 "Object 메소드는 supplier를 호출하지 않고 프록시의 super(=Object)
	 * 구현으로 처리된다"고 명시하고 있다. 그러나 현재 Interceptor의 조건
	 * {@code declaring.isAssignableFrom(m_intfc)}은 Object에 대해서도 true 이므로
	 * 실제로는 supplier가 호출되고 backing 객체의 Object 메소드로 위임된다.
	 * 본 테스트는 javadoc 약속대로 동작하는지 (= supplier 미호출) 검증한다.
	 * 현재 구현에서는 실패할 가능성이 있으며, 그 경우 코드 또는 javadoc 중 하나를 수정해야 한다.
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
}
