package utils.stream;


import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SliceTest {
	@Before
	public void setUp() {
		
	}
	
	@Test
	public void test0() throws Exception {
		List<Integer> ret;
		
		ret = FStream.range(0, 11).slice(3).toList();
		Assert.assertEquals(Arrays.asList(0, 1, 2, 3), ret);
		
		ret = FStream.range(0, 11).slice(3, 5).toList();
		Assert.assertEquals(Arrays.asList(3, 4, 5), ret);
		
		ret = FStream.range(0, 11).slice(1, 6, 3).toList();
		Assert.assertEquals(Arrays.asList(1, 4), ret);
		
		ret = FStream.range(0, 11).slice(FOption.of(3), FOption.empty(), FOption.of(3)).toList();
		Assert.assertEquals(Arrays.asList(3, 6, 9), ret);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test91() throws Exception {
		FStream.range(0, 11).slice(FOption.empty(), FOption.empty(), FOption.empty());
	}

	@Test(expected=IllegalArgumentException.class)
	public void test92() throws Exception {
		FStream.range(0, 11).slice(-1, 3);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test93() throws Exception {
		FStream.range(0, 11).slice(5, 3);
	}
}
