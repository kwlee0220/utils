package utils.func;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.Tuple;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FuncTest {

	@Test
	public void testReplaceFirst1_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 3);

		Function<Integer,Integer> func = v -> v + 1;
		Integer old = Funcs.replaceFirst(list, i -> i == 3, func);
		assertEquals((int)old, 3);
		assertEquals(List.of(1, 2, 4, 4, 3), list);
	}

	@Test
	public void testReplaceFirst1_2() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);

		Function<Integer,Integer> func = v -> v + 1;
		Integer old = Funcs.replaceFirst(list, i -> i == 7, func);
		assertEquals(old, null);
	}

	@Test
	public void testReplaceFirst2_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);

		Integer old = Funcs.replaceFirst(list, i -> i == 3, -1);
		assertEquals((int)old, 3);
		assertEquals(-1, (int)list.get(2));
	}

	@Test
	public void testReplaceOrInsertFirst_1() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);

		Function<Integer,Integer> func = v -> v + 1;
		int old = Funcs.replaceOrInsertFirst(list, i -> i == 3, func);
		assertEquals(old, 3);
		assertEquals(4, (int)list.get(2));
	}

	@Test
	public void testReplaceOrInsertFirst_2() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 1);

		Function<Integer,Integer> func = v -> -7;
		Integer old = Funcs.replaceOrInsertFirst(list, i -> i == 7, func);
		assertEquals(old, null);
		assertEquals(3, (int)list.get(2));
		assertEquals(7, list.size());
		assertEquals(-7, (int)list.get(6));
	}

	@Test
	public void testReplaceFirst2_2_RejectsNullList() throws Exception {
		assertThrows(IllegalArgumentException.class,
						() -> Funcs.replaceFirst(null, i -> i == 3, -1));
	}

	@Test
	public void testReplaceFirst2_3_RejectsNullPredicate() throws Exception {
		assertThrows(IllegalArgumentException.class,
						() -> Funcs.replaceFirst(Lists.newArrayList(1, 2, 3), null, -1));
	}

	@Test
	public void testAcceptIfPresent_CallsConsumerForNullValue() throws Exception {
		Map<String,Integer> map = new HashMap<>();
		map.put("a", null);
		AtomicReference<Tuple<String,Integer>> invoked = new AtomicReference<>();

		Funcs.acceptIfPresent(map, "a", (key, value) -> invoked.set(Tuple.of(key, value)));

		assertEquals(Tuple.of("a", null), invoked.get());
	}

	@Test
	public void testInnerJoin_IncludesRightNullValueWhenKeyExists() throws Exception {
		Map<String,Integer> left = new HashMap<>();
		left.put("a", 1);
		left.put("b", 2);

		Map<String,Integer> right = new HashMap<>();
		right.put("b", null);
		right.put("c", 3);

		Map<String,Tuple<Integer,Integer>> joined = Funcs.innerJoin(left, right);

		assertEquals(1, joined.size());
		assertEquals(Tuple.of(2, null), joined.get("b"));
	}

	@Test
	public void testArgmax_EmptyListReturnsMinusOne() throws Exception {
		assertEquals(-1, Funcs.argmax(List.of(), Integer::intValue));
	}

	@Test
	public void testArgmin_EmptyListReturnsMinusOne() throws Exception {
		assertEquals(-1, Funcs.argmin(List.of(), Integer::intValue));
	}
}
