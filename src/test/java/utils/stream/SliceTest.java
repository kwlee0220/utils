package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.Slice;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SliceTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));

		List<Integer> r;
		Slice slice;
		
		slice = Slice.builder().build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), r);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));

		List<Integer> r;
		Slice slice;
		
		slice = Slice.builder().start(5).build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(5, 6, 7), r);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));

		List<Integer> r;
		Slice slice;
		
		slice = Slice.builder().end(5).build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(0, 1, 2, 3, 4), r);
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));

		List<Integer> r;
		Slice slice;
		
		slice = Slice.builder().step(3).build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(0, 3, 6), r);
		
		stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));
		slice = Slice.builder().step(3).end(6).build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(0, 3), r);
		
		stream = FStream.from(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));
		slice = Slice.builder().start(1).step(3).end(6).build();
		r = stream.slice(slice).toList();
		Assert.assertEquals(List.of(1, 4), r);
	}
}
