package utils;


import java.io.IOException;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import utils.func.CheckedConsumer;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplierX;
import utils.func.Unchecked;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedTest {
	private static final String INIT = "init";
	private static final String COMPLETED = "completed";
	private static final String FAILED = "failed";
	private String m_result = INIT;
	
	@Test
	public void test0() throws Exception {
		CheckedRunnable cr = () -> {
			m_result = FAILED;
			throw new AssertionError();
		};
		
		m_result = INIT;
		Runnable r = Unchecked.ignore(cr);
		Assert.assertEquals(INIT, m_result);
		r.run();
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test4() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Unchecked.sneakyThrow(cr0).run();;
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		try {
			Unchecked.sneakyThrow(cr1).run();
			Assert.fail();
		}
		catch ( Exception e ) {
			Assert.assertEquals(IOException.class, e.getClass());
			Assert.assertEquals(FAILED, m_result);
		}
	}
	
	@Test
	public void test10() throws IOException {
		CheckedSupplierX<String,IOException> cr0 = () -> { return COMPLETED; };
		CheckedSupplierX<String,IOException> cr1 = () -> { throw new IOException("xxx"); };
		
		String ret0 = Unchecked.getOrThrow(cr0);
		Assert.assertEquals(COMPLETED, ret0);
		
		try {
			Unchecked.getOrThrow(cr1);
			Assert.fail();
		}
		catch ( IOException e ) { }
	}
	
	@Test
	public void test20() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Consumer<String> ret0 = Unchecked.ignore(cr0);
		Assert.assertEquals(INIT, m_result);
		
		ret0.accept(COMPLETED);
		Assert.assertEquals(COMPLETED, m_result);
		
		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = Unchecked.ignore(cr1);
		Assert.assertEquals(INIT, m_result);

		ret1.accept(COMPLETED);
		Assert.assertEquals(COMPLETED, m_result);
	}
	
	@Test
	public void test21() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Consumer<String> ret0 = Unchecked.sneakyThrow(cr0);
		Assert.assertEquals(INIT, m_result);
		
		ret0.accept(COMPLETED);
		Assert.assertEquals(COMPLETED, m_result);
		
		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = Unchecked.sneakyThrow(cr1);
		Assert.assertEquals(INIT, m_result);
		
		try {
			ret1.accept(COMPLETED);
			Assert.fail();
		}
		catch ( Exception e ) {
			Assert.assertEquals(IOException.class, e.getClass());
			Assert.assertEquals(COMPLETED, m_result);
		}
	}
}
