package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ZipWithTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream1 = FStream.of(Lists.newArrayList("a","b", "c", "d"));
		FStream<Integer> stream2 = FStream.range(0, 100);
		FStream<Tuple2<Integer,String>> stream = stream2.zipWith(stream1);
		
		Tuple2<Integer,String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(0), r._1);
		Assert.assertEquals("a", r._2);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(1), r._1);
		Assert.assertEquals("b", r._2);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(2), r._1);
		Assert.assertEquals("c", r._2);
		
		r = stream.next();
		Assert.assertEquals(true, r != null);
		Assert.assertEquals(Integer.valueOf(3), r._1);
		Assert.assertEquals("d", r._2);
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.range(0, 100);
		FStream<Tuple2<Integer,String>> stream = stream2.zipWith(stream1);
		
		Tuple2<Integer,String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r == null);
	}
	
//	@Test
//	public void test2() throws Exception {
//		Stream<Integer> stream1 = Stream.empty();
//		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
//		Stream<Integer> stream = Stream.concat(stream1, stream2);
//		
//		Option<Integer> r;
//		
//		r = stream.next();
//		Assert.assertEquals(true, r != null);
//		Assert.assertEquals(Integer.valueOf(5), r);
//		
//		r = stream.next();
//		Assert.assertEquals(true, r != null);
//		Assert.assertEquals(Integer.valueOf(3), r);
//		
//		r = stream.next();
//		Assert.assertEquals(true, r != null);
//		Assert.assertEquals(Integer.valueOf(2), r);
//		
//		r = stream.next();
//		Assert.assertEquals(true, r == null);
//	}
//	
//	@Test
//	public void test3() throws Exception {
//		Stream<Integer> stream1 = Stream.empty();
//		Stream<Integer> stream2 = Stream.of(Lists.newArrayList());
//		Stream<Integer> stream = Stream.concat(stream1, stream2);
//		
//		Option<Integer> r;
//		
//		r = stream.next();
//		Assert.assertEquals(true, r == null);
//	}
//	
//	@Test(expected=IllegalArgumentException.class)
//	public void test4() throws Exception {
//		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
//		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
//		Stream<Integer> stream = Stream.concat(null, stream2);
//	}
//	
//	@Test(expected=IllegalArgumentException.class)
//	public void test5() throws Exception {
//		Stream<Integer> stream1 = Stream.of(Lists.newArrayList(1, 2, 4));
//		Stream<Integer> stream2 = Stream.of(Lists.newArrayList(5, 3, 2));
//		Stream<Integer> stream = Stream.concat(stream1, null);
//	}
}
