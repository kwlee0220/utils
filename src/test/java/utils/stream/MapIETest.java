package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.CheckedFunction;
import utils.func.FOption;
import utils.func.FailureCase;
import utils.func.Unchecked;
import utils.func.Unchecked.CollectingErrorHandler;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MapIETest {
	@Before
	public void setUp() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4))
											.mapIE(v -> v -1);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(0), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		stream = stream.map(v -> v -1);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream = FStream.range(0, 10)
										.mapIE(i -> {
											if ( i%2 == 0 ) {
												return i;
											}
											throw new Exception("" + i);
										});
		
		FOption<Integer> r;
		
		List<Integer> list = stream.toList();
		Assert.assertEquals(Lists.newArrayList(0, 2, 4, 6, 8), list);
	}
	
	@Test
	public void test3() throws Exception {
		CheckedFunction<Integer,Integer> func
			= i -> { if (i%2 == 0) { return i; } throw  new Exception("" + i); };
		List<FailureCase<Integer>> faileds = Lists.newArrayList();
		CollectingErrorHandler<Integer> handler = Unchecked.collect(faileds);
		
		FOption<Integer> r;
		
		List<Integer> list = FStream.range(0, 10).mapIE(func, handler).toList();
		Assert.assertEquals(Lists.newArrayList(0, 2, 4, 6, 8), list);
		Assert.assertEquals(Lists.newArrayList(1, 3, 5, 7, 9), FStream.from(faileds)
																	.map(FailureCase::getData)
																	.toList());
	}
}
