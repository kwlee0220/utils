package utils.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;


/**
 * {@link IOUtils}의 파일 읽기, 파일 쓰기의 스트림 소유권, 압축 해제를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class IOUtilsTest {
	private static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		new Random(17).nextBytes(bytes);
		return bytes;
	}

	@Test
	public void toBytes_reads_the_whole_file(@TempDir Path dir) throws Exception {
		byte[] content = randomBytes(3 * 1024 * 1024 + 7);
		File file = dir.resolve("a.bin").toFile();
		Files.write(file.toPath(), content);

		Assertions.assertArrayEquals(content, IOUtils.readAllBytes(file));
	}

	@Test
	public void toFile_overloads_do_not_close_the_input(@TempDir Path dir) throws Exception {
		byte[] content = randomBytes(10_000);
		for ( int variant = 0; variant < 2; ++variant ) {
			AtomicBoolean closed = new AtomicBoolean();
			File file = dir.resolve("out" + variant).toFile();
			try ( InputStream is = new ByteArrayInputStream(content) {
										@Override
										public void close() throws IOException {
											closed.set(true);
											super.close();
										}
									} ) {
				long count = IOUtils.write(file, is);

				Assertions.assertEquals(content.length, count);
				Assertions.assertFalse(closed.get(), "variant " + variant + " closed the input");
			}
			Assertions.assertArrayEquals(content, Files.readAllBytes(file.toPath()));
		}
	}

	@Test
	public void compress_and_decompress_round_trip() throws Exception {
		byte[] content = randomBytes(50_000);
		Assertions.assertArrayEquals(content, IOUtils.decompress(IOUtils.compress(content)));
		Assertions.assertArrayEquals(new byte[0], IOUtils.decompress(IOUtils.compress(new byte[0])));
	}

	@Test
	@Timeout(5)
	public void truncated_input_fails_instead_of_looping() throws Exception {
		byte[] compressed = IOUtils.compress(randomBytes(50_000));
		byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);

		Assertions.assertThrows(DataFormatException.class, () -> IOUtils.decompress(truncated));
		Assertions.assertThrows(DataFormatException.class, () -> IOUtils.decompress(new byte[0]));
	}

	@Test
	@Timeout(5)
	public void data_requiring_a_dictionary_fails_instead_of_looping() throws Exception {
		Deflater deflater = new Deflater();
		deflater.setDictionary("dictionary".getBytes());
		deflater.setInput("payload".getBytes());
		deflater.finish();
		byte[] buffer = new byte[256];
		int length = deflater.deflate(buffer);
		deflater.end();

		byte[] compressed = Arrays.copyOf(buffer, length);
		Assertions.assertThrows(DataFormatException.class, () -> IOUtils.decompress(compressed));
	}

	@Test
	public void copyStream_rejects_non_positive_buffer_size() {
		IOUtils.CopyStream copy = IOUtils.copyAsync(new ByteArrayInputStream(new byte[0]), OutputStream.nullOutputStream());
		Assertions.assertThrows(IllegalArgumentException.class, () -> copy.bufferSize(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> copy.bufferSize(-1));
	}

	@Test
	@Timeout(5)
	public void copyStream_cancel_wakes_a_blocked_read() throws Exception {
		CountDownLatch closed = new CountDownLatch(1);
		CountDownLatch reading = new CountDownLatch(1);
		InputStream blocking = new InputStream() {
			@Override
			public int read() throws IOException {
				reading.countDown();
				try {
					closed.await();
				}
				catch ( InterruptedException e ) {
					throw new InterruptedIOException();
				}
				throw new IOException("stream closed");
			}
			@Override
			public void close() {
				closed.countDown();
			}
		};
		IOUtils.CopyStream copy = IOUtils.copyAsync(blocking, OutputStream.nullOutputStream());
		copy.start();
		reading.await();

		Assertions.assertTrue(copy.cancel(true));
		Assertions.assertTrue(copy.waitForFinished().isCancelled());
	}
}
