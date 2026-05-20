package utils.stream;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.Holder;
import utils.StopWatch;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class SuppliableStreamTest {
	private SuppliableFStream<Integer> m_stream;
	private final Exception m_error = new Exception();
	
	@BeforeEach
	public void setup() {
		m_stream = new SuppliableFStream<>(4);
	}
	
	@Test
	public void test0() throws Exception {
		FOption<Integer> r;

		r = m_stream.poll();
		Assertions.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test1() throws Exception {
		try {
			m_stream.next(1, TimeUnit.SECONDS);
			Assertions.fail("Should raise TimeoutException");
		}
		catch ( TimeoutException expected ) {}
	}

	@Test
	public void test2() throws Exception {
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(700);
				m_stream.supply(1);
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		});
		
		try {
			m_stream.next(200, TimeUnit.MILLISECONDS);
			Assertions.fail("Should raise TimeoutException");
		}
		catch ( TimeoutException expected ) {}
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
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());
		Assertions.assertTrue(watch.getElapsedInMillis() >= 300L);
	}
	
	@Test
	public void test4() throws Exception {
		FOption<Integer> r;
		
		m_stream.supply(1);
		m_stream.supply(2);
		m_stream.endOfSupply();

		r = m_stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());

		r = m_stream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(2), r.get());

		r = m_stream.next();
		Assertions.assertEquals(true, r.isAbsent());
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
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());

		r = m_stream.next();
		watch.stop();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(2), r.get());
		Assertions.assertTrue(watch.getElapsedInMillis() >= 300L);
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
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());

		StopWatch watch = StopWatch.start();
		r = m_stream.next();
		watch.stop();
		Assertions.assertEquals(true, r.isAbsent());
		Assertions.assertTrue(watch.getElapsedInMillis() >= 300L);
	}

	@Test
	public void test7() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			FOption<Integer> r;
		
			m_stream.supply(1);
			m_stream.supply(2);
			m_stream.endOfSupply(m_error);

			r = m_stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(1), r.get());

			r = m_stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(2), r.get());

			r = m_stream.next();
			});
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
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Integer.valueOf(1), r.get());

		StopWatch watch = StopWatch.start();
		try {
			r = m_stream.next();
		}
		catch ( Exception e ) {
			watch.stop();
			Assertions.assertTrue(watch.getElapsedInMillis() >= 300L);
		}
	}

	@Test
	public void test9() throws Exception {
		Holder<Integer> state = Holder.of(0);

		CompletableFuture.runAsync(() -> {
			try {
				FOption<Integer>ret;
				
				ret = m_stream.next();
				Assertions.assertEquals(1, (int)ret.get());
				Assertions.assertTrue(1 <= (int)state.get());
				
				ret = m_stream.next();
				Assertions.assertEquals(2, (int)ret.get());
				Assertions.assertEquals(2, (int)state.get());

				m_stream.close();
				synchronized ( state ) {
					state.set(3);
					state.notifyAll();
				}
			}
			catch ( Throwable e ) {
				e.printStackTrace();
			}
		});

		Assertions.assertEquals(0, (int)state.get());
		state.set(1);
		m_stream.supply(1);

		state.set(2);
		m_stream.supply(2);
		
		synchronized ( state ) {
			while ( state.get() == 2 ) {
				state.wait(1000);
			}
		}
		try {
			m_stream.supply(3);
			Assertions.fail("Should not be called");
		}
		catch ( IllegalStateException expected ) { }
	}

	@Test
	public void test10() throws Exception {
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
			Assertions.assertTrue(watch.getElapsedInMillis() >= 300L);
			Assertions.assertTrue(e instanceof IllegalStateException);
		}
	}

	// ---------- setSupplyListener ----------

	@Test
	public void listener_invoked_for_each_supply() throws Exception {
		AtomicInteger count = new AtomicInteger();
		m_stream.setSupplyListener(count::incrementAndGet);

		m_stream.supply(1);
		m_stream.supply(2);
		m_stream.supply(3);

		Assertions.assertEquals(3, count.get());
	}

	@Test
	public void null_listener_is_noop() throws Exception {
		// listener를 한 번 등록한 뒤 해제하면 이후 supply는 listener 호출 없이 정상 진행.
		AtomicInteger count = new AtomicInteger();
		m_stream.setSupplyListener(count::incrementAndGet);
		m_stream.supply(1);

		m_stream.setSupplyListener(null);
		m_stream.supply(2);
		m_stream.supply(3);

		Assertions.assertEquals(1, count.get());
	}

	@Test
	public void listener_exception_is_swallowed() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		m_stream.setSupplyListener(() -> {
			calls.incrementAndGet();
			throw new RuntimeException("listener-boom");
		});

		// listener가 예외를 던져도 supply는 정상 진행되고 큐에는 값이 들어간다.
		m_stream.supply(10);
		m_stream.supply(20);
		Assertions.assertEquals(2, calls.get());

		Assertions.assertEquals(Integer.valueOf(10), m_stream.next().get());
		Assertions.assertEquals(Integer.valueOf(20), m_stream.next().get());
	}

	@Test
	public void listener_is_replaced_when_set_again() throws Exception {
		AtomicInteger first = new AtomicInteger();
		AtomicInteger second = new AtomicInteger();

		m_stream.setSupplyListener(first::incrementAndGet);
		m_stream.supply(1);

		m_stream.setSupplyListener(second::incrementAndGet);
		m_stream.supply(2);

		Assertions.assertEquals(1, first.get());
		Assertions.assertEquals(1, second.get());
	}

	@Test
	public void listener_invoked_outside_lock() throws Exception {
		// listener 안에서 본 객체의 다른 메소드를 호출해도 deadlock 없이 즉시 동작해야 한다.
		// (lock 밖 호출의 직접적 관찰 가능 효과)
		Holder<FOption<Integer>> drained = Holder.of(FOption.empty());
		m_stream.setSupplyListener(() -> {
			// listener 시점에 enqueue가 이미 끝났으므로 poll()이 즉시 적재된 값을 받아간다.
			drained.set(m_stream.poll());
		});

		m_stream.supply(42);

		Assertions.assertTrue(drained.get().isPresent());
		Assertions.assertEquals(Integer.valueOf(42), drained.get().get());
		// listener가 poll로 빼냈으므로 다음 next는 더 이상 값이 없다.
		Assertions.assertEquals(0, m_stream.size());
	}

	@Test
	public void listener_invoked_for_supply_with_timeout_when_success() throws Exception {
		AtomicInteger count = new AtomicInteger();
		m_stream.setSupplyListener(count::incrementAndGet);

		m_stream.supply(7, 500, TimeUnit.MILLISECONDS);

		Assertions.assertEquals(1, count.get());
	}

	@Test
	public void listener_not_invoked_when_supply_timeouts() throws Exception {
		// 큐를 가득 채워두면 다음 supply는 timeout 발생 → listener 호출되지 않아야 한다.
		AtomicInteger count = new AtomicInteger();
		for ( int i = 0; i < m_stream.capacity(); ++i ) {
			m_stream.supply(i);
		}
		m_stream.setSupplyListener(count::incrementAndGet);

		try {
			m_stream.supply(999, 200, TimeUnit.MILLISECONDS);
			Assertions.fail("TimeoutException expected");
		}
		catch ( TimeoutException expected ) { }

		Assertions.assertEquals(0, count.get(),
							"timeout으로 enqueue 실패 시 listener는 호출되지 않아야 함");
	}

	@Test
	public void listener_invoked_for_supply_with_supplier() throws Exception {
		AtomicInteger count = new AtomicInteger();
		m_stream.setSupplyListener(count::incrementAndGet);

		Integer supplied = m_stream.supply(() -> 100);

		Assertions.assertEquals(Integer.valueOf(100), supplied);
		Assertions.assertEquals(1, count.get());
	}

	@Test
	public void listener_concurrent_supply_fires_per_supply() throws Exception {
		// 여러 thread가 동시에 supply하더라도 listener는 supply마다 정확히 한 번 호출되어야 한다.
		final int N_PRODUCERS = 4;
		final int N_PER_PRODUCER = 25;
		final int total = N_PRODUCERS * N_PER_PRODUCER;
		SuppliableFStream<Integer> stream = new SuppliableFStream<>(total);

		AtomicInteger count = new AtomicInteger();
		CountDownLatch done = new CountDownLatch(total);
		stream.setSupplyListener(() -> {
			count.incrementAndGet();
			done.countDown();
		});

		List<CompletableFuture<Void>> tasks = new ArrayList<>();
		for ( int p = 0; p < N_PRODUCERS; ++p ) {
			final int base = p * N_PER_PRODUCER;
			tasks.add(CompletableFuture.runAsync(() -> {
				try {
					for ( int i = 0; i < N_PER_PRODUCER; ++i ) {
						stream.supply(base + i);
					}
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}));
		}
		tasks.forEach(CompletableFuture::join);

		Assertions.assertTrue(done.await(2, TimeUnit.SECONDS),
							"listener가 모든 supply에 대해 호출되어야 함");
		Assertions.assertEquals(total, count.get());
	}
}
