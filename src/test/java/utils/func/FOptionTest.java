package utils.func;

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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FOptionTest {
	private String m_str;
	
	@Before
	public void setUp() {
		m_str = null;
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void test2() throws Exception {
		FOption<String> opt = FOption.empty();

		opt.ifAbsentOrThrow(() -> m_str = "a");
		Assert.assertEquals("a", m_str);
		
		opt.ifAbsentOrThrow(() -> { throw new IllegalArgumentException(); });
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void test3() throws Exception {
		FOption<String> opt = FOption.of("z");

		opt.ifPresentOrThrow(v -> m_str = v);
		Assert.assertEquals("z", m_str);
		
		opt.ifPresentOrThrow(v -> { throw new IllegalArgumentException(); });
	}
	
	@Test
	public void test4() throws Exception {
		@SuppressWarnings("unchecked")
		Supplier<String> suppl = mock(Supplier.class);
		when(suppl.get()).thenReturn("b");
		
		FStream<String> strm;
		
		strm = FOption.<String>empty().fstream();
		Assert.assertEquals(true, strm.next().isAbsent());
		
		strm = FOption.of("a").fstream();
		
		FOption<String> next = strm.next();
		Assert.assertEquals(true, next.isPresent());
		Assert.assertEquals("a", next.get());
		Assert.assertEquals("a", next.getOrNull());
		Assert.assertEquals("a", next.getOrElse("b"));
		Assert.assertEquals("a", next.getOrElse(suppl));
		verify(suppl, times(0)).get();
		
		next = strm.next();
		Assert.assertTrue(next.isAbsent());
		Assert.assertEquals(null, next.getOrNull());
		Assert.assertEquals("b", next.getOrElse("b"));

		Assert.assertEquals("b", next.getOrElse(suppl));
		verify(suppl, times(1)).get();
	}
	
	@Test(expected = NoSuchValueException.class)
	public void test5() throws Exception {
		FOption<String> opt = FOption.ofNullable("a");
		Assert.assertEquals(true, opt.isPresent());
		Assert.assertEquals("a", opt.get());
		Assert.assertEquals("a", opt.getUnchecked());
		
		opt.ifPresent(v -> m_str = v);
		Assert.assertEquals("a", m_str);
		
		FOption<String> opt2 = FOption.ofNullable(null);
		Assert.assertEquals(true, opt2.isAbsent());
		opt2.get();
	}
	
	@Test
	public void testFromOptional() throws Exception {
		Optional<String> opt2 = Optional.of("abc");
		FOption<String> opt = FOption.from(opt2);
		Assert.assertEquals(true, opt.isPresent());

		FOption<String> opt3 = FOption.from(Optional.<String>empty());
		Assert.assertEquals(true, opt3.isAbsent());
	}
	
	@Test
	public void testWhen1() throws Exception {
		FOption<String> opt = FOption.when(true, "a");
		Assert.assertEquals(true, opt.isPresent());
		Assert.assertEquals("a", opt.get());
		
		opt = FOption.when(false, "a");
		Assert.assertEquals(true, opt.isAbsent());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testWhen2() throws Exception {
		Supplier<String> suppl = mock(Supplier.class);
		when(suppl.get()).thenReturn("a");
		
		FOption<String> opt = FOption.when(true, suppl);
		
		verify(suppl, times(1)).get();
		Assert.assertEquals(true, opt.isPresent());
		Assert.assertEquals("a", opt.get());
		
		reset(suppl);
		opt = FOption.when(false, suppl);
		
		verify(suppl, times(0)).get();
		Assert.assertEquals(false, opt.isPresent());
	}
	
	@Test
	public void testNarrow() throws Exception {
		ArrayList<String> list = new ArrayList<>();
		FOption<ArrayList<String>> opt = FOption.of(list);
		FOption<List<String>> opt2 = FOption.narrow(opt);
	
		Assert.assertEquals(list, opt2.get());
	}
	
	@SuppressWarnings("unchecked")
	@Test(expected=RuntimeException.class)
	public void testGetOrElseThrow() throws Exception {
		CheckedSupplierX<String,IllegalArgumentException> suppl1 = mock(CheckedSupplierX.class);
		when(suppl1.get()).thenReturn("b");
		CheckedSupplierX<String,IllegalArgumentException> suppl2 = mock(CheckedSupplierX.class);
		when(suppl2.get()).thenThrow(RuntimeException.class);
		
		FOption<String> opt1 = FOption.of("a");
		FOption<String> opt2 = FOption.empty();
	
		Assert.assertEquals("a", opt1.getOrElseThrow(suppl1));
		verify(suppl1, times(0)).get();
		Assert.assertEquals("a", opt1.getOrElseThrow(suppl2));
		verify(suppl2, times(0)).get();
		
		Assert.assertEquals("b", opt2.getOrElseThrow(suppl1));
		verify(suppl1, times(1)).get();
		Assert.assertEquals("b", opt2.getOrElseThrow(suppl2));
	}
	
	@SuppressWarnings("unchecked")
	@Test(expected=RuntimeException.class)
	public void testGetOrThrow() throws RuntimeException {
		Supplier<RuntimeException> suppl = mock(Supplier.class);
		when(suppl.get()).thenThrow(RuntimeException.class);
		
		FOption<String> opt1 = FOption.of("a");
		FOption<String> opt2 = FOption.empty();
	
		Assert.assertEquals("a", opt1.getOrThrow(suppl));
		verify(suppl, times(0)).get();
		
		Assert.assertEquals("b", opt2.getOrThrow(suppl));
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
		
		try {
			opt1.filter(null);
			Assert.fail("should have raised 'IllegalArgumentException' exception");
		}
		catch ( IllegalArgumentException expected ) { }
		
		opt = opt1.filter(v -> true);
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());

		opt = opt1.filter(v -> false);
		Assert.assertTrue(opt.isAbsent());
		
		try {
			opt2.filter(null);
			Assert.fail("should have raised 'IllegalArgumentException' exception");
		}
		catch ( IllegalArgumentException expected ) { }

		opt = opt2.filter(v -> true);
		Assert.assertTrue(opt.isAbsent());
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
		
		Assert.assertTrue(opt1.test(pred1));
		verify(pred1, times(1)).test(anyInt());

		Assert.assertFalse(opt1.test(pred2));
		verify(pred2, times(1)).test(anyInt());

		Assert.assertFalse(opt2.test(pred1));
		verify(pred1, times(1)).test(anyInt());
	}
	
	@Test
	public void testMap() throws Throwable {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		FOption<Integer> opt3 = opt1.map(v -> v * 2);
		Assert.assertTrue(opt3.isPresent());
		Assert.assertEquals(20, (int)opt3.get());
		
		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		Assert.assertTrue(opt4.isAbsent());
	}
	
	@Test(expected=IOException.class)
	public void testMapSneakily() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		FOption<Integer> opt3 = opt1.mapSneakily(v -> v * 2);
		Assert.assertTrue(opt3.isPresent());
		Assert.assertEquals(20, (int)opt3.get());
		
		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		Assert.assertTrue(opt4.isAbsent());
		
		opt1.mapSneakily(v -> { throw new IOException(); });
	}
	
	@Test(expected=IOException.class)
	public void testMapOrThrow() throws IOException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		FOption<Integer> opt3 = opt1.mapOrThrow(v -> v * 2);
		Assert.assertTrue(opt3.isPresent());
		Assert.assertEquals(20, (int)opt3.get());
		
		FOption<Integer> opt4 = opt2.map(v -> v * 2);
		Assert.assertTrue(opt4.isAbsent());
		
		opt1.mapOrThrow(v -> { throw new IOException(); });
	}
	
	@Test
	public void testTransform() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		int ret = opt1.transform(5, (s,v) -> s+v);
		Assert.assertEquals(15, ret);

		int ret2 = opt2.transform(5, (s,v) -> s+v);
		Assert.assertEquals(5, ret2);
	}
	
	@Test
	public void testFlatMap() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;
		
		opt = opt1.flatMap(v -> FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
		
		opt = opt1.flatMap(v -> FOption.empty());
		Assert.assertTrue(opt.isAbsent());
		
		opt = opt2.flatMap(v -> FOption.empty());
		Assert.assertTrue(opt.isAbsent());
	}
	
	@Test
	public void testFlatmap2() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt3 = FOption.of(5);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt
			= opt1.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
		
		opt = opt3.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		Assert.assertTrue(opt.isAbsent());
		
		opt = opt2.flatMapOrElse(v -> (v % 2 == 0) ? FOption.of(v/2) : FOption.empty(), () -> FOption.of(0));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(0, (int)opt.get());
	}
	
	@Test(expected=IOException.class)
	public void testFlatMapSneakily() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;
		
		opt = opt2.flatMapSneakily(v -> FOption.empty());
		Assert.assertTrue(opt.isAbsent());
		
		opt = opt2.flatMapSneakily(v -> { throw new IOException(); });
		Assert.assertTrue(opt.isAbsent());
		
		opt = opt1.flatMapSneakily(v -> FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
		
		opt = opt1.flatMapSneakily(v -> FOption.empty());
		Assert.assertTrue(opt.isAbsent());

		opt1.mapSneakily(v -> { throw new IOException(); });
	}
	
	@Test
	public void testOrElse1() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;
		
		opt = opt1.orElse(5);
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());
		
		opt = opt2.orElse(5);
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
	}
	
	@Test
	public void testOrElse2() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;
		
		opt = opt1.orElse(FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());
		
		opt = opt1.orElse(FOption.empty());
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());
		
		opt = opt2.orElse(FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
		
		opt = opt2.orElse(FOption.empty());
		Assert.assertTrue(opt.isAbsent());
	}
	
	@Test
	public void testOrElseSupplier() {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();

		FOption<Integer> opt;
		
		opt = opt1.orElse(() -> FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());
		
		opt = opt2.orElse(() -> FOption.of(5));
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(5, (int)opt.get());
	}
	
	@SuppressWarnings("unchecked")
	@Test(expected=RuntimeException.class)
	public void testOrThrow() throws RuntimeException {
		Supplier<RuntimeException> suppl = mock(Supplier.class);
		when(suppl.get()).thenThrow(RuntimeException.class);

		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		FOption<Integer> opt;
		
		opt = opt1.orThrow(suppl);
		Assert.assertTrue(opt.isPresent());
		Assert.assertEquals(10, (int)opt.get());
		verify(suppl, times(0)).get();
		
		opt2.orThrow(suppl);
	}
	
	@Test
	public void testToList() throws RuntimeException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		
		Assert.assertEquals(Lists.newArrayList(10), opt1.toList());
		Assert.assertEquals(Lists.newArrayList(), opt2.toList());
	}
	
	@Test
	public void testEquals() throws RuntimeException {
		FOption<Integer> opt1 = FOption.of(10);
		FOption<Integer> opt2 = FOption.empty();
		FOption<Integer> opt3 = FOption.of(null);
		
		Assert.assertEquals(FOption.of(10), opt1);
		Assert.assertNotEquals(opt3, opt1);
		Assert.assertNotEquals(opt1, new Object());
		Assert.assertNotEquals(opt1, null);
		Assert.assertEquals(FOption.empty(), opt2);
		Assert.assertNotEquals(opt3, opt2);
	}
}
