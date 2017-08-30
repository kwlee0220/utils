package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapTest {
	@Test
	public void test0() throws Exception {
		List<Stream<Integer>> strmList = Lists.newArrayList();
		
		strmList.add(Stream.of(Lists.newArrayList(0, 1, 2)));
		strmList.add(Stream.of(Lists.newArrayList(3, 4)));
		strmList.add(Stream.of(Lists.newArrayList(5)));
		strmList.add(Stream.of(Lists.newArrayList(6, 7)));
		strmList.add(Stream.empty());
		
		Stream<Integer> strm = Stream.of(0, 1, 2, 3);
		Stream<Integer> stream = strm.flatMap(i -> strmList.get(i));

		Option<Integer> r;
		for ( int i =0; i <= 7; ++i ) {
			r = stream.next();
			Assert.assertEquals(true, r.isDefined());
			Assert.assertEquals(Integer.valueOf(i), r.get());
		}
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		List<Stream<Integer>> strmList = Lists.newArrayList();
	
		strmList.add(Stream.empty());
		strmList.add(Stream.empty());
		Stream<Integer> strm = Stream.of(0, 1);
		Stream<Integer> stream = strm.flatMap(i -> strmList.get(i));
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		List<Stream<Integer>> strmList = Lists.newArrayList();
		
		strmList.add(Stream.of(Lists.newArrayList(0, 1, 2)));
		
		Stream<Integer> strm = Stream.of(0);
		Stream<Integer> stream = strm.flatMap(i -> strmList.get(i));

		Option<Integer> r;
		for ( int i =0; i <= 2; ++i ) {
			r = stream.next();
			Assert.assertEquals(true, r.isDefined());
			Assert.assertEquals(Integer.valueOf(i), r.get());
		}
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		List<Stream<Integer>> strmList = Lists.newArrayList();
		
		strmList.add(Stream.of(Lists.newArrayList(0, 1, 2)));
		
		Stream<Integer> strm = Stream.of(0);
		Stream<Integer> stream = strm.flatMap(null);
	}
}
