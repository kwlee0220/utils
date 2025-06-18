package utils;

import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitStreamTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void test1() throws Exception {
		SplitStream strm = SplitStream.of("aa,bbb , c", ',');
		
		Assert.assertEquals(true, strm.hasNext());
		Assert.assertEquals("aa", strm.next());
		Assert.assertEquals("bbb , c", strm.remaining());
		
		Assert.assertEquals(true, strm.hasNext());
		Assert.assertEquals("bbb ", strm.next());
		Assert.assertEquals(" c", strm.remaining());
		
		Assert.assertEquals(true, strm.hasNext());
		Assert.assertEquals(" c", strm.next());
		Assert.assertEquals("", strm.remaining());

		Assert.assertEquals(false, strm.hasNext());
		try {
			strm.next();
			Assert.fail("NoSuchElementException expected");
		}
		catch ( NoSuchElementException expected ) { }
	}
}
