package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * {@link KeyValueFStream#reduceByKey(BiFunction)}와 {@link KeyValueFStream#toMap(Map, BiFunction)}을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueReduceTest {
	private static KeyValueFStream<String,Integer> kvs() {
		return KeyValueFStream.of(KeyValue.of("b", 1), KeyValue.of("a", 2), KeyValue.of("b", 3),
									KeyValue.of("c", 4), KeyValue.of("a", 5));
	}

	@Test
	public void reduceByKeyKeepsFirstSeenOrder() throws Exception {
		Map<String,Integer> sums = kvs().reduceByKey(Integer::sum);
		assertEquals(Map.of("b", 4, "a", 7, "c", 4), sums);
		assertEquals(List.of("b", "a", "c"), List.copyOf(sums.keySet()));
	}

	@Test
	public void toMapWithHashMapAndMerger() throws Exception {
		HashMap<String,Integer> max = kvs().toMap(new HashMap<>(), Math::max);
		assertEquals(Map.of("b", 3, "a", 5, "c", 4), max);
	}

	@Test
	public void toMapWithMapMergesExistingEntries() throws Exception {
		TreeMap<String,Integer> target = new TreeMap<>(Map.of("a", 100));
		TreeMap<String,Integer> result = kvs().toMap(target, Integer::sum);
		assertSame(target, result);
		assertEquals(Map.of("a", 107, "b", 4, "c", 4), result);
	}

	@Test
	public void nullValuesAreMergedNotDropped() throws Exception {
		KeyValueFStream<String,String> s = KeyValueFStream.of(KeyValue.of("k", null), KeyValue.of("k", "x"));
		Map<String,String> map = s.reduceByKey((v1,v2) -> v1 + "+" + v2);
		assertEquals("null+x", map.get("k"));
		KeyValueFStream<String,String> single = KeyValueFStream.of(KeyValue.of("k", (String)null));
		assertTrue(single.reduceByKey((v1,v2) -> v1).containsKey("k"));
	}

	@Test
	public void nullMergeResultIsRejected() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
													() -> kvs().reduceByKey((v1,v2) -> null));
		assertTrue(e.getMessage().contains("returned null for key: b"), e.getMessage());
	}

	@Test
	public void rejectsNullArguments() {
		BiFunction<Integer,Integer,Integer> nullMerger = null;
		assertThrows(IllegalArgumentException.class, () -> kvs().reduceByKey(nullMerger));
		assertThrows(IllegalArgumentException.class, () -> kvs().toMap(new HashMap<>(), nullMerger));
		Map<String,Integer> nullMap = null;
		assertThrows(IllegalArgumentException.class, () -> kvs().toMap(nullMap, Integer::sum));
	}
}
