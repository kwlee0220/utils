package utils.stream;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UniqueTest {
	@Test
	public void test0() throws Exception {
		List<Integer> ret = FStream.of(1, 2, 2, 2, 1, 1, 1, 3, 3)
										.unique()
										.toList();
		List<Integer> answer = Arrays.asList(1, 2, 1, 3);
		Assert.assertEquals(answer, ret);
	}
	
	@Test
	public void test1() throws Exception {
		List<Integer> ret = FStream.of(5, 5, 5).distinct().toList();
		List<Integer> answer = Arrays.asList(5);
		Assert.assertEquals(answer, ret);
	}
	
	@Test
	public void test2() throws Exception {
		List<Object> ret = FStream.from(Arrays.asList()).distinct().toList();
		List<Object> answer = Arrays.asList();
		Assert.assertEquals(answer, ret);
	}
	
	@Test
	public void test10() throws Exception {
		List<String> ret = FStream.of("a", "be", "ca", "the", "an")
										.unique(String::length)
										.toList();
		List<String> answer = Arrays.asList("a", "be", "the", "an");
		Assert.assertEquals(answer, ret);
	}
}
