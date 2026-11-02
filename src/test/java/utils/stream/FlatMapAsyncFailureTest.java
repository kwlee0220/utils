package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link FStream#flatMapAsync}가 실패를 결과 스트림의 오류로 전파하는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class FlatMapAsyncFailureTest {
	private static AsyncExecutionOptions options(boolean keepOrder, int workers) {
		return AsyncExecutionOptions.create().withKeepOrder(keepOrder).withWorkerCount(workers);
	}

	/** 스트림을 끝까지 읽으며 받은 원소를 모으고, 던져진 예외를 반환한다. */
	private static <T> Throwable drain(FStream<T> stream, List<T> received) {
		try ( stream ) {
			while ( true ) {
				var next = stream.next();
				if ( next.isAbsent() ) {
					return null;
				}
				received.add(next.getUnchecked());
			}
		}
		catch ( Throwable e ) {
			return e;
		}
	}

	private static void checkedMapperFailurePropagatesUnwrapped(boolean keepOrder) {
		IOException failure = new IOException("mapper failed");
		FStream<Integer> out = FStream.of(1, 2, 3)
										.flatMapAsync(i -> {
											if ( i == 2 ) {
												throw failure;
											}
											return FStream.of(i * 10);
										}, options(keepOrder, 2));
		Throwable thrown = drain(out, new ArrayList<>());
		assertSame(failure, thrown, "keepOrder=" + keepOrder);
	}

	@Test
	public void checked_mapper_failure_propagates_unwrapped_when_unordered() {
		checkedMapperFailurePropagatesUnwrapped(false);
	}

	@Test
	public void checked_mapper_failure_propagates_unwrapped_when_ordered() {
		checkedMapperFailurePropagatesUnwrapped(true);
	}

	@Test
	public void unchecked_mapper_failure_propagates_in_both_modes() {
		for ( boolean keepOrder: new boolean[] { false, true } ) {
			IllegalStateException failure = new IllegalStateException("mapper failed");
			FStream<Integer> out = FStream.of(1, 2, 3)
											.flatMapAsync(i -> {
												if ( i == 2 ) {
													throw failure;
												}
												return FStream.of(i * 10);
											}, options(keepOrder, 2));
			assertSame(failure, drain(out, new ArrayList<>()), "keepOrder=" + keepOrder);
		}
	}

	@Test
	public void failure_while_reading_an_inner_stream_propagates() {
		IllegalStateException failure = new IllegalStateException("inner stream failed");
		FStream<Integer> out = FStream.of(1)
										.flatMapAsync(i -> FStream.of(1, 2, 3).map(v -> {
											if ( v == 2 ) {
												throw failure;
											}
											return v;
										}), options(false, 1));
		List<Integer> received = new ArrayList<>();
		assertSame(failure, drain(out, received));
		assertEquals(List.of(1), received, "elements produced before the failure are delivered first");
	}

	@Test
	public void results_before_the_failure_are_delivered_then_the_failure() {
		IOException failure = new IOException("second input failed");
		FStream<Integer> out = FStream.of(1, 2)
										.flatMapAsync(i -> {
											if ( i == 2 ) {
												throw failure;
											}
											return FStream.of(10);
										}, options(false, 1));
		List<Integer> received = new ArrayList<>();
		assertSame(failure, drain(out, received));
		assertEquals(List.of(10), received);
	}

	@Test
	public void no_new_input_is_started_after_a_failure() {
		AtomicInteger calls = new AtomicInteger();
		FStream<Integer> out = FStream.range(0, 100)
										.flatMapAsync(i -> {
											calls.incrementAndGet();
											throw new IllegalStateException("fail " + i);
										}, options(false, 1));
		assertTrue(drain(out, new ArrayList<>()) instanceof IllegalStateException);
		assertEquals(1, calls.get());
	}

	@Test
	public void a_mapper_that_handles_its_own_failures_keeps_the_other_results() {
		// 일부 실패를 허용하려면 mapper 안에서 처리한다(marmot RangeQuery와 같은 방식).
		FStream<Integer> out = FStream.of(1, 2, 3)
										.flatMapAsync(i -> (i == 2) ? FStream.<Integer>empty() : FStream.of(i * 10),
														options(true, 2));
		assertEquals(List.of(10, 30), out.toList());
	}
}
