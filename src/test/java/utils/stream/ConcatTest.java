package utils.stream;


import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Lists;

import utils.Tuple;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class ConcatTest {
	FStream<Integer> m_strm1;
	FStream<Integer> m_strm2;
	
	@BeforeEach
	public void setup() {
		m_strm1 = FStream.of(1, 2);
		m_strm2 = FStream.of(3, 4);
	}
	
	private String toString(FStream<Integer> strm) {
		return strm.map(v -> "" + v).join("");
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream1 = FStream.of(1, 2, 4);
		FStream<Integer> stream2 = FStream.of(5, 3, 2);
		String ret = toString(stream1.concatWith(stream2));
		
		Assertions.assertEquals("124532", ret);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream1 = FStream.of(1, 2, 4);
		FStream<Integer> stream2 = FStream.of();
		String ret = toString(stream1.concatWith(stream2));
		
		Assertions.assertEquals("124", ret);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream1 = FStream.of(1, 2, 4);
		FStream<Integer> stream2 = FStream.empty();
		String ret = toString(stream2.concatWith(stream1));
		
		Assertions.assertEquals("124", ret);
	}
	
	@Test
	public void test4() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.from(Lists.newArrayList());
		String ret = toString(stream2.concatWith(stream1));

		Assertions.assertEquals("", ret);
	}
	
	@Test
	public void test5() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
			FStream<Integer> stream2 = null;
			stream1.concatWith(stream2);
			});
	}
	
	@Test
	public void test7() throws Exception {
		FStream<FStream<Integer>> gen = FStream.unfold(0, v -> Tuple.of(v+1, FStream.of(v)));
		FStream<Integer> stream = FStream.concat(gen);
		
		for ( int i =0; i < 10; ++i ) {
			FOption<Integer> r = stream.next();
			Assertions.assertEquals(true, r.isPresent());
			Assertions.assertEquals(Integer.valueOf(i), r.get());
		}
	}
	
	@Test
	public void test8() throws Exception {
		FStream<Double> dstream = FStream.concat(FStream.of(1.15), FStream.of(1.03), FStream.generate(1d, d->d));
		
		FOption<Double> r;
		
		r = dstream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Double.valueOf(1.15), r.get());
		
		r = dstream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Double.valueOf(1.03), r.get());
		
		r = dstream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Double.valueOf(1d), r.get());
		
		r = dstream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Double.valueOf(1d), r.get());
		
		r = dstream.next();
		Assertions.assertEquals(true, r.isPresent());
		Assertions.assertEquals(Double.valueOf(1d), r.get());
	}
	
	@Test
	public void test9() throws Exception {
		List<String> list1 = Lists.newArrayList("a", "b");
		List<String> list2 = Lists.newArrayList("c");
		List<String> list3 = Lists.newArrayList();
		List<String> list4 = Lists.newArrayList("d", "e");
		
		String ret;
		ret = FStream.concat().join("");
		Assertions.assertEquals("", ret);
		
		ret = FStream.concat(list1).join("");
		Assertions.assertEquals("ab", ret);
		
		ret = FStream.concat(list1, list2).join("");
		Assertions.assertEquals("abc", ret);
		
		ret = FStream.concat(list1, list2, list3).join("");
		Assertions.assertEquals("abc", ret);
		
		ret = FStream.concat(list1, list2, list3, list4).join("");
		Assertions.assertEquals("abcde", ret);
	}
	
	@Test
	public void test10() throws Exception {
		List<String> list1 = Lists.newArrayList("a", "b", "d");
		
		String ret;
		ret = FStream.from(list1).concatWith("d").join("");
		Assertions.assertEquals("abdd", ret);
		
		ret = FStream.empty().concatWith("d").join("");
		Assertions.assertEquals("d", ret);
	}
}
