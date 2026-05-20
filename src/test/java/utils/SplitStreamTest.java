package utils;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitStreamTest {
	@BeforeEach
	public void setup() {
	}

	@Test
	public void test1() throws Exception {
		SplitStream strm = SplitStream.of("aa,bbb , c", ',');

		Assertions.assertEquals(true, strm.hasNext());
		Assertions.assertEquals("aa", strm.next());
		Assertions.assertEquals("bbb , c", strm.remaining());

		Assertions.assertEquals(true, strm.hasNext());
		Assertions.assertEquals("bbb ", strm.next());
		Assertions.assertEquals(" c", strm.remaining());

		Assertions.assertEquals(true, strm.hasNext());
		Assertions.assertEquals(" c", strm.next());
		Assertions.assertEquals("", strm.remaining());

		Assertions.assertEquals(false, strm.hasNext());
		try {
			strm.next();
			Assertions.fail("NoSuchElementException expected");
		}
		catch ( NoSuchElementException expected ) { }
	}
}
