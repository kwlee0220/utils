package utils.stream;


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
}
