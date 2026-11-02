package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


/**
 * {@link FStream#takeTopK} 오버로드를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TakeTopKTest {
	@Test
	public void natural_order_defaults_to_the_largest_k_in_descending_order() {
		assertEquals(Arrays.asList(5, 4), FStream.of(5, 1, 3, 4).takeTopK(2).toList());
		assertEquals(Arrays.asList(5, 4), FStream.of(5, 1, 3, 4).takeTopK(2, SortOrder.DESCENDING).toList());
	}

	@Test
	public void ascending_picks_the_smallest_k_in_ascending_order() {
		assertEquals(Arrays.asList(1, 3), FStream.of(5, 1, 3, 4).takeTopK(2, SortOrder.ASCENDING).toList());
	}

	@Test
	public void keyer_defaults_to_the_largest_keys() {
		assertEquals(Arrays.asList("ccc", "bb"),
					FStream.of("bb", "a", "ccc").takeTopK(2, String::length).toList());
		assertEquals(Arrays.asList("a", "bb"),
					FStream.of("bb", "a", "ccc").takeTopK(2, String::length, SortOrder.ASCENDING).toList());
	}

	@Test
	public void comparator_overload_keeps_the_comparator_order() {
		// marmot 등 기존 호출자는 이 의미(sort(cmp).take(k))에 맞춰 비교자를 넘긴다.
		assertEquals(Arrays.asList(1, 3), FStream.of(5, 1, 3, 4).takeTopK(2, Comparator.naturalOrder()).toList());
		assertEquals(Arrays.asList(5, 4),
					FStream.of(5, 1, 3, 4).takeTopK(2, (a, b) -> Integer.compare(b, a)).toList());
	}

	@Test
	public void fewer_elements_than_k_are_all_returned_in_order() {
		assertEquals(Arrays.asList(3, 2, 1), FStream.of(2, 3, 1).takeTopK(10).toList());
	}

	@Test
	public void zero_k_yields_an_empty_stream_without_reading_the_source() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		FStream<Integer> top = FStream.of(1, 2, 3).peek(v -> reads.incrementAndGet()).takeTopK(0);
		assertTrue(top.next().isAbsent());
		assertEquals(0, reads.get());
		top.close();
	}

	@Test
	public void the_source_is_read_lazily_and_closed_after_reading() throws Exception {
		AtomicBoolean closed = new AtomicBoolean(false);
		AtomicInteger reads = new AtomicInteger();
		FStream<Integer> top = FStream.of(1, 2, 3)
										.peek(v -> reads.incrementAndGet())
										.onClose(() -> closed.set(true))
										.takeTopK(2);
		assertEquals(0, reads.get(), "nothing is read before the first next()");
		assertFalse(closed.get());

		assertEquals(3, (int)top.next().get());
		assertEquals(3, reads.get());
		assertTrue(closed.get());
		top.close();
	}

	@Test
	public void closing_before_reading_closes_the_source() throws Exception {
		AtomicBoolean closed = new AtomicBoolean(false);
		FStream<Integer> top = FStream.of(1, 2, 3).onClose(() -> closed.set(true)).takeTopK(2);
		top.close();
		assertTrue(closed.get());
	}

	@Test
	public void null_elements_are_allowed_when_the_comparator_handles_them() {
		assertEquals(Arrays.asList(null, 1),
					FStream.of(2, null, 1).takeTopK(2, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()))
							.toList());
	}

	@Test
	public void empty_stream_and_invalid_arguments() {
		assertEquals(Collections.emptyList(), FStream.<Integer>empty().takeTopK(3).toList());
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).takeTopK(-1));
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).takeTopK(1, (SortOrder)null));
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).takeTopK(1, (Comparator<Integer>)null));
	}

	@Test
	public void next_after_close_throws_like_any_other_stream() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 3).takeTopK(3);
		assertEquals(3, (int)stream.next().get());
		stream.close();
		assertThrows(IllegalStateException.class, stream::next);
	}
}
