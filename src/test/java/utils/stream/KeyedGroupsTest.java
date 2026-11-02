package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * {@link KeyedGroups}가 내부 상태를 읽기 전용 뷰로만 노출하고, 처음 나온 순서를 유지하는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedGroupsTest {
	private static KeyedGroups<String,Integer> groups() {
		return KeyValueFStream.of(KeyValue.of("c", 1), KeyValue.of("a", 2), KeyValue.of("b", 3),
									KeyValue.of("a", 4))
								.groupByKey();
	}

	@Test
	public void views_are_read_only() {
		KeyedGroups<String,Integer> groups = groups();

		assertThrows(UnsupportedOperationException.class, () -> groups.asMap().put("z", null));
		assertThrows(UnsupportedOperationException.class, () -> groups.asMap().put("e", List.of()));
		assertThrows(UnsupportedOperationException.class, () -> groups.asMap().get("a").add(9));
		assertThrows(UnsupportedOperationException.class, () -> groups.get("a").clear());
		assertThrows(UnsupportedOperationException.class,
					() -> groups.getOrDefault("a", Collections.emptyList()).add(9));
		assertThrows(UnsupportedOperationException.class, () -> groups.keySet().remove("a"));

		assertEquals(4, groups.size());
		assertEquals(3, groups.groupCount());
	}

	@Test
	public void as_map_is_a_live_view() {
		KeyedGroups<String,Integer> groups = groups();
		var view = groups.asMap();

		groups.add("d", 5);
		assertEquals(List.of(5), view.get("d"));
		groups.remove("c");
		assertEquals(List.of("a", "b", "d"), List.copyOf(view.keySet()));
	}

	@Test
	public void groups_keep_first_appearance_order() {
		assertEquals(List.of("c", "a", "b"), List.copyOf(groups().keySet()));
		assertEquals(List.of("c", "a", "b"), groups().fstream().keys().toList());
		assertEquals(List.of(KeyValue.of("c", 1), KeyValue.of("a", 2), KeyValue.of("a", 4), KeyValue.of("b", 3)),
					groups().ungroup().toList());
	}

	@Test
	public void get_returns_null_and_get_or_default_returns_the_default_for_a_missing_key() {
		KeyedGroups<String,Integer> groups = groups();
		List<Integer> fallback = List.of();

		assertNull(groups.get("x"));
		assertSame(fallback, groups.getOrDefault("x", fallback));
		assertEquals(List.of(2, 4), groups.get("a"));
	}

	@Test
	public void fstream_exposes_the_internal_lists_for_in_place_updates() {
		// marmot InMemoryKeyedRecordSetFactory가 값 목록을 제자리에서 정렬한다. 이 사용을 깨지 않는다.
		KeyedGroups<String,Integer> groups = KeyValueFStream.of(KeyValue.of("a", 3), KeyValue.of("a", 1))
															.groupByKey();
		groups.fstream().forEach(kv -> kv.value().sort(null));
		assertEquals(List.of(1, 3), groups.get("a"));
	}
}
