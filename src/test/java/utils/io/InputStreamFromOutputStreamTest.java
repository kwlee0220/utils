package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import utils.async.AbstractThreadedExecution;
import utils.func.CheckedConsumerX;
import utils.stream.FStream;


/**
 * {@link InputStreamFromOutputStream}의 데이터 전달과, pump 결과에 따른 EOF 판정을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InputStreamFromOutputStreamTest {
	@Test
	public void test01() throws Exception {
		int[] bytes = FStream.range(0, 1024).toArray();
		ByteBuffer bbuf = ByteBuffer.allocate(bytes.length * 4);
		bbuf.asIntBuffer().put(bytes);

		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(os, out -> {
																		out.write(bbuf.array());
																		out.close();
																	})); ) {
			byte[] buf = new byte[1024 * 10];
			int nbytes = IOUtils.readUpTo(is, buf);

			Assertions.assertEquals(bbuf.capacity(), nbytes);

			int[] newInts = new int[1024];
			ByteBuffer.wrap(buf).asIntBuffer().get(newInts);
			Assertions.assertArrayEquals(bytes, newInts);
		}
	}

	@Test
	@Timeout(10)
	public void successful_pump_ends_with_eof_repeatedly() throws Exception {
		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(os, out -> {
																		out.write(new byte[] {1, 2, 3});
																		out.close();
																	})); ) {
			Assertions.assertArrayEquals(new byte[] {1, 2, 3}, is.readAllBytes());
			Assertions.assertEquals(-1, is.read());
			Assertions.assertEquals(-1, is.read(new byte[4], 0, 4));
		}
	}

	@Test
	@Timeout(10)
	public void failure_after_closing_output_is_not_read_as_eof() throws Exception {
		// pump가 finally에서 출력을 닫은 뒤 실패하는 흔한 형태. 잘린 데이터가 정상 종료처럼 보이면 안 된다.
		IllegalStateException cause = new IllegalStateException("record read failed");
		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(os, out -> {
																		try {
																			out.write(new byte[] {1, 2});
																		}
																		finally {
																			out.close();
																		}
																		throw cause;
																	})); ) {
			Assertions.assertEquals(1, is.read());
			Assertions.assertEquals(2, is.read());
			IOException thrown = Assertions.assertThrows(IOException.class, is::read);
			Assertions.assertSame(cause, thrown.getCause());
		}
	}

	@Test
	@Timeout(10)
	public void io_failure_of_pump_is_thrown_as_is() throws Exception {
		IOException cause = new IOException("disk failure");
		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(os, out -> {
																		out.close();
																		throw cause;
																	})); ) {
			IOException thrown = Assertions.assertThrows(IOException.class, () -> is.read(new byte[4], 0, 4));
			Assertions.assertSame(cause, thrown);
		}
	}

	@Test
	@Timeout(10)
	public void failure_without_closing_output_reports_pump_cause() throws Exception {
		// 출력을 닫지 않고 끝나면 pipe는 "Write end dead"를 낸다. 그때 pump의 원인을 보여야 한다.
		IllegalStateException cause = new IllegalStateException("broken pump");
		try ( InputStream is = new InputStreamFromOutputStream(os -> pump(os, out -> {
																		out.write(7);
																		throw cause;
																	})); ) {
			Assertions.assertEquals(7, is.read());
			IOException thrown = Assertions.assertThrows(IOException.class, is::read);
			Assertions.assertSame(cause, thrown.getCause());
			Assertions.assertEquals(1, thrown.getSuppressed().length, "pipe error is kept as suppressed");
		}
	}

	@Test
	@Timeout(10)
	public void cancelled_pump_is_reported_as_truncated() throws Exception {
		CountDownLatch written = new CountDownLatch(1);
		Holder holder = new Holder();
		try ( InputStream is = new InputStreamFromOutputStream(os -> {
									holder.exec = pump(os, out -> {
										// cancel(true)은 협조적 취소다: pump가 취소 요청을 보고 출력을 닫고 끝난다.
										try {
											out.write(1);
											out.flush();
											written.countDown();
											while ( !isCancelRequested(holder) ) {
												Thread.sleep(10);
											}
											throw new CancellationException("cancel requested");
										}
										finally {
											out.close();
										}
									});
									return holder.exec;
								}); ) {
			Assertions.assertEquals(1, is.read());
			written.await();
			holder.exec.cancel(true);

			IOException thrown = Assertions.assertThrows(IOException.class, is::read);
			Assertions.assertInstanceOf(CancellationException.class, thrown.getCause());
		}
	}

	@Test
	public void pump_function_failure_keeps_the_cause() {
		RuntimeException cause = new RuntimeException("cannot start");
		IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
											() -> new InputStreamFromOutputStream(os -> { throw cause; }));
		Assertions.assertSame(cause, thrown.getCause());
		Assertions.assertThrows(IllegalStateException.class,
								() -> new InputStreamFromOutputStream(os -> null));
	}

	@Test
	@Timeout(10)
	public void factory_closes_output_and_reports_writer_failure() throws Exception {
		try ( InputStream is = InputStreamFromOutputStream.of(out -> out.write(new byte[] {1, 2, 3})) ) {
			Assertions.assertArrayEquals(new byte[] {1, 2, 3}, is.readAllBytes());
		}

		IllegalStateException cause = new IllegalStateException("writer failed");
		try ( InputStream is = InputStreamFromOutputStream.of(out -> { out.write(9); throw cause; }) ) {
			Assertions.assertEquals(9, is.read());
			IOException thrown = Assertions.assertThrows(IOException.class, is::read);
			Assertions.assertSame(cause, thrown.getCause());
		}
	}

	private static boolean isCancelRequested(Holder holder) {
		return holder.exec != null && holder.exec.isCancelRequested();
	}

	private static final class Holder {
		volatile Pump exec;
	}

	private static Pump pump(OutputStream os, CheckedConsumerX<OutputStream,Exception> body) {
		Pump exec = new Pump(os, body);
		exec.start();
		return exec;
	}

	private static class Pump extends AbstractThreadedExecution<Void> {
		private final OutputStream m_os;
		private final CheckedConsumerX<OutputStream,Exception> m_body;

		private Pump(OutputStream os, CheckedConsumerX<OutputStream,Exception> body) {
			m_os = os;
			m_body = body;

			setLogger(LoggerFactory.getLogger(Pump.class));
		}

		@Override
		protected Void executeWork() throws CancellationException, Exception {
			m_body.accept(m_os);
			return null;
		}
	}
}
