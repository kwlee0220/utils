package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * {@link FStream#startsWith(FStream)}를 검증한다. 본 스트림은 비교에 필요한 만큼만 읽어야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StartsWithTest {
	@Test
	public void test0() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream2 = FStream.from(Lists.newArrayList("a","b", "c"));
		
		Assertions.assertEquals(true, stream1.startsWith(stream2));
	}
	
	@Test
	public void test1() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream3 = FStream.from(Lists.newArrayList("a","b", "d"));
		
		Assertions.assertEquals(false, stream1.startsWith(stream3));
	}
	
	@Test
	public void test2() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c", "d"));
		FStream<String> stream4 = FStream.empty();

		Assertions.assertEquals(true, stream1.startsWith(stream4));
	}
	
	@Test
	public void test3() throws Exception {
		FStream<String> stream1 = FStream.from(Lists.newArrayList("a","b", "c"));
		FStream<String> stream2 = FStream.from(Lists.newArrayList("a","b", "c", "d"));

		Assertions.assertEquals(false, stream1.startsWith(stream2));
	}
	
	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> stream1 = FStream.from(Lists.newArrayList(1, 2, 4));
		
			Assertions.assertEquals(true, stream1.startsWith(null));
			});
	}

	// 읽은 원소 수를 세고, limit을 넘겨 읽으면 실패하는 스트림.
	private static FStream<Integer> source(AtomicInteger reads, int limit) {
		return FStream.range(0, 100).map(i -> {
			if ( reads.incrementAndGet() > limit ) {
				throw new IllegalStateException("read beyond the prefix: " + i);
			}
			return i;
		});
	}

	@Test
	public void an_empty_prefix_does_not_read_the_source() {
		AtomicInteger reads = new AtomicInteger();
		assertTrue(source(reads, 0).startsWith(FStream.empty()));
		assertEquals(0, reads.get());
	}

	@Test
	public void a_matched_prefix_does_not_read_further() {
		AtomicInteger reads = new AtomicInteger();
		assertTrue(source(reads, 2).startsWith(FStream.of(0, 1)));
		assertEquals(2, reads.get());
	}

	@Test
	public void null_elements_are_compared() {
		assertTrue(FStream.of(null, 1).startsWith(FStream.of((Integer)null)));
		assertFalse(FStream.of(1).startsWith(FStream.of((Integer)null)));
	}
}
