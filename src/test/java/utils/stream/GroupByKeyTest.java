package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * {@link KeyValueFStream#groupByKey()}·{@link KeyValueFStream#toListMap()}의 닫기와 {@code null} 값 처리를 검증한다.
 * <p>
 * 2026-11-02 이전에는 {@code toListMap()}이 도중 예외에서 원본을 닫지 않았고, {@code groupByKey()}는 {@code null}
 * 값을 거부해 두 연산의 정책이 달랐다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class GroupByKeyTest {
	private static KeyValueFStream<String,Integer> source() {
		return KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", 2), KeyValue.of("a", 3));
	}

	@Test
	public void group_by_key_and_to_list_map_give_the_same_groups_in_stream_order() {
		Map<String,List<Integer>> expected = Map.of("a", List.of(1, 3), "b", List.of(2));
		assertEquals(expected, source().toListMap());
		assertEquals(expected, source().groupByKey().asMap());
	}

	@Test
	public void null_values_are_kept_by_both() {
		KeyValueFStream<String,Integer> withNull = KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("a", null));
		assertEquals(Arrays.asList(1, null), withNull.toListMap().get("a"));

		withNull = KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("a", null));
		assertEquals(Arrays.asList(1, null), withNull.groupByKey().get("a"));
	}

	@Test
	public void to_list_map_closes_the_source_when_it_fails_midway() {
		AtomicBoolean closed = new AtomicBoolean(false);
		KeyValueFStream<String,Integer> failing
			= KeyValueFStream.from(source().map(kv -> {
											if ( kv.value() == 2 ) {
												throw new IllegalStateException("boom");
											}
											return kv;
										})
										.onClose(() -> closed.set(true)));
		assertThrows(IllegalStateException.class, failing::toListMap);
		assertTrue(closed.get(), "the source must be closed on failure");
	}

	@Test
	public void group_by_key_closes_the_source_when_it_fails_midway() {
		AtomicBoolean closed = new AtomicBoolean(false);
		KeyValueFStream<String,Integer> failing
			= KeyValueFStream.from(source().map(kv -> {
											if ( kv.value() == 2 ) {
												throw new IllegalStateException("boom");
											}
											return kv;
										})
										.onClose(() -> closed.set(true)));
		assertThrows(IllegalStateException.class, failing::groupByKey);
		assertTrue(closed.get(), "the source must be closed on failure");
	}
}
