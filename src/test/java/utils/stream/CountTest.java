package utils.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CountTest {
	@Test
	public void test0() throws Exception {
		Runnable task = mock(Runnable.class);
		
		FStream<Integer> stream;
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4)).onClose(task);
		Assertions.assertEquals(3, stream.count());
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.from(Lists.newArrayList(1)).onClose(task);
		Assertions.assertEquals(1, stream.count());
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.<Integer>empty().onClose(task);
		Assertions.assertEquals(0, stream.count());
		verify(task, times(1)).run();
	}
}
