package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * 원소 타입이 그대로인 중간 연산이 {@link KeyValueFStream}을 반환하는지 검증한다.
 * <p>
 * 대입문 자체가 검증 대상이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueIntermediateOpsTest {
	private static KeyValueFStream<String,Integer> kvs() {
		return KeyValueFStream.of(KeyValue.of("c", 3), KeyValue.of("a", 1), KeyValue.of("b", 2),
									KeyValue.of("a", 1));
	}

	private static List<String> keys(KeyValueFStream<String,Integer> s) {
		return s.keys().toList();
	}

	@Test
	public void filterVariants() throws Exception {
		KeyValueFStream<String,Integer> r1 = kvs().filter(kv -> kv.value() > 1);
		assertEquals(List.of("c", "b"), keys(r1));

		KeyValueFStream<String,Integer> r2 = kvs().filter((k,v) -> k.equals("a") && v == 1);
		assertEquals(List.of("a", "a"), keys(r2));

		KeyValueFStream<String,Integer> r3 = kvs().filterNot(kv -> kv.value() > 1);
		assertEquals(List.of("a", "a"), keys(r3));
	}

	@Test
	public void takeDropAndWhile() throws Exception {
		KeyValueFStream<String,Integer> take = kvs().take(2);
		assertEquals(List.of("c", "a"), keys(take));
		KeyValueFStream<String,Integer> drop = kvs().drop(2);
		assertEquals(List.of("b", "a"), keys(drop));
		KeyValueFStream<String,Integer> tw = kvs().takeWhile(kv -> kv.value() > 2);
		assertEquals(List.of("c"), keys(tw));
		KeyValueFStream<String,Integer> dw = kvs().dropWhile(kv -> kv.value() > 2);
		assertEquals(List.of("a", "b", "a"), keys(dw));
	}

	@Test
	public void sortDistinctAndSortByKey() throws Exception {
		KeyValueFStream<String,Integer> sorted = kvs().sort(Comparator.comparing(KeyValue::value));
		assertEquals(List.of("a", "a", "b", "c"), keys(sorted));

		KeyValueFStream<String,Integer> distinct = kvs().distinct();
		assertEquals(List.of("c", "a", "b"), keys(distinct));

		KeyValueFStream<String,Integer> byKeyDesc = kvs().sortByKey(Comparator.reverseOrder());
		assertEquals(List.of("c", "b", "a", "a"), keys(byKeyDesc));
	}

	@Test
	public void onCloseRunsAndChainsAsKeyValueStream() throws Exception {
		AtomicBoolean closed = new AtomicBoolean();
		KeyValueFStream<String,Integer> s = kvs().onClose(() -> closed.set(true));
		assertEquals(List.of(3, 1, 2, 1), s.filter((k,v) -> true).values().toList());
		assertTrue(closed.get());
	}

	@Test
	public void closingResultClosesSource() throws Exception {
		AtomicBoolean closed = new AtomicBoolean();
		KeyValueFStream<String,Integer> s = kvs().onClose(() -> closed.set(true))
												.filter((k,v) -> v > 1)
												.take(1);
		s.close();
		assertTrue(closed.get());
	}

	@Test
	public void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> kvs().filter((BiPredicate<String,Integer>)null));
		assertThrows(IllegalArgumentException.class, () -> kvs().sortByKey((Comparator<String>)null));
	}
}
