package utils;


import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import io.vavr.CheckedConsumer;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import utils.Unchecked.CheckedSupplier;

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
		Runnable r = Unchecked.liftIE(cr);
		Assert.assertEquals(INIT, m_result);
		r.run();
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test1() throws Exception {
		CheckedRunnable cr = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Runnable r = Unchecked.liftRTE(cr);
		Assert.assertEquals(INIT, m_result);
		
		try {
			r.run();
			Assert.fail();
		}
		catch ( RuntimeException e ) {
			Assert.assertEquals(FAILED, m_result);
			Assert.assertEquals(IOException.class, e.getCause().getClass());
		}
	}
	
	@Test
	public void test2() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Supplier<Try<Void>> ret0 = Unchecked.lift(cr0);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret0.get().isSuccess());
		Assert.assertEquals(null, ret0.get().get());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Supplier<Try<Void>> ret1 = Unchecked.lift(cr1);
		Assert.assertEquals(INIT, m_result);
		Assert.assertEquals(true, ret1.get().isFailure());
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
		Assert.assertEquals(true, Try.run(cr0).isSuccess());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Assert.assertEquals(true, Try.run(cr1).isFailure());
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
		Unchecked.runRTE(cr0);
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		try {
			Unchecked.runRTE(cr1);
			Assert.fail();
		}
		catch ( RuntimeException e ) {
			Assert.assertEquals(IOException.class, e.getCause().getClass());
			Assert.assertEquals(FAILED, m_result);
		}
	}
	
	@Test
	public void test5() throws Exception {
		CheckedRunnable cr0 = () -> { m_result = COMPLETED; };
		CheckedRunnable cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};

		m_result = INIT;
		Try<Void> ret0 = Try.run(cr0);
		Assert.assertEquals(true, ret0.isSuccess());
		Assert.assertNull(ret0.get());
		Assert.assertEquals(COMPLETED, m_result);

		m_result = INIT;
		Try<Void> ret1 = Try.run(cr1);
		Assert.assertEquals(true, ret1.isFailure());
		Assert.assertEquals(IOException.class, ret1.getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test10() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Supplier<String> ret0 = Unchecked.liftRTE(cr0);
		Assert.assertEquals(INIT, m_result);
		Assert.assertEquals(COMPLETED, ret0.get());
		
		m_result = INIT;
		Supplier<String> ret1 = Unchecked.liftRTE(cr1);
		Assert.assertEquals(INIT, m_result);
		try {
			Assert.assertNull(ret1.get());
			Assert.fail();
		}
		catch ( RuntimeException e ) {
			Assert.assertEquals(IOException.class, e.getCause().getClass());
			Assert.assertEquals(FAILED, m_result);
		}
	}
	
	@Test
	public void test11() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Supplier<Try<String>> ret0 = Unchecked.lift(cr0);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret0.get().isSuccess());
		Assert.assertEquals(COMPLETED, ret0.get().get());
		Assert.assertEquals(COMPLETED, m_result);
		
		m_result = INIT;
		Supplier<Try<String>> ret1 = Unchecked.lift(cr1);
		Assert.assertEquals(INIT, m_result);
		
		Assert.assertEquals(true, ret1.get().isFailure());
		Assert.assertEquals(IOException.class, ret1.get().getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test12() throws Exception {
		CheckedSupplier<String> cr0 = () -> { return m_result = COMPLETED; };
		CheckedSupplier<String> cr1 = () -> {
			m_result = FAILED;
			throw new IOException("xxx");
		};
		
		m_result = INIT;
		Try<String> ret0 = Unchecked.tryToSupply(cr0);
		Assert.assertEquals(true, ret0.isSuccess());
		Assert.assertEquals(COMPLETED, ret0.get());
		Assert.assertEquals(COMPLETED, m_result);
		
		m_result = INIT;
		Try<String> ret1 = Unchecked.tryToSupply(cr1);
		Assert.assertEquals(true, ret1.isFailure());
		Assert.assertEquals(IOException.class, ret1.getCause().getClass());
		Assert.assertEquals(FAILED, m_result);
	}
	
	@Test
	public void test20() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Consumer<String> ret0 = Unchecked.liftIE(cr0);
		Assert.assertEquals(INIT, m_result);
		
		ret0.accept(COMPLETED);
		Assert.assertEquals(COMPLETED, m_result);
		
		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = Unchecked.liftIE(cr1);
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
		Consumer<String> ret0 = Unchecked.liftRTE(cr0);
		Assert.assertEquals(INIT, m_result);
		
		ret0.accept(COMPLETED);
		Assert.assertEquals(COMPLETED, m_result);
		
		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Consumer<String> ret1 = Unchecked.liftRTE(cr1);
		Assert.assertEquals(INIT, m_result);
		
		try {
			ret1.accept(COMPLETED);
			Assert.fail();
		}
		catch ( RuntimeException e ) {
			Assert.assertEquals(IOException.class, e.getCause().getClass());
			Assert.assertEquals(COMPLETED, m_result);
		}
	}
	
	@Test
	public void test22() throws Exception {
		CheckedConsumer<String> cr0 = (t) -> {
			m_result = t;
		};
		m_result = INIT;
		Function<String, Try<Void>> func0 = Unchecked.lift(cr0);
		Assert.assertEquals(INIT, m_result);
		
		Try<Void> ret0 = func0.apply(COMPLETED);
		Assert.assertEquals(true, ret0.isSuccess());
		Assert.assertEquals(null, ret0.get());
		Assert.assertEquals(COMPLETED, m_result);
		
		CheckedConsumer<String> cr1 = (t) -> {
			m_result = t;
			throw new IOException("xxx");
		};
		m_result = INIT;
		Function<String, Try<Void>> func1 = Unchecked.lift(cr1);
		Assert.assertEquals(INIT, m_result);

		Try<Void> ret1 = func1.apply(COMPLETED);
		Assert.assertEquals(true, ret1.isFailure());
		Assert.assertEquals(IOException.class, ret1.getCause().getClass());
		Assert.assertEquals(COMPLETED, m_result);
	}
}
