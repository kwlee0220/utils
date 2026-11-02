package utils.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class TakeLastTest {
	@Test
	public void test0() throws Exception {
		Runnable task = mock(Runnable.class);
		
		FStream<Integer> stream;
		List<Integer> tail;
		
		stream = FStream.range(0, 64).onClose(task);
		tail = stream.takeLast(4);
		Assertions.assertEquals(Lists.newArrayList(60, 61, 62, 63), tail);
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.range(0, 64).onClose(task);
		tail = stream.takeLast(1);
		Assertions.assertEquals(Lists.newArrayList(63), tail);
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.range(0, 64).onClose(task);
		tail = stream.takeLast(0);
		Assertions.assertEquals(Lists.newArrayList(), tail);
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.range(0, 2).onClose(task);
		tail = stream.takeLast(4);
		Assertions.assertEquals(Lists.newArrayList(0, 1), tail);
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.range(0, 2).onClose(task);
		tail = stream.takeLast(0);
		Assertions.assertEquals(Lists.newArrayList(), tail);
		verify(task, times(1)).run();
	}

	@Test
	public void wrapAroundKeepsOrderAndNulls() throws Exception {
		// 순환 버퍼가 여러 바퀴 돈 뒤에도 순서가 맞아야 한다(64 % 5 != 0).
		Assertions.assertEquals(Lists.newArrayList(59, 60, 61, 62, 63), FStream.range(0, 64).takeLast(5));
		Assertions.assertEquals(Lists.newArrayList(null, "c", null),
								FStream.of("a", null, "c", null).takeLast(3));
	}

	@Test
	public void hugeCountDoesNotOverflow() throws Exception {
		Assertions.assertEquals(Lists.newArrayList(0, 1, 2), FStream.range(0, 3).takeLast(Integer.MAX_VALUE));
	}

	@Test
	public void ofExactClassSkipsNulls() throws Exception {
		FStream<Number> stream = FStream.of(1, null, 2L, 3);
		Assertions.assertEquals(Lists.newArrayList(1, 3), stream.ofExactType(Integer.class).toList());
	}
}
