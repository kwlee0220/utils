package utils.stream;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class QuasiSortTest {
	@Test
	public void test1() throws Exception {
		List<Integer> result;
		
		result = FStream.from(Arrays.<Integer>asList()).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(), result);
		
		result = FStream.from(Arrays.<Integer>asList(7)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(7), result);
		
		result = FStream.from(Arrays.<Integer>asList(7, 5)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(5, 7), result);
		
		result = FStream.from(Arrays.<Integer>asList(7, 5, 3, 1)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(3, 1, 5, 7), result);
	}
	
	@Test
	public void test2() throws Exception {
		List<Integer> result;
		
		result = FStream.from(Arrays.<Integer>asList()).quasiSort(2, v -> v).toList();
		Assert.assertEquals(Arrays.asList(), result);
		
		result = FStream.from(Arrays.<Integer>asList(7)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(7), result);
		
		result = FStream.from(Arrays.<Integer>asList(7, 5)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(5, 7), result);
		
		result = FStream.from(Arrays.<Integer>asList(7, 5, 3, 1)).quasiSort(2).toList();
		Assert.assertEquals(Arrays.asList(3, 1, 5, 7), result);
	}
	
	@Test
	public void test3() throws Exception {
		List<Integer> result;
		
		List<Integer> input = Arrays.<Integer>asList(7, 5, 3, 1, 2, 9);
		result = FStream.from(input)
						.quasiSort(2, (v1,v2)->Integer.compare(v1, v2))
						.toList();
		Assert.assertEquals(Arrays.asList(3, 1, 2, 5, 7, 9), result);

		result = FStream.from(input)
						.quasiSort(2, (v1,v2)->Integer.compare(v2, v1))
						.toList();
		Assert.assertEquals(Arrays.asList(7, 5, 3, 9, 2, 1), result);
	}
	
	@Test
	public void test4() throws Exception {
		List<Integer> result;
		
		List<Integer> input = Arrays.<Integer>asList(7, 5, 3, 1, 2, 9);
		result = FStream.from(input)
						.quasiSort(2, v -> v)
						.toList();
		Assert.assertEquals(Arrays.asList(3, 1, 2, 5, 7, 9), result);

		result = FStream.from(input)
						.quasiSort(2, v -> -v)
						.toList();
		Assert.assertEquals(Arrays.asList(7, 5, 3, 9, 2, 1), result);

		result = FStream.from(input)
						.quasiSort(2, v -> v, true)
						.toList();
		Assert.assertEquals(Arrays.asList(7, 5, 3, 9, 2, 1), result);
	}
}
