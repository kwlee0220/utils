package utils.stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * @author Kang-Woo Lee (ETRI)
 */
public class PrimitiveTakeTest {
	@Test
	public void testIntFStreamTake() {
		IntFStream stream = FStream.range(0, 10);
		int[] result = stream.take(3).toArray();
		Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, result);
	}

	@Test
	public void testLongFStreamTake() {
		LongFStream stream = LongFStream.range(0L, 10L);
		long[] result = stream.take(3).toArray();
		Assertions.assertArrayEquals(new long[] { 0L, 1L, 2L }, result);
	}

	@Test
	public void testLongFStreamRange() {
		LongFStream stream = LongFStream.range(5L, 8L);
		long[] result = stream.toArray();
		Assertions.assertArrayEquals(new long[] { 5L, 6L, 7L }, result);
	}
}
