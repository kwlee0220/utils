package utils.stream;


import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IteratorTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4);
		
		Iterator<Integer> iter = stream.iterator();

		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(1, (int)iter.next());
		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(2, (int)iter.next());
		Assertions.assertTrue(iter.hasNext());
		Assertions.assertEquals(4, (int)iter.next());
		Assertions.assertFalse(iter.hasNext());
	}
	
	@Test
	public void test1() throws Exception {
		Assertions.assertThrows(NoSuchElementException.class, () -> {
			FStream<Integer> stream = FStream.empty();
		
			Iterator<Integer> iter = stream.iterator();

			Assertions.assertFalse(iter.hasNext());
			iter.next();
			});
	}

	/**
	 * null 원소도 그대로 순회한다(2026-11-02 이전에는 null에서 순회가 끝났다). stream()도 같다.
	 */
	@Test
	public void nullElementIsNotEndOfStream() throws Exception {
		java.util.List<String> seen = new java.util.ArrayList<>();
		for ( String s: FStream.of("a", null, "c") ) {
			seen.add(s);
		}
		Assertions.assertEquals(java.util.Arrays.asList("a", null, "c"), seen);
		Assertions.assertEquals(3, FStream.of("a", null, "c").stream().count());
	}

	/**
	 * 끝까지 순회하면 원본을 닫는다. 처음부터 빈 스트림도 같다(이전에는 닫지 않았다).
	 */
	@Test
	public void closesSourceAtEnd() throws Exception {
		boolean[] closed = new boolean[1];
		for ( Integer i: FStream.<Integer>empty().onClose(() -> closed[0] = true) ) {
			Assertions.fail("unexpected element: " + i);
		}
		Assertions.assertTrue(closed[0], "empty stream not closed");

		closed[0] = false;
		Iterator<Integer> iter = FStream.of(1, 2).onClose(() -> closed[0] = true).iterator();
		iter.next();
		Assertions.assertFalse(closed[0]);
		iter.next();
		Assertions.assertFalse(iter.hasNext());
		Assertions.assertTrue(closed[0]);
	}

	/**
	 * 원소를 미리 읽지 않는다. iterator()를 만들거나 next()로 꺼내도 그다음 원소는 아직 읽지 않는다.
	 */
	@Test
	public void readsLazily() throws Exception {
		int[] pulled = new int[1];
		Iterator<Integer> iter = FStream.of(1, 2, 3).peek(v -> ++pulled[0]).iterator();
		Assertions.assertEquals(0, pulled[0]);
		Assertions.assertEquals(1, (int)iter.next());
		Assertions.assertEquals(1, pulled[0]);
	}
}
