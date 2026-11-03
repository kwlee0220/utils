package utils.func;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import utils.Throwables;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ResultTest {
	private static final Result<String> R1 = Result.success("a");
	private static final Result<String> R2 = Result.cancelled();
	private static final Result<String> R3 = Result.failure(new IOException("IOException"));
	private static final Result<String> R4 = Result.failure(new IllegalArgumentException("IllegalArgumentException"));

	@Test
	public void testIs() throws Exception {
		assertEquals(true, R1.isSuccessful());
		assertEquals(false, R1.isFailed());
		assertEquals(false, R1.isCancelled());
		assertEquals(false, R2.isSuccessful());
		assertEquals(false, R2.isFailed());
		assertEquals(true, R2.isCancelled());
		assertEquals(false, R3.isSuccessful());
		assertEquals(true, R3.isFailed());
		assertEquals(false, R3.isCancelled());
	}

	@Test
	public void testGet() throws Exception {
		assertEquals("a", R1.get());
		assertThrows(IllegalStateException.class, R2::get);

		ExecutionException r3Ex = assertThrows(ExecutionException.class, R3::get);
		assertEquals(IOException.class, r3Ex.getCause().getClass());

		assertThrows(ExecutionException.class, R4::get);
	}

	@Test
	public void testGetOrElse() throws Exception {
		assertEquals("a", R1.getOrElse("b"));
		assertEquals("b", R2.getOrElse("b"));
		assertEquals("b", R3.getOrElse("b"));
		assertEquals("b", R4.getOrElse("b"));
	}

	@Test
	public void testGetOrElseThrow1() throws Exception {
		IllegalStateException ex = new IllegalStateException();
		assertEquals("a", R1.getOrThrow(() -> ex));
		assertThrows(IllegalStateException.class, () -> R2.getOrThrow(() -> ex));
		assertThrows(IllegalStateException.class, () -> R3.getOrThrow(() -> ex));
		assertThrows(IllegalStateException.class, () -> R4.getOrThrow(() -> ex));
	}

	@Test
	public void testGetOrElseThrow2() throws Exception {
		Function<Throwable,ExecutionException> prvd = (exc) -> new ExecutionException(exc);

		assertEquals("a", R1.getOrThrow(prvd));

		ExecutionException r2Ex = assertThrows(ExecutionException.class, () -> R2.getOrThrow(prvd));
		assertEquals(CancellationException.class, Throwables.unwrapThrowable(r2Ex).getClass());	// 취소 상태

		ExecutionException r3Ex = assertThrows(ExecutionException.class, () -> R3.getOrThrow(prvd));
		assertEquals(IOException.class, Throwables.unwrapThrowable(r3Ex).getClass());

		ExecutionException r4Ex = assertThrows(ExecutionException.class, () -> R4.getOrThrow(prvd));
		assertEquals(IllegalArgumentException.class, Throwables.unwrapThrowable(r4Ex).getClass());
	}

	@Test
	public void testFilter() throws Exception {
		assertEquals("a", R1.filter(s -> s.length() == 1).get());
		assertEquals(true, R1.filter(s -> s.length() == 2).isCancelled());
		assertEquals(true, R2.filter(s -> s.length() == 1).isCancelled());
		assertEquals(true, R3.filter(s -> s.length() == 1).isFailed());
	}

	/**
	 * 원인을 모르는 실패(null 원인)도 만들 수 있고, equals·hashCode가 NPE 없이 동작한다.
	 */
	@Test
	public void failureWithNullCauseIsComparable() {
		Result<Object> f = Result.failure(null);
		org.junit.jupiter.api.Assertions.assertTrue(f.isFailed());
		org.junit.jupiter.api.Assertions.assertEquals(f, Result.failure(null));
		org.junit.jupiter.api.Assertions.assertEquals(f.hashCode(), Result.failure(null).hashCode());
	}
}
