package utils.stream;


import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Lists;

import io.vavr.Tuple;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ConcatTeat {
	@Mock FStream<Integer> m_strm1;
	@Mock FStream<Integer> m_strm2;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		when(m_strm1.next()).thenReturn(Option.some(1), Option.some(2), Option.none());
		when(m_strm2.next()).thenReturn(Option.some(3), Option.some(4), Option.none());
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, stream2);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList());
		FStream<Integer> stream = FStream.concat(stream1, stream2);

		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, stream2);

		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(5), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test3() throws Exception {
		FStream<Integer> stream1 = FStream.empty();
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList());
		FStream<Integer> stream = FStream.concat(stream1, stream2);

		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=NullPointerException.class)
	public void test4() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(null, stream2);
	}
	
	@Test(expected=NullPointerException.class)
	public void test5() throws Exception {
		FStream<Integer> stream1 = FStream.of(Lists.newArrayList(1, 2, 4));
		FStream<Integer> stream2 = FStream.of(Lists.newArrayList(5, 3, 2));
		FStream<Integer> stream = FStream.concat(stream1, null);
	}
	
	@Test
	public void test6() throws Exception {
		FStream<Integer> stream = FStream.concat(m_strm1, m_strm2);
		
		Option<Integer> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		verify(m_strm1, times(1)).next();
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		verify(m_strm1, times(2)).next();
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		verify(m_strm1, times(3)).next();
		verify(m_strm1, times(1)).close();
		verify(m_strm2, times(1)).next();
		
		r = stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(Integer.valueOf(4), r.get());
		verify(m_strm1, times(3)).next();
		verify(m_strm1, times(1)).close();
		verify(m_strm2, times(2)).next();
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		verify(m_strm1, times(3)).next();
		verify(m_strm1, times(1)).close();
		verify(m_strm2, times(3)).next();
		verify(m_strm1, times(1)).close();
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
		verify(m_strm1, times(3)).next();
		verify(m_strm1, times(1)).close();
		verify(m_strm2, times(3)).next();
		verify(m_strm1, times(1)).close();
	}
	
	@Test
	public void test7() throws Exception {
		FStream<FStream<Integer>> gen = FStream.unfold(0, v -> Tuple.of(FStream.of(v), v+1));
		FStream<Integer> stream = FStream.concat(gen);
		
		for ( int i =0; i < 10; ++i ) {
			Option<Integer> r = stream.next();
			Assert.assertEquals(true, r.isDefined());
			Assert.assertEquals(Integer.valueOf(i), r.get());
		}
	}
}