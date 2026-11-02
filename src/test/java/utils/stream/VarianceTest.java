package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import utils.KeyValue;
import utils.Tuple;
import utils.func.Try;


/**
 * 제네릭 변성({@code ? super}/{@code ? extends})을 넓힌 연산들이 이전에는 컴파일되지 않던 호출을 받는지 검증한다.
 * <p>
 * 이 파일이 컴파일된다는 것이 검증의 핵심이다(2026-11-02 이전에는 각 호출이 컴파일 오류였다). 결과 값도 함께 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class VarianceTest {
	private static FStream<Integer> ints() {
		return FStream.of(1, 2);
	}

	@Test
	public void flat_maps_accept_subtype_results() {
		FStream<Number> r1 = ints().flatMap(i -> FStream.of(i, i * 10));
		assertEquals(List.of(1, 10, 2, 20), r1.toList());

		FStream<Number> r2 = ints().flatMapIterable(i -> List.of(i));
		assertEquals(List.of(1, 2), r2.toList());

		FStream<Number> r3 = ints().flatMapArray(i -> new Integer[] { i });
		assertEquals(List.of(1, 2), r3.toList());

		FStream<Number> r4 = ints().flatMapTry(i -> Try.success(i));
		assertEquals(List.of(1, 2), r4.toList());
	}

	@Test
	public void concat_and_starts_with_accept_subtype_streams() {
		FStream<Number> nums = FStream.of(0.5);
		assertEquals(List.of(0.5, 1, 2), nums.concatWith(ints()).toList());
		assertEquals(List.of(0.5, 3), FStream.<Number>of(0.5).concatWith(List.of(3)).toList());
		assertTrue(FStream.<Number>of(1, 2, 3).startsWith(ints()));
	}

	@Test
	public void keyers_may_take_a_supertype() {
		Function<Object,Integer> hash = o -> o.hashCode() % 2;
		assertEquals(List.of(1, 2), ints().distinct(hash).toList());

		Function<Object,String> str = Object::toString;
		assertEquals(List.of(2), ints().maxMultiple(str));
		assertEquals(List.of(1), ints().minMultiple(str));
	}

	@Test
	public void lift_and_lookup_accept_wildcard_functions() {
		Function<FStream<? extends Number>, FStream<String>> f = s -> s.map(Object::toString);
		assertEquals(List.of("1", "2"), ints().lift(f).toList());

		Function<Object,String> lookup = o -> "v" + o;
		assertEquals(List.of(KeyValue.of(1, "v1"), KeyValue.of(2, "v2")),
					ints().lookup(lookup, false).toList());

		Map<Number,String> lut = Map.of(1, "one");
		assertEquals(List.of(KeyValue.of(1, "one")), ints().lookup(lut).toList());

		Function<Object,KeyValue<String,Object>> toKv = o -> KeyValue.of(o.toString(), o);
		assertEquals(List.of(KeyValue.of("1", 1), KeyValue.of("2", 2)), ints().mapToKeyValue(toKv).toList());
	}

	@Test
	public void key_value_match_from_and_to_map_accept_wider_types() {
		Map<CharSequence,Integer> lut = Map.of("a", 1);
		KeyValueFStream<String,String> kvs = KeyValueFStream.of(KeyValue.of("a", "A"), KeyValue.of("b", "B"));
		assertEquals(List.of(KeyValue.of("a", Tuple.of("A", 1))), kvs.match(lut).toList());

		Map<CharSequence,List<Integer>> lut2 = Map.of("a", List.of(1, 2));
		kvs = KeyValueFStream.of(KeyValue.of("a", "A"));
		assertEquals(2, kvs.flatMatch(lut2).count());

		List<KeyValue<String,Integer>> list = List.of(KeyValue.of("a", 1));
		KeyValueFStream<String,Number> widened = KeyValueFStream.from(list);
		assertEquals(List.of(KeyValue.of("a", 1)), widened.toList());

		KeyValueFStream<String,Number> widened2 = KeyValueFStream.from(FStream.from(list));
		HashMap<CharSequence,Object> target = widened2.toMap(new HashMap<CharSequence,Object>());
		assertEquals(Map.of("a", 1), target);

		Function<KeyValueFStream<? extends String,? extends Number>, KeyValueFStream<String,String>> f
			= s -> KeyValueFStream.from(s.map(kv -> KeyValue.of(kv.key(), kv.value().toString())));
		assertEquals(List.of(KeyValue.of("a", "1")), KeyValueFStream.from(list).liftKeyValues(f).toList());
	}
}
