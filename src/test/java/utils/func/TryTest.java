package utils.func;


import java.io.IOException;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

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
		Supplier<Try<Void>> ret0 = Try.lift(cr0);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret0.get().isSuccessful());
		Assert.assertEquals(null, ret0.get().get());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Supplier<Try<Void>> ret1 = Try.lift(cr1);
		Assert.assertEquals(INIT, m_result);
		Assert.assertEquals(true, ret1.get().isFailed());
		Assert.assertEquals(IOException.class, ret1.get().getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test3() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Assert.assertEquals(true, Try.lift(cr0).get().isSuccessful());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Assert.assertEquals(true, Try.lift(cr1).get().isFailed());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test5() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Try<Void> ret0 = Try.lift(cr0).get();
		Assert.assertEquals(true, ret0.isSuccessful());
		Assert.assertNull(ret0.get());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Try<Void> ret1 = Try.lift(cr1).get();
		Assert.assertEquals(true, ret1.isFailed());
		Assert.assertEquals(IOException.class, ret1.getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test11() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Supplier<Try<String>> ret0 = Try.lift(cr0);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret0.get().isSuccessful());
		Assert.assertEquals(COMPLETED, ret0.get().get());
		Assert.assertEquals(COMPLETED, m_result);
		
		m_result = INIT;
		Supplier<Try<String>> ret1 = Try.lift(cr1);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret1.get().isFailed());
		Assert.assertEquals(IOException.class, ret1.get().getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test(expected = IOException.class)
	public void test12() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Try<String> ret0 = Try.lift(cr0).get();
		Assert.assertEquals(true, ret0.isSuccessful());
		Assert.assertEquals(COMPLETED, ret0.get());
		Assert.assertEquals(COMPLETED, m_result);
		
		m_result = INIT;
		Try<String> ret1 = Try.lift(cr1).get();
		Assert.assertEquals(true, ret1.isFailed());
		Assert.assertEquals(IOException.class, ret1.getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
		
		ret1.get();
	}
}
