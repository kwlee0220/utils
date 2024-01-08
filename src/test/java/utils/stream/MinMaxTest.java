package utils.stream;


import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MinMaxTest {
	@Test
	public void test0X() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		Assert.assertEquals(4, (int)stream.max().get());
	}
	
	@Test
	public void test1X() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 4);
		Assert.assertEquals(4, (int)stream.max().get());
	}
	
	@Test
	public void test2X() throws Exception {
		FStream<Integer> stream = FStream.of();
		Assert.assertEquals(true, stream.max().isAbsent());
	}
	
	@Test
	public void test10X() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		Assert.assertEquals(4, (int)stream.max((v1,v2) -> v1-v2).get());
	}
	
	@Test
	public void test20X() throws Exception {
		FStream<String> stream = FStream.of("I", "was", "in", "the", "room");
		Assert.assertEquals("room", stream.max(s -> s.length()).get());
	}
	
	@Test
	public void test21X() throws Exception {
		FStream<String> stream = FStream.of("I", "3333", "was", "in", "the", "room");
		Assert.assertEquals(Arrays.asList("3333", "room"), stream.maxMultiple(s -> s.length()));
	}
	
	
	@Test
	public void test0N() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4);
		Assert.assertEquals(1, (int)stream.min().get());
	}
	
	@Test
	public void test1N() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1, 4);
		Assert.assertEquals(1, (int)stream.min().get());
	}
	
	@Test
	public void test2N() throws Exception {
		FStream<Integer> stream = FStream.of();
		Assert.assertEquals(true, stream.min().isAbsent());
	}
	
	@Test
	public void test10N() throws Exception {
		FStream<Integer> stream = FStream.of(1, 2, 4, 1);
		Assert.assertEquals(1, (int)stream.min((v1,v2) -> v1-v2).get());
	}
	
	@Test
	public void test20N() throws Exception {
		FStream<String> stream = FStream.of("I", "was", "in", "the", "room");
		Assert.assertEquals("I", stream.min(s -> s.length()).get());
	}
	
	@Test
	public void test21N() throws Exception {
		FStream<String> stream = FStream.of("3333", "was", "the", "room", "tidy");
		Assert.assertEquals(Arrays.asList("was", "the"), stream.minMultiple(s -> s.length()));
	}
}
