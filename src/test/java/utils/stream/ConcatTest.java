package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Lists;

import utils.func.FOption;
import utils.func.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ConcatTest {
	FStream<Integer> m_strm1;
	FStream<Integer> m_strm2;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		m_strm1 = FStream.of(1, 2);
		m_strm2 = FStream.of(3, 4);
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.from(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = stream1.concatWith(stream2);
		
		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.from(Lists.newArrayList());
		FStream<Integer> stream = stream1.concatWith(stream2);

		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.from(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = stream1.concatWith(stream2);

		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.from(Lists.newArrayList());
		FStream<Integer> stream = stream1.concatWith(stream2);

		FOption<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = null;
		FStream<Integer> stream = stream1.concatWith(stream2);
	}
	
	@Test
	public void test7() throws Exception {
		FStream<FStream<Integer>> gen = FStream.unfold(0, v -> Tuple.of(v+1, FStream.of(v)));
		FStream<Integer> stream = FStream.concat(gen);
		
		for ( int i =0; i < 10; ++i ) {
			FOption<Integer> r = stream.next();
			Assert.assertEquals(true, r.isPresent());
			Assert.assertEquals(Integer.valueOf(i), r.get());
		}
	}
	
	@Test
	public void test8() throws Exception {
		FStream<Double> dstream = FStream.concat(FStream.of(1.15), FStream.of(1.03), FStream.generate(1d, d->d));
		
		FOption<Double> r;
		
		r = dstream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Double.valueOf(1.15), r.get());
		
		r = dstream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Double.valueOf(1.03), r.get());
		
		r = dstream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Double.valueOf(1d), r.get());
		
		r = dstream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Double.valueOf(1d), r.get());
		
		r = dstream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Double.valueOf(1d), r.get());
	}
}
