package utils.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import utils.async.AbstractThreadedExecution;
import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class InputStreamFromOutputStreamTest {
	@BeforeEach
	public void setup() {
	}

	@Test
	public void test01() throws Exception {
		int[] bytes = FStream.range(0, 1024).toArray();
		ByteBuffer bbuf = ByteBuffer.allocate(bytes.length * 4);
		bbuf.asIntBuffer().put(bytes);

		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(bbuf.array(), os)); ) {
			byte[] buf = new byte[1024 * 10];
			int nbytes = IOUtils.readAtBest(is, buf);

			Assertions.assertEquals(bbuf.capacity(), nbytes);

			int[] newInts = new int[1024];
			ByteBuffer.wrap(buf).asIntBuffer().get(newInts);
			Assertions.assertArrayEquals(bytes, newInts);
		}
	}

	private static WriteInToOutStream pump(byte[] bytes, OutputStream os) {
		WriteInToOutStream exec = new WriteInToOutStream(bytes, os);
		exec.start();

		return exec;
	}

	private static class WriteInToOutStream extends AbstractThreadedExecution<Void> {
		private final byte[] m_bytes;
		private final OutputStream m_os;

		private WriteInToOutStream(byte[] bytes, OutputStream os) {
			m_bytes = bytes;
			m_os = os;

			setLogger(LoggerFactory.getLogger(WriteInToOutStream.class));
		}

		@Override
		protected Void executeWork() throws CancellationException, Exception {
			m_os.write(m_bytes);
			m_os.close();

			return null;
		}
	}
}
