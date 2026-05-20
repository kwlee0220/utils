package utils.stream;


import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.Tuple;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ZipWithTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream1 = FStream.from(Arrays.asList("a","b", "c", "d"));
		FStream<Integer> stream2 = FStream.generate(0, v -> v + 1);
		FStream<Tuple<Integer,String>> stream = stream2.zipWith(stream1);
		
		FOption<Tuple<Integer,String>> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(0), r.get()._1);
		Assertions.assertEquals("a", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(1), r.get()._1);
		Assertions.assertEquals("b", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(2), r.get()._1);
		Assertions.assertEquals("c", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(3), r.get()._1);
		Assertions.assertEquals("d", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream1 = FStream.from(Arrays.asList("a","b", "c"));
		FStream<Integer> stream2 = FStream.range(0, 4);
		FStream<Tuple<Integer,String>> stream = stream2.zipWith(stream1, true);
		
		FOption<Tuple<Integer,String>> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(0), r.get()._1);
		Assertions.assertEquals("a", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(1), r.get()._1);
		Assertions.assertEquals("b", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(2), r.get()._1);
		Assertions.assertEquals("c", r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r != null);
		Assertions.assertEquals(Integer.valueOf(3), r.get()._1);
		Assertions.assertEquals(null, r.get()._2);
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<String> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.range(0, 100);
		FStream<Tuple<Integer,String>> stream = stream2.zipWith(stream1);

		FOption<Tuple<Integer,String>> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<String> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.range(0, 100);
		FStream<Tuple<String,Integer>> stream = stream1.zipWith(stream2);

		FOption<Tuple<String,Integer>> r;
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
}
