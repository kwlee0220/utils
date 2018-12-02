package utils.stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class BufferStreamTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(2, 1);
		
		Option<List<Integer>> r;
		
		r = stream.next();
		Assert.assertThat(r.get(), hasSize(2));
		Assert.assertThat(r.get(), contains(1,2));
		
		r = stream.next();
		Assert.assertThat(r.get(), hasSize(2));
		Assert.assertThat(r.get(), contains(2,4));

		r = stream.next();	// 4, 1
		r = stream.next();	// 1, 3
		r = stream.next();	// 3, 5
		r = stream.next();	// 5, 2
		r = stream.next();	// 2, 7
		r = stream.next();	// 7, 6
		Assert.assertThat(r.get(), hasSize(2));
		Assert.assertThat(r.get(), contains(7, 6));
		
		r = stream.next();	// 6
		Assert.assertThat(r.get(), hasSize(1));
		Assert.assertThat(r.get(), contains(6));
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(3, 2);
		
		Option<List<Integer>> r;
		
		r = stream.next();	// 1, 2, 4
		Assert.assertThat(r.get(), hasSize(3));
		Assert.assertThat(r.get(), contains(1,2,4));
		
		r = stream.next();	// 4, 1, 3
		Assert.assertThat(r.get(), hasSize(3));
		Assert.assertThat(r.get(), contains(4, 1, 3));

		r = stream.next();	// 3, 5, 2
		Assert.assertThat(r.get(), hasSize(3));
		Assert.assertThat(r.get(), contains(3, 5, 2));
		
		r = stream.next();	// 2, 7, 6
		Assert.assertThat(r.get(), hasSize(3));
		Assert.assertThat(r.get(), contains(2, 7, 6));
		
		r = stream.next();	// 6
		Assert.assertThat(r.get(), hasSize(1));
		Assert.assertThat(r.get(), contains(6));
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(1, 5);
		
		Option<List<Integer>> r;
		
		r = stream.next();	// 1
		Assert.assertThat(r.get(), hasSize(1));
		Assert.assertThat(r.get(), contains(1));
		
		r = stream.next();	// 5
		Assert.assertThat(r.get(), hasSize(1));
		Assert.assertThat(r.get(), contains(5));
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}

	@Test(expected=IllegalArgumentException.class)
	public void test3() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(0, 3);
		
		Option<List<Integer>> r;
		
		r = stream.next();	// 
		Assert.assertThat(r.get(), is(empty()));
		
		r = stream.next();	// 
		Assert.assertThat(r.get(), is(empty()));
		
		r = stream.next();	// 
		Assert.assertThat(r.get(), is(empty()));
		
		r = stream.next();
		Assert.assertEquals(true, r.isEmpty());
	}

	@Test(expected=IllegalArgumentException.class)
	public void test4() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(-1, 3);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test5() throws Exception {
		FStream<Integer> base = FStream.of(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<List<Integer>> stream = base.buffer(3, -1);
	}
}
