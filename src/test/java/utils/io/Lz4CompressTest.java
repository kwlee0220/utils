package utils.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class Lz4CompressTest {
	private static final int COUNT = 1000;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		byte[] bytes = new byte[4 * COUNT];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		for ( int i =0; i < COUNT; ++i ) {
			buffer.putInt(i);
		}
		
		InputStream cin = Lz4Compressions.compress(new ByteArrayInputStream(bytes), 1024);
		byte[] compressed = IOUtils.toBytes(cin);

		byte[] restored = new byte[4 * COUNT + 1];
		InputStream in = Lz4Compressions.decompress(new ByteArrayInputStream(compressed));
		int len = IOUtils.readAtBest(in, restored);
		
		ByteBuffer buffer2 = ByteBuffer.wrap(restored, 0, len);
		for ( int i =0; i < COUNT; ++i ) {
			Assert.assertEquals(i, buffer2.getInt());
		}
	}
}
