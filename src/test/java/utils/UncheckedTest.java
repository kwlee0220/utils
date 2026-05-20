package utils;


import java.io.IOException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.func.CheckedConsumer;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplierX;
import utils.func.UncheckedConsumer;
import utils.func.UncheckedRunnable;

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
		Runnable r = UncheckedRunnable.ignore(cr);
		Assertions.assertEquals(INIT, m_result);
		r.run();
		Assertions.assertEquals(FAILED, m_result);
	}

	@Test
	public void test4() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		UncheckedRunnable.sneakyThrow(cr0).run();
		Assertions.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		try {
			UncheckedRunnable.sneakyThrow(cr1).run();
			Assertions.fail();
		}
		catch ( Exception e ) {
			Assertions.assertEquals(IOException.class, e.getClass());
			Assertions.assertEquals(FAILED, m_result);
		}
	}

	@Test
	public void test10() throws IOException {
		CheckedSupplierX<String,IOException> cr0 = () -> { return COMPLETED; };
		CheckedSupplierX<String,IOException> cr1 = () -> { throw new IOException("xxx"); };

		String ret0 = cr0.get();
		Assertions.assertEquals(COMPLETED, ret0);

		try {
			cr1.get();
			Assertions.fail();
		}
		catch ( IOException e ) { }
	}

	@Test
	public void test20() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Consumer<String> ret0 = UncheckedConsumer.ignore(cr0);
		Assertions.assertEquals(INIT, m_result);

		ret0.accept(COMPLETED);
		Assertions.assertEquals(COMPLETED, m_result);

		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = UncheckedConsumer.ignore(cr1);
		Assertions.assertEquals(INIT, m_result);

		ret1.accept(COMPLETED);
		Assertions.assertEquals(COMPLETED, m_result);
	}

	@Test
	public void test21() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Consumer<String> ret0 = UncheckedConsumer.sneakyThrow(cr0);
		Assertions.assertEquals(INIT, m_result);

		ret0.accept(COMPLETED);
		Assertions.assertEquals(COMPLETED, m_result);

		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = UncheckedConsumer.sneakyThrow(cr1);
		Assertions.assertEquals(INIT, m_result);

		try {
			ret1.accept(COMPLETED);
			Assertions.fail();
		}
		catch ( Exception e ) {
			Assertions.assertEquals(IOException.class, e.getClass());
			Assertions.assertEquals(COMPLETED, m_result);
		}
	}
}
