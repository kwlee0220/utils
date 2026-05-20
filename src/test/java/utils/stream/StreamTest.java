package utils.stream;


import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
		
		Assertions.assertEquals(list, jstrm.collect(Collectors.toList()));
	}
	
	@Test
	@Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
	public void test1() throws Exception {
		Stream<Integer> jstrm = Stream.iterate(0, seed -> {
			try { Thread.sleep(50); } catch ( InterruptedException e ) { }
			return seed + 1;
		});
		FStream<Integer> strm = FStream.from(jstrm);
		
		Iterator<Integer> iter = strm.stream().limit(16).iterator();
		for ( int i =0; i < 8; ++i ) {
			Assertions.assertEquals((Integer)i, iter.next());
		}
	}

	@Test
	public void test2() throws Exception {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			FStream<String> strm = FStream.range(0, 10).mapToObj(idx -> "" + idx);
		
			Assertions.assertEquals("0", strm.next().get());
			Assertions.assertEquals("1", strm.next().get());
		
			strm.close();
			strm.next();
			});
	}
}
