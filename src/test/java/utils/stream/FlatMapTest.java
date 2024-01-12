package utils.stream;


import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapTest {
	private String toString(FStream<Integer> strm) {
		return strm.map(v -> "" + v).join("");
	}
	
	@Test
	public void test0() throws Exception {
		List<FStream<Integer>> strmList = Arrays.asList(FStream.of(0, 1, 2), FStream.of(3, 4),
														FStream.of(5), FStream.of(6, 7),
														FStream.empty());
		
		FStream<Integer> strm = FStream.of(0, 1, 2, 3, 4);
		FStream<Integer> stream = strm.flatMap(i -> strmList.get(i));
		
		String ret = toString(stream);
		Assert.assertEquals("01234567", ret);
	}
	
	@Test
	public void test1() throws Exception {
		List<FStream<Integer>> strmList = Arrays.asList(FStream.empty(), FStream.empty());
	
		FStream<Integer> strm = FStream.of(0, 1);
		FStream<Integer> stream = strm.flatMap(i -> strmList.get(i));
		
		String ret = toString(stream);
		Assert.assertEquals("", ret);
	}
	
	@Test
	public void test2() throws Exception {
		List<FStream<Integer>> strmList = Lists.newArrayList();
		
		strmList.add(FStream.from(Lists.newArrayList(0, 1, 2)));
		
		FStream<Integer> strm = FStream.of(0);
		FStream<Integer> stream = strm.flatMap(i -> strmList.get(i));

		FOption<Integer> r;
		for ( int i =0; i <= 2; ++i ) {
			r = stream.next();
			Assert.assertEquals(true, r.isPresent());
			Assert.assertEquals(Integer.valueOf(i), r.get());
		}
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		List<FStream<Integer>> strmList = Lists.newArrayList();
		
		strmList.add(FStream.from(Lists.newArrayList(0, 1, 2)));
		
		FStream<Integer> strm = FStream.of(0);
		strm.flatMap(null);
	}
	
	@Test
	public void test4() throws Exception {
		FStream<Integer> strm = FStream.of(0, 1, 2, 3);
		FStream<Integer> stream = strm.flatMapNullable(i -> i % 2 == 0 ? i : null);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals((long)0, (long)r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals((long)2, (long)r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test5() throws Exception {
		List<List<Integer>> strmList = Arrays.asList(Arrays.asList(0, 1, 2), Arrays.asList(3, 4),
												Arrays.asList(5), Arrays.asList(6, 7),
												Arrays.asList());
		
		FStream<Integer> strm = FStream.of(0, 1, 2, 3, 4);
		FStream<Integer> stream = strm.flatMap(i -> FStream.from(strmList.get(i)));
		
		String ret = toString(stream);
		Assert.assertEquals("01234567", ret);
	}
}
