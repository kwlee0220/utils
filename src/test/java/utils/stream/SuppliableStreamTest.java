package utils.stream;


import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.StopWatch;
import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class SuppliableStreamTest {
	private SuppliableFStream<Integer> m_stream;
	private final Exception m_error = new Exception();
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		m_stream = FStream.pipe(3);
	}
	
	@Test
	public void test0() throws Exception {
		FOption<Integer> r;

		r = m_stream.poll();
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test1() throws Exception {
		FOption<Try<Integer>> r;
		
		r = m_stream.next(1, TimeUnit.SECONDS);
		Assert.assertTrue(r.get().isFailure());
		Assert.assertEquals(TimeoutException.class, r.get().getCause().getClass());
	}

	@Test
	public void test2() throws Exception {
		FOption<Try<Integer>> r;
		
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(700);
				m_stream.supply(1);
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});

		r = m_stream.next(200, TimeUnit.MILLISECONDS);
		Assert.assertTrue(r.get().isFailure());
		Assert.assertEquals(TimeoutException.class, r.get().getCause().getClass());
	}
	
	@Test
	public void test3() throws Exception {
		FOption<Integer> r;

		StopWatch watch = StopWatch.start();
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(300);
				m_stream.supply(1);
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});

		r = m_stream.next();
		watch.stop();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		Assert.assertThat(watch.getElapsedInMillis(), greaterThanOrEqualTo(300L));
	}
	
	@Test
	public void test4() throws Exception {
		FOption<Integer> r;
		
		m_stream.supply(1);
		m_stream.supply(2);
		m_stream.endOfSupply();

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test5() throws Exception {
		FOption<Integer> r;

		CompletableFuture.runAsync(() -> {
			try {
				m_stream.supply(1);
				Thread.sleep(300);
				m_stream.supply(2);
				m_stream.endOfSupply();
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});

		StopWatch watch = StopWatch.start();
		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());

		r = m_stream.next();
		watch.stop();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		Assert.assertThat(watch.getElapsedInMillis(), greaterThanOrEqualTo(300L));
	}
	
	@Test
	public void test6() throws Exception {
		FOption<Integer> r;

		CompletableFuture.runAsync(() -> {
			try {
				m_stream.supply(1);
				Thread.sleep(300);
				m_stream.endOfSupply();
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());

		StopWatch watch = StopWatch.start();
		r = m_stream.next();
		watch.stop();
		Assert.assertEquals(true, r.isAbsent());
		Assert.assertThat(watch.getElapsedInMillis(), greaterThanOrEqualTo(300L));
	}

	@Test(expected=RuntimeException.class)
	public void test7() throws Exception {
		FOption<Integer> r;
		
		m_stream.supply(1);
		m_stream.supply(2);
		m_stream.endOfSupply(m_error);

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());

		r = m_stream.next();
	}

	@Test
	public void test8() throws Exception {
		FOption<Integer> r;

		CompletableFuture.runAsync(() -> {
			try {
				m_stream.supply(1);
				Thread.sleep(300);
				m_stream.endOfSupply(m_error);
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});

		r = m_stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());

		StopWatch watch = StopWatch.start();
		try {
			r = m_stream.next();
		}
		catch ( Exception e ) {
			watch.stop();
			Assert.assertThat(watch.getElapsedInMillis(), greaterThanOrEqualTo(300L));
		}
	}

	@Test
	public void test9() throws Exception {
		FOption<Integer> r;

		CompletableFuture.runAsync(() -> {
			try {
				m_stream.next();
				m_stream.next();
				m_stream.close();
			}
			catch ( Exception e ) {
				e.printStackTrace();
			}
		});

		m_stream.supply(1);
		m_stream.supply(2);
		Thread.sleep(100);
		Assert.assertEquals(false, m_stream.supply(3));
	}

	@Test
	public void test10() throws Exception {
		FOption<Integer> r;

		CompletableFuture.runAsync(() -> {
			try {
				m_stream.next();
				m_stream.next();
				Thread.sleep(300);
				m_stream.close();
			}
			catch ( Exception e ) {
				e.printStackTrace();
			}
		});

		m_stream.supply(1);
		m_stream.supply(2);
		StopWatch watch = StopWatch.start();
		try {
			m_stream.supply(3);
		}
		catch ( Exception e ) {
			watch.stop();
			Assert.assertThat(watch.getElapsedInMillis(), greaterThanOrEqualTo(300L));
			Assert.assertTrue(e instanceof IllegalStateException);
		}
	}
}
