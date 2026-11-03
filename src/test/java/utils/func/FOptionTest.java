package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FOptionTest {
	private String m_str;

	@BeforeEach
	public void setUp() {
		m_str = null;
	}

	@Test
	public void test2() throws Exception {
		FOption<String> opt = FOption.empty();

		opt.ifAbsentOrThrow(() -> m_str = "a");
		assertEquals("a", m_str);

		assertThrows(IllegalArgumentException.class,
						() -> opt.ifAbsentOrThrow(() -> { throw new IllegalArgumentException(); }));
	}

	@Test
	public void test3() throws Exception {
		FOption<String> opt = FOption.of("z");

		opt.ifPresentOrThrow(v -> m_str = v);
		assertEquals("z", m_str);

		assertThrows(IllegalArgumentException.class,
						() -> opt.ifPresentOrThrow(v -> { throw new IllegalArgumentException(); }));
	}

	@Test
	public void test4() throws Exception {
		@SuppressWarnings("unchecked")
		Supplier<String> suppl = mock(Supplier.class);
		when(suppl.get()).thenReturn("b");

		FStream<String> strm;

		strm = FOption.<String>empty().fstream();
		assertEquals(true, strm.next().isAbsent());

		strm = FOption.of("a").fstream();

		FOption<String> next = strm.next();
		assertEquals(true, next.isPresent());
		assertEquals("a", next.get());
		assertEquals("a", next.getOrNull());
		assertEquals("a", next.getOrElse("b"));
		assertEquals("a", next.getOrElse(suppl));
		verify(suppl, times(0)).get();

		next = strm.next();
		assertTrue(next.isAbsent());
		assertEquals(null, next.getOrNull());
		assertEquals("b", next.getOrElse("b"));

		assertEquals("b", next.getOrElse(suppl));
		verify(suppl, times(1)).get();
	}

	@Test
	public void test5() throws Exception {
		FOption<String> opt = FOption.ofNullable("a");
		assertEquals(true, opt.isPresent());
		assertEquals("a", opt.get());
		assertEquals("a", opt.getOrNull());

		opt.ifPresent(v -> m_str = v);
		assertEquals("a", m_str);

		FOption<String> opt2 = FOption.ofNullable(null);
		assertEquals(true, opt2.isAbsent());
		assertThrows(NoSuchValueException.class, opt2::get);
	}

	@Test
	public void testFromOptional() throws Exception {
		Optional<String> opt2 = Optional.of("abc");
		FOption<String> opt = FOption.from(opt2);
		assertEquals(true, opt.isPresent());

		FOption<String> opt3 = FOption.from(Optional.<String>empty());
		assertEquals(true, opt3.isAbsent());
	}

	@Test
	public void testWhen1() throws Exception {
		Optional<String> opt = Nullables.whenTrue(true, "a");
		assertEquals(true, opt.isPresent());
		assertEquals("a", opt.get());

		opt = Nullables.whenTrue(false, "a");
		assertEquals(true, opt.isEmpty());
	}

	@Test
	public void testNarrow() throws Exception {
		ArrayList<String> list = new ArrayList<>();
		FOption<ArrayList<String>> opt = FOption.of(list);
		FOption<List<String>> opt2 = FOption.narrow(opt);

		assertEquals(list, opt2.get());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetOrElseThrow() throws Exception {
		CheckedSupplierX<String,IllegalArgumentException> suppl1 = mock(CheckedSupplierX.class);
		when(suppl1.get()).thenReturn("b");
		CheckedSupplierX<String,IllegalArgumentException> suppl2 = mock(CheckedSupplierX.class);
		when(suppl2.get()).thenThrow(RuntimeException.class);

		FOption<String> opt1 = FOption.of("a");
		FOption<String> opt2 = FOption.empty();

		assertEquals("a", opt1.getOrElseThrow(suppl1));
		verify(suppl1, times(0)).get();
		assertEquals("a", opt1.getOrElseThrow(suppl2));
		verify(suppl2, times(0)).get();

		assertEquals("b", opt2.getOrElseThrow(suppl1));
		verify(suppl1, times(1)).get();
		assertThrows(RuntimeException.class, () -> opt2.getOrElseThrow(suppl2));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetOrThrow() throws RuntimeException {
		Supplier<RuntimeException> suppl = mock(Supplier.class);
		when(suppl.get()).thenThrow(RuntimeException.class);

		FOption<String> opt1 = FOption.of("a");
		FOption<String> opt2 = FOption.empty();

		assertEquals("a", opt1.getOrThrow(suppl));
		verify(suppl, times(0)).get();

		assertThrows(RuntimeException.class, () -> opt2.getOrThrow(suppl));
	}

	@Test
	public void testIfAbsent() {
		Runnable task = mock(Runnable.class);

		FOption<String> opt1 = FOption.of("a");
		FOption<String> opt2 = FOption.empty();

		opt1.ifAbsent(task);
		verify(task, times(0)).run();

		opt2.ifAbsent(task);
		verify(task, times(1)).run();
	}

	@Test
	public void testFilter() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		assertThrows(IllegalArgumentException.class, () -> opt1.filter(null));

		opt = opt1.filter(v -> true);
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());

		opt = opt1.filter(v -> false);
		assertTrue(opt.isAbsent());

		assertThrows(IllegalArgumentException.class, () -> opt2.filter(null));

		opt = opt2.filter(v -> true);
		assertTrue(opt.isAbsent());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testTest() throws Throwable {
		Predicate<Integer> pred1 = mock(Predicate.class);
		when(pred1.test(anyInt())).thenReturn(true);
		Predicate<Integer> pred2 = mock(Predicate.class);
		when(pred2.test(anyInt())).thenReturn(false);

		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		assertTrue(opt1.test(pred1));
		verify(pred1, times(1)).test(anyInt());

		assertFalse(opt1.test(pred2));
		verify(pred2, times(1)).test(anyInt());

		assertFalse(opt2.test(pred1));
		verify(pred1, times(1)).test(anyInt());
	}

	@Test
	public void testMap() throws Throwable {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt3 = opt1.map(v -> v * 2);
		assertTrue(opt3.isPresent());
		assertEquals(20, (int)opt3.get());

		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		assertTrue(opt4.isAbsent());
	}

	@Test
	public void testMapSneakily() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt3 = opt1.mapSneakily(v -> v * 2);
		assertTrue(opt3.isPresent());
		assertEquals(20, (int)opt3.get());

		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		assertTrue(opt4.isAbsent());

		assertThrows(IOException.class, () -> opt1.mapSneakily(v -> { throw new IOException(); }));
	}

	@Test
	public void testMapOrThrow() throws IOException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt3 = opt1.mapOrThrow(v -> v * 2);
		assertTrue(opt3.isPresent());
		assertEquals(20, (int)opt3.get());

		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		assertTrue(opt4.isAbsent());

		assertThrows(IOException.class, () -> opt1.mapOrThrow(v -> { throw new IOException(); }));
	}

	@Test
	public void testTransform() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		int ret = opt1.fold(5, (s,v) -> s+v);
		assertEquals(15, ret);

		int ret2 = opt2.fold(5, (s,v) -> s+v);
		assertEquals(5, ret2);
	}

	@Test
	public void testFlatMap() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt1.flatMap(v -> FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());

		opt = opt1.flatMap(v -> FOption.empty());
		assertTrue(opt.isAbsent());

		opt = opt2.flatMap(v -> FOption.empty());
		assertTrue(opt.isAbsent());
	}

	@Test
	public void testFlatmap2() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt3 = FOption.of(5);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt
			= opt1.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());

		opt = opt3.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		assertTrue(opt.isAbsent());

		opt = opt2.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		assertTrue(opt.isPresent());
		assertEquals(0, (int)opt.get());
	}

	@Test
	public void testFlatMapSneakily() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt2.flatMapSneakily(v -> FOption.empty());
		assertTrue(opt.isAbsent());

		opt = opt2.flatMapSneakily(v -> { throw new IOException(); });
		assertTrue(opt.isAbsent());

		opt = opt1.flatMapSneakily(v -> FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());

		opt = opt1.flatMapSneakily(v -> FOption.empty());
		assertTrue(opt.isAbsent());

		assertThrows(IOException.class, () -> opt1.mapSneakily(v -> { throw new IOException(); }));
	}

	@Test
	public void testOrElse1() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt1.orElse(5);
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());

		opt = opt2.orElse(5);
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());
	}

	@Test
	public void testOrElse2() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt1.or(FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());

		opt = opt1.or(FOption.empty());
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());

		opt = opt2.or(FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());

		opt = opt2.or(FOption.empty());
		assertTrue(opt.isAbsent());
	}

	@Test
	public void testOrElseSupplier() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt1.or(() -> FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());

		opt = opt2.or(() -> FOption.of(5));
		assertTrue(opt.isPresent());
		assertEquals(5, (int)opt.get());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOrThrow() throws RuntimeException {
		Supplier<RuntimeException> suppl = mock(Supplier.class);
		when(suppl.get()).thenThrow(RuntimeException.class);

		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;

		opt = opt1.orThrow(suppl);
		assertTrue(opt.isPresent());
		assertEquals(10, (int)opt.get());
		verify(suppl, times(0)).get();

		assertThrows(RuntimeException.class, () -> opt2.orThrow(suppl));
	}

	@Test
	public void testToList() throws RuntimeException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		assertEquals(Lists.newArrayList(10), opt1.toList());
		assertEquals(Lists.newArrayList(), opt2.toList());
	}

	@Test
	public void testEquals() throws RuntimeException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		FOption<Integer> opt3 = FOption.of(null);

		assertEquals(FOption.of(10), opt1);
		assertNotEquals(opt3, opt1);
		assertNotEquals(opt1, new Object());
		assertNotEquals(opt1, null);
		assertEquals(FOption.empty(), opt2);
		assertNotEquals(opt3, opt2);
	}

	/**
	 * equals가 true인 두 FOption은 hashCode도 같으므로 HashSet·HashMap에서 찾을 수 있다.
	 */
	@Test
	public void testHashCodeConsistentWithEquals() {
		FOption<String> a = FOption.of("a");
		FOption<String> a2 = FOption.of(new String("a"));
		assertEquals(a, a2);
		assertEquals(a.hashCode(), a2.hashCode());
		assertTrue(new java.util.HashSet<>(List.of(a)).contains(a2));

		assertEquals(FOption.of(null), FOption.of(null));
		assertEquals(FOption.of(null).hashCode(), FOption.of(null).hashCode());
		assertEquals(FOption.empty().hashCode(), FOption.empty().hashCode());
		assertNotEquals(FOption.empty(), FOption.of(null));
		assertNotEquals(FOption.empty().hashCode(), FOption.of(null).hashCode());
	}

	@Test
	public void orElseValueMayBeNull() {
		FOption<String> r = FOption.<String>empty().orElse((String)null);
		assertTrue(r.isPresent());
		assertEquals(null, r.get());
	}

	@Test
	public void toListIsUnmodifiable() {
		List<String> list = FOption.of("a").toList();
		assertEquals(List.of("a"), list);
		assertThrows(UnsupportedOperationException.class, () -> list.set(0, "b"));
	}

	@Test
	public void deserializedEmptyIsSingleton() throws Exception {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try ( java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes) ) {
			out.writeObject(FOption.empty());
		}
		try ( java.io.ObjectInputStream in
					= new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())) ) {
			assertTrue(in.readObject() == FOption.empty());
		}
	}

	@Test
	public void staticIfPresentRejectsNullEffectEvenForNullValue() {
		assertThrows(IllegalArgumentException.class, () -> Nullables.ifNotNull(null, (java.util.function.Consumer<Object>)null));
	}
}
