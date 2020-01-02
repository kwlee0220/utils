package utils.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FindLastTest {
	@Test
	public void test0() throws Exception {
		Runnable task = mock(Runnable.class);
		
		FStream<Integer> stream;
		FOption<Integer> last;
		
		stream = FStream.from(Lists.newArrayList(1, 2, 4)).onClose(task);
		last = stream.findLast();
		Assert.assertEquals(4, (int)last.get());
		verify(task, times(1)).run();
		
		reset(task);
		stream = FStream.from(Lists.newArrayList(1)).onClose(task);
		last = stream.findLast();
		Assert.assertEquals(1, (int)last.get());
		verify(task, times(1)).run();

		reset(task);
		stream = FStream.<Integer>empty().onClose(task);
		last = stream.findLast();
		Assert.assertTrue(last.isAbsent());
		verify(task, times(1)).run();
	}
}
