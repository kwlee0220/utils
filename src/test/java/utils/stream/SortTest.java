package utils.stream;


import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SortTest {
	@Test
	public void test0() throws Exception {
		Random random = new Random(System.currentTimeMillis());
		
		List<Integer> list = FStream.unfold(random, ran -> Tuple.of(ran, ran.nextInt(10)))
									.take(30).sort().toList();
		Assertions.assertEquals(30, list.size());
		
		int last = -1;
		for ( int i = 0; i < list.size(); ++i ) {
			Assertions.assertTrue(list.get(i)>= last);
			last = list.get(i);
		}
	}
	
	@Test
	public void test1() throws Exception {
		Random random = new Random(System.currentTimeMillis());
		
		List<Integer> list = FStream.unfold(random, ran -> Tuple.of(ran, ran.nextInt(10)))
									.take(30).sort((i,j) -> j-i).toList();
		Assertions.assertEquals(30, list.size());
		
		int last = Integer.MAX_VALUE;
		for ( int i = 0; i < list.size(); ++i ) {
			Assertions.assertTrue(list.get(i)<= last);
			last = list.get(i);
		}
	}
	
	@Test
	public void test2() throws Exception {
		Random random = new Random(System.currentTimeMillis());

		List<Integer> list = FStream.unfold(random, ran -> Tuple.of(ran, ran.nextInt(10)))
									.take(30).toList();
		
		List<Integer> sorted1 = list.stream().sorted().collect(Collectors.toList());
		List<Integer> sorted2 = FStream.from(list).sort().toList();
		Assertions.assertEquals(sorted1, sorted2);
		
		sorted1 = list.stream().sorted((i,j) -> j-i).collect(Collectors.toList());
		sorted2 = FStream.from(list).sort((i,j) -> j-i).toList();
		Assertions.assertEquals(sorted1, sorted2);
		
		sorted1 = list.stream().sorted((i,j) -> i-j).collect(Collectors.toList());
		sorted2 = FStream.from(list).sort((i,j) -> i-j).toList();
		Assertions.assertEquals(sorted1, sorted2);
	}

	@Test
	public void sort_order_selects_the_direction() {
		Assertions.assertEquals(Arrays.asList(1, 3, 4, 5), FStream.of(5, 1, 3, 4).sort().toList());
		Assertions.assertEquals(Arrays.asList(1, 3, 4, 5), FStream.of(5, 1, 3, 4).sort(SortOrder.ASCENDING).toList());
		Assertions.assertEquals(Arrays.asList(5, 4, 3, 1), FStream.of(5, 1, 3, 4).sort(SortOrder.DESCENDING).toList());
	}

	@Test
	public void sort_by_key_defaults_to_ascending_and_accepts_an_order() {
		Assertions.assertEquals(Arrays.asList("a", "bb", "ccc"),
								FStream.of("bb", "ccc", "a").sort(String::length).toList());
		Assertions.assertEquals(Arrays.asList("ccc", "bb", "a"),
								FStream.of("bb", "ccc", "a").sort(String::length, SortOrder.DESCENDING).toList());
	}

	@Test
	public void quasi_sort_order_selects_the_direction() {
		// 큐 길이가 입력보다 길면 정확히 정렬된다.
		Assertions.assertEquals(Arrays.asList(1, 3, 4, 5), FStream.of(5, 1, 3, 4).quasiSort(10).toList());
		Assertions.assertEquals(Arrays.asList(5, 4, 3, 1),
								FStream.of(5, 1, 3, 4).quasiSort(10, SortOrder.DESCENDING).toList());
	}

	@Test
	public void null_order_is_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> FStream.of(1).sort((SortOrder)null));
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> FStream.of("a").sort(String::length, (SortOrder)null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> FStream.of(1).quasiSort(2, (SortOrder)null));
	}
}
