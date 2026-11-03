package utils.func;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TryTest {
	private static final String INIT = "init";
	private static final String COMPLETED = "completed";
	private static final String FAILED = "failed";
	private String m_result = INIT;

	@Test
	public void test2() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Supplier<Try<Void>> ret0 = Try.liftRunnable(cr0);
		assertEquals(INIT, m_result);

		assertEquals(true, ret0.get().isSuccessful());
		assertEquals(null, ret0.get().get());
		assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Supplier<Try<Void>> ret1 = Try.liftRunnable(cr1);
		assertEquals(INIT, m_result);
		assertEquals(true, ret1.get().isFailed());
		assertEquals(IOException.class, ret1.get().getCause().getClass());
		assertEquals(FAILED, m_result);
	}

	@Test
	public void test3() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		assertEquals(true, Try.liftRunnable(cr0).get().isSuccessful());
		assertEquals(COMPLETED, m_result);

		m_result = INIT;
		assertEquals(true, Try.liftRunnable(cr1).get().isFailed());
		assertEquals(FAILED, m_result);
	}

	@Test
	public void test5() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Try<Void> ret0 = Try.liftRunnable(cr0).get();
		assertEquals(true, ret0.isSuccessful());
		assertNull(ret0.get());
		assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Try<Void> ret1 = Try.liftRunnable(cr1).get();
		assertEquals(true, ret1.isFailed());
		assertEquals(IOException.class, ret1.getCause().getClass());
		assertEquals(FAILED, m_result);
	}

	@Test
	public void test11() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Supplier<Try<String>> ret0 = Try.liftSupplier(cr0);
		assertEquals(INIT, m_result);

		assertEquals(true, ret0.get().isSuccessful());
		assertEquals(COMPLETED, ret0.get().get());
		assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Supplier<Try<String>> ret1 = Try.liftSupplier(cr1);
		assertEquals(INIT, m_result);

		assertEquals(true, ret1.get().isFailed());
		assertEquals(IOException.class, ret1.get().getCause().getClass());
		assertEquals(FAILED, m_result);
	}

	@Test
	public void test12() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Try<String> ret0 = Try.liftSupplier(cr0).get();
		assertEquals(true, ret0.isSuccessful());
		assertEquals(COMPLETED, ret0.get());
		assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Try<String> ret1 = Try.liftSupplier(cr1).get();
		assertEquals(true, ret1.isFailed());
		assertEquals(IOException.class, ret1.getCause().getClass());
		assertEquals(FAILED, m_result);

		assertThrows(IOException.class, ret1::get);
	}

	/**
	 * {@code Success}는 값으로, {@code Failure}는 원인(같은 예외 객체)으로 비교한다.
	 */
	@Test
	public void equalsAndHashCode() {
		org.junit.jupiter.api.Assertions.assertEquals(Try.success("a"), Try.success(new String("a")));
		org.junit.jupiter.api.Assertions.assertEquals(Try.success("a").hashCode(), Try.success(new String("a")).hashCode());
		org.junit.jupiter.api.Assertions.assertEquals(Try.success(null), Try.success(null));
		org.junit.jupiter.api.Assertions.assertNotEquals(Try.success("a"), Try.success("b"));

		IllegalStateException cause = new IllegalStateException("x");
		org.junit.jupiter.api.Assertions.assertEquals(Try.failure(cause), Try.failure(cause));
		org.junit.jupiter.api.Assertions.assertEquals(Try.failure(cause).hashCode(), Try.failure(cause).hashCode());
		org.junit.jupiter.api.Assertions.assertNotEquals(Try.failure(cause), Try.failure(new IllegalStateException("x")));
		org.junit.jupiter.api.Assertions.assertNotEquals(Try.success(cause), Try.failure(cause));
	}
}
