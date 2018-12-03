package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrependableFStreamTest {
	private PrependableFStream<Integer> m_stream;

	@Before
	public void setup() {
		m_stream = FStream.of(Lists.newArrayList(1, 2)).toPrependable();
	}
	
	@Test
	public void test0() throws Exception {
		Option<Integer> r;

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(1, (int)r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(2, (int)r.get());
		
		r = m_stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		Option<Integer> r;

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(1, (int)r.get());
		
		m_stream.prepend(1);

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(1, (int)r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(2, (int)r.get());
		
		r = m_stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		Option<Integer> r;

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(1, (int)r.get());
		
		m_stream.prepend(1);
		m_stream.prepend(3);

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(3, (int)r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(1, (int)r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(2, (int)r.get());
		
		r = m_stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test3() throws Exception {
		Option<Integer> r;

		r = m_stream.next();
		r = m_stream.next();
		r = m_stream.next();
		
		m_stream.prepend(4);
		m_stream.prepend(3);

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(3, (int)r.get());

		r = m_stream.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(4, (int)r.get());
		
		r = m_stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test4() throws Exception {
		FStream<Integer> empty1 = FStream.empty();
		PrependableFStream<Integer> empty = empty1.toPrependable();

		Option<Integer> r;
		
		empty.prepend(4);
		empty.prepend(3);

		r = empty.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(3, (int)r.get());

		r = empty.next();
		Assert.assertEquals(true, r.isDefined());
		Assert.assertEquals(4, (int)r.get());
		
		r = empty.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test(expected=IllegalStateException.class)
	public void test5() throws Exception {

		m_stream.close();
		m_stream.prepend(4);
	}
}
