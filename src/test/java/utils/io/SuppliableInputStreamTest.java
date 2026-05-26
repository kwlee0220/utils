package utils.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SuppliableInputStreamTest {
	private static final long WAIT_MS = 5_000;

	private static ByteBuffer buf(String s) {
		return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
	}

	// ----- 빈 chunk 처리 (#1) -----

	// 빈 chunk는 건너뛰어야 한다 — 단일 바이트 read에서 BufferUnderflow가 나면 안 된다.
	@Test
	@Timeout(5)
	public void emptyChunkSkippedInSingleByteRead() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(ByteBuffer.allocate(0));		// 빈 chunk
		is.supply(buf("A"));
		is.supply(ByteBuffer.allocate(0));		// 빈 chunk
		is.supply(buf("B"));
		is.endOfSupply();

		Assertions.assertEquals('A', is.read());
		Assertions.assertEquals('B', is.read());
		Assertions.assertEquals(-1, is.read());
		is.close();
	}

	// 빈 chunk는 read(byte[])에서 0 반환 루프를 유발하지 않아야 한다.
	@Test
	@Timeout(5)
	public void emptyChunkSkippedInBulkRead() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(ByteBuffer.allocate(0));
		is.supply(buf("hello"));
		is.endOfSupply();

		byte[] b = new byte[16];
		int n = is.read(b, 0, b.length);
		Assertions.assertEquals(5, n);
		Assertions.assertEquals("hello", new String(b, 0, n, StandardCharsets.UTF_8));
		Assertions.assertEquals(-1, is.read(b, 0, b.length));
		is.close();
	}

	// 빈 chunk만 공급되고 EOS이면 즉시 스트림 끝으로 처리되어야 한다.
	@Test
	@Timeout(5)
	public void onlyEmptyChunksThenEosIsEof() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(ByteBuffer.allocate(0));
		is.supply(ByteBuffer.allocate(0));
		is.endOfSupply();

		Assertions.assertEquals(-1, is.read());
		is.close();
	}

	// ----- read 계약 -----

	// len==0이면 블록 없이 즉시 0을 반환해야 한다(공급된 데이터가 없어도). (#3)
	@Test
	@Timeout(5)
	public void zeroLengthReadReturnsZeroWithoutBlocking() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		Assertions.assertEquals(0, is.read(new byte[4], 0, 0));
		is.close();
	}

	// 단일 바이트 read는 0~255의 부호 없는 값을 반환해야 한다.
	@Test
	@Timeout(5)
	public void singleByteReadIsUnsigned() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(ByteBuffer.wrap(new byte[] { (byte)0xFF, (byte)0x80 }));
		is.endOfSupply();

		Assertions.assertEquals(0xFF, is.read());
		Assertions.assertEquals(0x80, is.read());
		Assertions.assertEquals(-1, is.read());
		is.close();
	}

	// bulk read는 chunk 경계를 넘어 채우지 않는다(반환값이 len보다 작을 수 있음).
	@Test
	@Timeout(5)
	public void bulkReadDoesNotCrossChunkBoundary() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("foo"));
		is.supply(buf("bar"));
		is.endOfSupply();

		byte[] b = new byte[16];
		Assertions.assertEquals(3, is.read(b, 0, 16));		// "foo"까지만
		Assertions.assertEquals("foo", new String(b, 0, 3, StandardCharsets.UTF_8));
		Assertions.assertEquals(3, is.read(b, 0, 16));		// "bar"까지만
		Assertions.assertEquals("bar", new String(b, 0, 3, StandardCharsets.UTF_8));
		Assertions.assertEquals(-1, is.read(b, 0, 16));
		is.close();
	}

	// ----- 정상 경로 -----

	@Test
	@Timeout(5)
	public void readsChunksInOrder() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("foo"));
		is.supply(buf("bar"));
		is.endOfSupply();

		byte[] all = is.readAllBytes();
		Assertions.assertEquals("foobar", new String(all, StandardCharsets.UTF_8));
		Assertions.assertEquals(6, is.offset());
		is.close();
	}

	@Test
	@Timeout(5)
	public void readUtf8MultibyteAcrossChunks() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("한글"));
		is.supply(buf("세계"));
		is.endOfSupply();

		byte[] all = is.readAllBytes();
		Assertions.assertEquals("한글세계", new String(all, StandardCharsets.UTF_8));
		is.close();
	}

	// ----- 종료 처리 -----

	// 정상 종료: 남은 chunk를 모두 읽은 뒤 -1.
	@Test
	@Timeout(5)
	public void endOfSupplyDrainsThenEof() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("X"));
		is.endOfSupply();

		Assertions.assertEquals('X', is.read());
		Assertions.assertEquals(-1, is.read());
		is.close();
	}

	// 오류 종료: 남은 chunk를 모두 읽은 뒤 cause가 전달된다.
	@Test
	@Timeout(5)
	public void errorEndSurfacesCauseAfterDrain() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("X"));
		is.endOfSupply(new IOException("boom"));

		Assertions.assertEquals('X', is.read());
		IOException ex = Assertions.assertThrows(IOException.class, is::read);
		Assertions.assertEquals("boom", ex.getMessage());
		is.close();
	}

	// 첫 오류 원인만 유지된다.
	@Test
	@Timeout(5)
	public void firstErrorCauseWins() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.endOfSupply(new IOException("first"));
		is.endOfSupply(new IOException("second"));

		IOException ex = Assertions.assertThrows(IOException.class, is::read);
		Assertions.assertEquals("first", ex.getMessage());
		is.close();
	}

	// EOS 이후의 supply는 조용히 무시된다.
	@Test
	@Timeout(5)
	public void supplyAfterEosIsIgnored() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.endOfSupply();
		is.supply(buf("ignored"));		// 예외 없이 무시되어야 한다.

		Assertions.assertEquals(-1, is.read());
		is.close();
	}

	// ----- close 동작 -----

	@Test
	@Timeout(5)
	public void closeDiscardsQueuedDataAndFailsRead() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("data"));
		Assertions.assertFalse(is.isClosed());

		is.close();
		Assertions.assertTrue(is.isClosed());
		Assertions.assertThrows(IOException.class, is::read);
	}

	@Test
	@Timeout(5)
	public void supplyAfterCloseThrows() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.close();
		Assertions.assertThrows(StreamClosedException.class, () -> is.supply(buf("x")));
	}

	// ----- 큐 상태 조회 -----

	@Test
	@Timeout(5)
	public void queueAndSeqNoReflectState() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create(8);
		Assertions.assertEquals(8, is.getMaxQueueLength());
		Assertions.assertEquals(0, is.getQueueLength());
		Assertions.assertEquals(0, is.getCurrentChunkSeqNo());

		is.supply(buf("aa"));
		is.supply(buf("bb"));
		Assertions.assertEquals(2, is.getQueueLength());

		is.read();		// 첫 chunk 활성화 → seqNo 1
		Assertions.assertEquals(1, is.getCurrentChunkSeqNo());
		is.endOfSupply();
		is.close();
	}

	// ----- back-pressure (bounded queue) -----

	@Test
	@Timeout(10)
	public void boundedQueueAppliesBackPressure() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create(1);
		is.supply(buf("ab"));		// 큐가 가득 참(size 1 == max)

		AtomicBoolean supplied = new AtomicBoolean(false);
		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread producer = new Thread(() -> {
			try {
				is.supply(buf("cd"));		// 빈자리가 생길 때까지 대기해야 한다.
				supplied.set(true);
			}
			catch ( Throwable e ) {
				err.set(e);
			}
		}, "producer");
		producer.setDaemon(true);
		producer.start();

		// 소비 전까지 두 번째 공급은 막혀 있어야 한다.
		Thread.sleep(200);
		Assertions.assertFalse(supplied.get(), "back-pressure가 동작하지 않음");

		// 첫 chunk를 소비하면 빈자리가 생겨 두 번째 공급이 풀린다.
		Assertions.assertEquals('a', is.read());
		Assertions.assertEquals('b', is.read());
		Assertions.assertEquals('c', is.read());		// 다음 chunk fetch가 "cd"를 끌어온다.
		Assertions.assertEquals('d', is.read());

		waitUntil(supplied::get);
		producer.join(WAIT_MS);
		Assertions.assertNull(err.get());
		is.endOfSupply();
		is.close();
	}

	// ----- supply 시간 제한 -----

	@Test
	@Timeout(5)
	public void supplyTimeoutZeroThrowsImmediately() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create(1);
		is.supply(buf("a"));		// 큐 가득 참
		Assertions.assertThrows(TimeoutException.class,
								() -> is.supply(buf("b"), 0, TimeUnit.MILLISECONDS));
		is.close();
	}

	@Test
	@Timeout(5)
	public void supplyTimeoutPositiveThrowsAfterWait() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create(1);
		is.supply(buf("a"));
		long started = System.currentTimeMillis();
		Assertions.assertThrows(TimeoutException.class,
								() -> is.supply(buf("b"), 100, TimeUnit.MILLISECONDS));
		Assertions.assertTrue(System.currentTimeMillis() - started >= 90);
		is.close();
	}

	// ----- awaitActiveChunk -----

	@Test
	@Timeout(5)
	public void awaitActiveChunkTimesOut() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		Assertions.assertFalse(is.awaitActiveChunk(1, 100, TimeUnit.MILLISECONDS));
		is.close();
	}

	@Test
	@Timeout(5)
	public void awaitActiveChunkReachedAfterRead() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.supply(buf("a"));
		is.read();		// chunk 1 활성화
		Assertions.assertTrue(is.awaitActiveChunk(1, 100, TimeUnit.MILLISECONDS));
		is.endOfSupply();
		is.close();
	}

	@Test
	@Timeout(5)
	public void awaitActiveChunkReturnsWhenClosed() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create();
		is.close();
		// 닫힌 상태에서는 도달 불가능한 순번을 기다려도 즉시 true로 반환한다.
		Assertions.assertTrue(is.awaitActiveChunk(99, 100, TimeUnit.MILLISECONDS));
	}

	// ----- 동시 생산-소비 -----

	@Test
	@Timeout(15)
	public void concurrentProducerConsumer() throws Exception {
		SuppliableInputStream is = SuppliableInputStream.create(4);
		int chunks = 500;

		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread producer = new Thread(() -> {
			try {
				for ( int i = 0; i < chunks; ++i ) {
					is.supply(buf("0123456789"));
				}
				is.endOfSupply();
			}
			catch ( Throwable e ) {
				err.set(e);
			}
		}, "producer");
		producer.setDaemon(true);
		producer.start();

		byte[] all = is.readAllBytes();
		producer.join(WAIT_MS);

		Assertions.assertNull(err.get());
		Assertions.assertEquals(chunks * 10, all.length);
		Assertions.assertEquals(chunks * 10L, is.offset());
		is.close();
	}

	// ----- 헬퍼 -----

	private static void waitUntil(BooleanSupplier cond) throws InterruptedException {
		long deadline = System.currentTimeMillis() + WAIT_MS;
		while ( !cond.getAsBoolean() ) {
			if ( System.currentTimeMillis() > deadline ) {
				Assertions.fail("조건이 " + WAIT_MS + "ms 안에 충족되지 않음");
			}
			Thread.sleep(10);
		}
	}
}
