package utils.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

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
		Assert.assertEquals(Lists.newArrayList(60, 61, 62, 63), tail);
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.range(0, 64).onClose(task);
		tail = stream.takeLast(1);
		Assert.assertEquals(Lists.newArrayList(63), tail);
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.range(0, 64).onClose(task);
		tail = stream.takeLast(0);
		Assert.assertEquals(Lists.newArrayList(), tail);
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.range(0, 2).onClose(task);
		tail = stream.takeLast(4);
		Assert.assertEquals(Lists.newArrayList(0, 1), tail);
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.range(0, 2).onClose(task);
		tail = stream.takeLast(0);
		Assert.assertEquals(Lists.newArrayList(), tail);
		verify(task, times(1)).run();
	}
}
