package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import utils.func.FOption;


/**
 * {@code null} 원소를 데이터로 다뤄야 하는 연산({@code shuffle}, {@code distinctUntilChanged}, {@code min}/{@code max},
 * {@code dropLast})을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class NullElementTest {
	@Test
	public void shuffle_keeps_null_elements() {
		List<Integer> shuffled = FStream.of(1, null, 2, null).shuffle().toList();
		assertEquals(4, shuffled.size());
		assertEquals(2, shuffled.stream().filter(v -> v == null).count());
		assertTrue(shuffled.containsAll(Arrays.asList(1, 2)));
	}

	@Test
	public void shuffle_throws_after_close() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2).shuffle();
		stream.next();
		stream.close();
		assertThrows(IllegalStateException.class, stream::next);
	}

	@Test
	public void distinct_until_changed_collapses_adjacent_nulls() {
		assertEquals(Arrays.asList(null, 1, null),
					FStream.of(null, null, 1, 1, null, null).distinctUntilChanged().toList());
		assertEquals(Arrays.asList("a", null),
					FStream.of("a", "b", null, null)
							.distinctUntilChanged(s -> s == null ? null : "x").toList());
	}

	@Test
	public void max_and_min_return_a_present_null_when_the_comparator_picks_it() {
		Comparator<Integer> nullsLast = Comparator.nullsLast(Comparator.naturalOrder());
		FOption<Integer> max = FStream.of(1, null).max(nullsLast);
		assertTrue(max.isPresent());
		assertEquals(null, max.getOrNull());

		Comparator<Integer> nullsFirst = Comparator.nullsFirst(Comparator.naturalOrder());
		FOption<Integer> min = FStream.of(1, null, 2).min(nullsFirst);
		assertTrue(min.isPresent());
		assertEquals(null, min.getOrNull());

		assertTrue(FStream.<Integer>empty().max(nullsLast).isAbsent());
		assertEquals(2, (int)FStream.of(1, null, 2).max(nullsFirst).get());
	}

	@Test
	public void natural_order_max_rejects_null_elements() {
		assertThrows(NullPointerException.class, () -> FStream.of(null, 1).max());
	}

	@Test
	public void drop_last_keeps_null_elements() {
		assertEquals(Arrays.asList(null, 1), FStream.of(null, 1, null, 2).dropLast(2).toList());
		assertEquals(Arrays.asList(), FStream.of(1, 2).dropLast(Integer.MAX_VALUE).toList());
	}
}
