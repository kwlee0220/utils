package utils.thread;


import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.async.Execution;
import utils.async.Executions;
import utils.func.FOption;
import utils.func.Result;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class CompletionFStreamTest {
	private Executor m_executor = Executors.newFixedThreadPool(5);
	private CompletionFStream<Integer> m_completions = new CompletionFStream<>(5);

	@Test
	public void test0() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(300); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 2; }, m_executor);

		FOption<Result<Integer>> ret;

		ret = m_completions.next();
		assertEquals(1, (int)ret.get().get());

		ret = m_completions.next();
		assertEquals(2, (int)ret.get().get());

		ret = m_completions.next();
		assertEquals(0, (int)ret.get().get());

		assertThrows(TimeoutException.class, () -> m_completions.next(10, MILLISECONDS));

		m_completions.endOfSupply();
		ret = m_completions.next(0, MILLISECONDS);
		assertTrue(ret.isAbsent());
	}

	@Test
	public void test1() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 0; }, m_executor);
		m_completions.submit(() -> { throw new IllegalStateException(); }, m_executor);
		Thread.sleep(200);
		m_completions.submit(() -> { MILLISECONDS.sleep(100); return 2; }, m_executor);

		Result<Integer> trial;

		trial = m_completions.next().get();
		assertTrue(trial.isFailed());
		assertEquals(IllegalStateException.class, trial.getCause().getClass());

		trial = m_completions.next().get();
		assertEquals(0, (int)trial.get());
	}

	@Test
	public void test2_submitAfterClose() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.close();

		assertThrows(IllegalStateException.class,
						() -> m_completions.submit(() -> { MILLISECONDS.sleep(30); return 2; }, m_executor));
	}

	@Test
	public void test3_submitAfterEndOfSupply() throws Exception {
		m_completions.submit(() -> { MILLISECONDS.sleep(30); return 0; }, m_executor);
		m_completions.submit(() -> { return 1; }, m_executor);
		m_completions.endOfSupply();

		assertThrows(IllegalStateException.class,
						() -> m_completions.submit(() -> { MILLISECONDS.sleep(30); return 2; }, m_executor));
	}

	@Test
	public void test4_submitNullCallable() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> m_completions.submit(null, m_executor));
	}

	@Test
	public void test5_submitNullExecution() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> m_completions.submit((Execution<Integer>) null));
	}

	@Test
	public void test6_submitAlreadyStartedExecution() throws Exception {
		Execution<Integer> exec = Executions.supplyCheckedAsync(() -> {
			MILLISECONDS.sleep(50);
			return 42;
		}, m_executor);

		// 이미 시작된 Execution을 submit하는 경우, 정상적으로 결과를 수집해야 한다.
		m_completions.submit(exec);

		Result<Integer> result = m_completions.next().get();
		assertEquals(42, (int)result.get());
	}

	@Test
	public void test7_submitNotStartedNonStartable() throws Exception {
		// 시작되지 않은 비-StartableExecution은 IllegalArgumentException을 발생시켜야 한다.
		@SuppressWarnings("unchecked")
		Execution<Integer> exec = org.mockito.Mockito.mock(Execution.class);
		org.mockito.Mockito.when(exec.isStarted()).thenReturn(false);

		assertThrows(IllegalArgumentException.class, () -> m_completions.submit(exec));
	}

	@Test
	public void test8_failedExecutionCarriesCause() throws Exception {
		IllegalArgumentException cause = new IllegalArgumentException("boom");
		m_completions.submit(() -> { throw cause; }, m_executor);

		Result<Integer> result = m_completions.next().get();
		assertTrue(result.isFailed());
		assertSame(cause, result.getCause());
	}

	@Test
	public void test9_endOfSupplyAfterAllConsumed() throws Exception {
		m_completions.submit(() -> 0, m_executor);
		m_completions.submit(() -> 1, m_executor);

		// 적재된 결과 모두 소비 (next()는 결과 또는 종료까지 블록).
		assertTrue(m_completions.next().isPresent());
		assertTrue(m_completions.next().isPresent());

		// 모두 소비한 뒤 endOfSupply 호출.
		m_completions.endOfSupply();

		// 종료된 stream에서 next는 empty 반환.
		FOption<Result<Integer>> ret = m_completions.next(0, MILLISECONDS);
		assertTrue(ret.isAbsent());
	}

	@Test
	public void test10_consumesMoreTasksThanBufferCapacity() throws Exception {
		// buffer capacity(5)보다 많은 task를 submit하면, 초과 task는 worker 쓰레드의 supply에서
		// 소비자의 next 호출을 기다리며 블록된 뒤 차례로 적재되어야 한다.
		for ( int i = 0; i < 6; ++i ) {
			final int v = i;
			m_completions.submit(() -> v, m_executor);
		}

		int total = 0;
		for ( int i = 0; i < 6; ++i ) {
			Result<Integer> r = m_completions.next().get();
			assertTrue(r.isSuccessful());
			total += r.get();
		}
		assertEquals(0 + 1 + 2 + 3 + 4 + 5, total);
	}
}
