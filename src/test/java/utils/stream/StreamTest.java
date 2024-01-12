package utils.stream;


import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StreamTest {
	@Test
	public void test0() throws Exception {
		List<Integer> list = Lists.newArrayList(1, 2, 4);
		FStream<Integer> stream = FStream.from(list);
		Stream<Integer> jstrm = stream.stream();
		
		Assert.assertEquals(list, jstrm.collect(Collectors.toList()));
	}
	
	@Test(timeout = 1000)
	public void test1() throws Exception {
		Stream<Integer> jstrm = Stream.iterate(0, seed -> {
			try { Thread.sleep(50); } catch ( InterruptedException e ) { }
			return seed + 1;
		});
		FStream<Integer> strm = FStream.from(jstrm);
		
		Iterator<Integer> iter = strm.stream().limit(16).iterator();
		for ( int i =0; i < 8; ++i ) {
			Assert.assertEquals((Integer)i, iter.next());
		}
	}

	@Test(expected=IllegalStateException.class)
	public void test2() throws Exception {
		FStream<String> strm = FStream.range(0, 10).mapToObj(idx -> "" + idx);
		
		Assert.assertEquals("0", strm.next().get());
		Assert.assertEquals("1", strm.next().get());
		
		strm.close();
		strm.next();
	}
}
