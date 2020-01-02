package utils.stream;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OnCloseTest {
	@Test
	public void test0() throws Exception {
		Runnable task = mock(Runnable.class);
		
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4))
										.onClose(task);
		Assert.assertEquals(3, stream.count());
		verify(task, times(1)).run();
	}
}
