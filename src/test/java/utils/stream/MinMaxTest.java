package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MinMaxTest {
	@Test
	public void test0M() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		
		List<Integer> max = stream.maxMultiple();
		Assert.assertEquals(1, max.size());
		Assert.assertEquals(4, (int)max.get(0));
	}
	@Test
	public void test0S() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		
		int max = stream.max().get();
		Assert.assertEquals(4, max);
	}
	
	@Test
	public void test1S() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(null, stream.max());
	}
	@Test
	public void test1M() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.maxMultiple().isEmpty());
	}
	
	@Test
	public void test2S() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 4);
		
		int max = stream.max().get();
		Assert.assertEquals(4, max);
	}
	@Test
	public void test2M() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 4);
		
		List<Integer> max = stream.maxMultiple();
		Assert.assertEquals(4, (int)max.get(0));
		Assert.assertEquals(2, max.size());
	}
	
	@Test
	public void test3S() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, 4, 1);

		int max = stream.max().get();
		Assert.assertEquals(5, max);
	}
	
	@Test
	public void test3M() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, 4, 1);

		List<Integer> max = stream.maxMultiple();
		Assert.assertEquals(1, max.size());
		Assert.assertEquals(5, (int)max.get(0));
	}

	@Test
	public void test4S() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(null, stream.max());
	}
	@Test
	public void test4M() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.maxMultiple().isEmpty());
	}
	
	@Test
	public void test10S() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);

		int min = stream.min().get();
		Assert.assertEquals(1, min);
	}
	@Test
	public void test10M() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);

		List<Integer> min = stream.minMultiple();
		Assert.assertEquals(2, min.size());
		Assert.assertEquals(1, (int)min.get(0));
	}

	@Test
	public void test11S() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(null, stream.min());
	}
	@Test
	public void test11M() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.minMultiple().isEmpty());
	}

	@Test
	public void test12S() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, -2, 1);


		int min = stream.min().get();
		Assert.assertEquals(-2, min);
	}
	@Test
	public void test12M() throws Exception {
		FStream<Integer> stream = FStream.of(1, 5, -2, 1);


		List<Integer> min = stream.minMultiple();
		Assert.assertEquals(1, min.size());
		Assert.assertEquals(-2, (int)min.get(0));
	}

	@Test
	public void test13S() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.min().isAbsent());
	}
	@Test
	public void test13M() throws Exception {
		FStream<Integer> stream = FStream.of();
		
		Assert.assertEquals(true, stream.minMultiple().isEmpty());
	}
}
