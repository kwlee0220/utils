package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ReduceTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4, 1));

		FOption<Integer> sum = stream.reduce((s,t) -> s+t);
		Assertions.assertEquals(8, (int)sum.get());
	}

	@Test
	public void test1() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

		FOption<String> c = stream.reduce((s,t) -> s+t);
		Assertions.assertEquals("this", c.get());
	}

	@Test
	public void empty_stream_returns_absent() throws Exception {
		FStream<Integer> stream = FStream.empty();

		FOption<Integer> result = stream.reduce((s,t) -> s+t);
		Assertions.assertTrue(result.isAbsent());
	}

	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

			stream.reduce(null);
			});
	}

	@Test
	public void test5() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			FStream<String> stream = FStream.from(Lists.newArrayList("t", "h", "i", "s"));

			RuntimeException error = new RuntimeException();
			stream.reduce((s,t) -> {throw error;});
			});
	}

	@Test
	public void test6() throws Exception {
		FStream<String> stream = FStream.from(Lists.newArrayList("t"));

		FOption<String> c = stream.reduce((s,t) -> s+t);
		Assertions.assertEquals("t", c.get());
	}
}
